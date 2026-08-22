package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.config.external.IntervalsIcuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Assina e valida o parâmetro {@code state} do fluxo OAuth2 com o intervals.icu (D2).
 *
 * <p>Formato: {@code <atletaId>.<epochSeconds>.<HMAC-SHA256 base64url>}, onde o MAC cobre
 * {@code <atletaId>.<epochSeconds>} e a chave é o {@code clientSecret}.
 *
 * <p><b>Por que assinar, se o Strava não assina:</b> lá o {@code state} é um UUID cru e quem
 * dispara a autorização é o técnico, numa superfície interna. Aqui o fluxo é self-service e o
 * callback é público por definição — sem assinatura, qualquer um que descubra um {@code atletaId}
 * monta o callback com um {@code code} obtido da <b>própria</b> conta intervals.icu e vincula a
 * conta dele ao registro de outro atleta. O efeito não é vazamento de dado do Menthoros: é
 * poluição do dado de treino de um terceiro, que é bem mais difícil de detectar do que um erro
 * barulhento.
 *
 * <p><b>Por que HMAC e não uma tabela de states:</b> stateless resolve multi-instância sem
 * migration, sem limpeza de registros expirados e sem uma segunda fonte de verdade. O
 * {@code clientSecret} já está no ambiente e já é secreto — não introduz material novo a proteger.
 * Em contrapartida, ele passa a ser material criptográfico: é por isso que
 * {@link IntervalsIcuProperties} o valida como {@code @NotBlank} e derruba o boot se vier vazio
 * (D11). Com chave vazia, tudo aqui continua "funcionando" e não protege nada.
 *
 * <p><b>Nenhum método lança.</b> A entrada de {@link #validar(String)} vem de um endpoint público,
 * ou seja, do atacante. Qualquer exceção que escapasse viraria 500 e violaria CA13, que exige
 * redirect em todo caminho de falha.
 */
@Slf4j
@Component
public class IntervalsIcuStateSigner {

    private static final String ALGORITMO = "HmacSHA256";
    private static final String SEPARADOR = ".";
    private static final int PARTES = 3;

    /** TTL do state: o atleta tem 10 minutos entre pedir a autorização e concluí-la. */
    private static final Duration TTL = Duration.ofMinutes(10);

    /**
     * Tolerância para relógios adiantados. Quem assina e quem valida podem ser JVMs diferentes
     * (multi-instância), e um skew de poucos segundos não deve invalidar um state legítimo. Não é
     * brecha de segurança: forjar um timestamp futuro ainda exige a chave para assinar.
     */
    private static final Duration SKEW_TOLERADO = Duration.ofSeconds(30);

    private final byte[] chave;
    private final Clock clock;

    public IntervalsIcuStateSigner(IntervalsIcuProperties properties, Clock clock) {
        this.chave = properties.getClientSecret().getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /**
     * Idempotent: NO — o timestamp muda a cada chamada, então dois states do mesmo atleta diferem.
     * Side Effects: NONE.
     * Tenant-aware: NO — o atletaId já vem resolvido pelo chamador.
     *
     * @param atletaId atleta que iniciou a autorização
     * @return state pronto para ir na query string da URL de autorização
     */
    public String assinar(UUID atletaId) {
        String payload = atletaId + SEPARADOR + clock.instant().getEpochSecond();
        return payload + SEPARADOR + mac(payload);
    }

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: NO — o callback não tem tenant; quem resolve é o chamador, a partir do atleta.
     *
     * @param state valor recebido no callback (pode ser nulo, malformado ou hostil)
     * @return o atletaId quando assinatura e prazo conferem; {@link Optional#empty()} caso contrário
     */
    public Optional<UUID> validar(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }

        String[] partes = state.split("\\" + SEPARADOR);
        if (partes.length != PARTES) {
            return Optional.empty();
        }

        String payload = partes[0] + SEPARADOR + partes[1];
        if (!assinaturaConfere(payload, partes[2])) {
            log.warn("State intervals.icu com assinatura inválida rejeitado");
            return Optional.empty();
        }

        // Só depois de a assinatura conferir vale a pena interpretar o conteúdo: até aqui, tudo
        // no state é entrada não confiável.
        try {
            long emitidoEm = Long.parseLong(partes[1]);
            long agora = clock.instant().getEpochSecond();
            long idade = agora - emitidoEm;

            if (idade > TTL.toSeconds()) {
                log.warn("State intervals.icu expirado rejeitado: idade={}s", idade);
                return Optional.empty();
            }
            if (idade < -SKEW_TOLERADO.toSeconds()) {
                log.warn("State intervals.icu com timestamp futuro rejeitado: idade={}s", idade);
                return Optional.empty();
            }

            return Optional.of(UUID.fromString(partes[0]));
        } catch (IllegalArgumentException e) {
            // Timestamp não-numérico ou atletaId não-UUID. Só alcançável com a chave correta
            // (a assinatura já conferiu), então é bug nosso e não ataque — mas ainda assim não sobe.
            log.warn("State intervals.icu com assinatura válida e conteúdo ilegível: {}",
                    e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private boolean assinaturaConfere(String payload, String assinaturaRecebida) {
        String esperada = mac(payload);
        if (esperada == null) {
            return false;
        }
        // Tempo constante: comparar com equals() vazaria, pelo tempo de resposta, quantos bytes
        // do MAC o atacante já acertou.
        return MessageDigest.isEqual(
                esperada.getBytes(StandardCharsets.UTF_8),
                assinaturaRecebida.getBytes(StandardCharsets.UTF_8));
    }

    private String mac(String payload) {
        try {
            Mac hmac = Mac.getInstance(ALGORITMO);
            hmac.init(new SecretKeySpec(chave, ALGORITMO));
            byte[] assinado = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            // withoutPadding + urlSafe: o state viaja como query param na URL de autorização.
            return Base64.getUrlEncoder().withoutPadding().encodeToString(assinado);
        } catch (java.security.GeneralSecurityException e) {
            log.error("Falha ao calcular HMAC do state intervals.icu: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
