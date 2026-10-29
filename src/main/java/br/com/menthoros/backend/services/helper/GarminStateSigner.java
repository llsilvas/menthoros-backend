package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.config.external.GarminProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Assina e valida o {@code state} do fluxo OAuth2 com o Garmin (D2 de garmin-connection).
 *
 * <p>Formato: {@code garmin.<atletaId>.<epoch>.<nonce>.<MAC base64url>}. Separador {@code .} (e não
 * {@code |}) para o state viajar na query string sem escape. O prefixo fixo e a chave
 * ({@code client-secret} do Garmin) impedem que um state do intervals.icu valide aqui. O nonce
 * evita colisão de duas autorizações no mesmo segundo, já que o state é a chave do cache do
 * verifier PKCE.
 *
 * <p>Nenhum método lança: a entrada de {@link #validar(String)} vem do callback público.
 * {@code GarminOAuthServiceImpl} só usa este componente depois de {@code configurado()}, porque
 * com segredo vazio o HMAC não protege nada.
 */
@Slf4j
@Component
public class GarminStateSigner {

    private static final String ALGORITMO = "HmacSHA256";
    private static final String PREFIXO = "garmin";
    private static final String SEPARADOR = ".";
    private static final int PARTES = 5;
    private static final int NONCE_BYTES = 16;
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration SKEW_TOLERADO = Duration.ofSeconds(30);

    private final byte[] chave;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public GarminStateSigner(GarminProperties properties, Clock clock) {
        String segredo = properties.getClientSecret();
        this.chave = (segredo == null ? "" : segredo).getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /**
     * Idempotent: NO — timestamp e nonce mudam a cada chamada.
     * Side Effects: NONE.
     * Tenant-aware: NO — o atletaId já vem resolvido pelo chamador.
     */
    public String assinar(UUID atletaId) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        String payload = String.join(SEPARADOR, PREFIXO, atletaId.toString(),
                Long.toString(clock.instant().getEpochSecond()),
                Base64.getUrlEncoder().withoutPadding().encodeToString(nonce));
        String assinatura = mac(payload);
        if (assinatura == null) {
            throw new IllegalStateException("Não foi possível assinar o state Garmin");
        }
        return payload + SEPARADOR + assinatura;
    }

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: NO — o callback não tem tenant.
     *
     * @return o atletaId quando prefixo, assinatura e prazo conferem; vazio caso contrário
     */
    public Optional<UUID> validar(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        String[] partes = state.split("\\" + SEPARADOR, -1);
        if (partes.length != PARTES || !PREFIXO.equals(partes[0])) {
            return Optional.empty();
        }

        String payload = String.join(SEPARADOR, partes[0], partes[1], partes[2], partes[3]);
        String esperada = mac(payload);
        if (esperada == null || !MessageDigest.isEqual(
                esperada.getBytes(StandardCharsets.UTF_8), partes[4].getBytes(StandardCharsets.UTF_8))) {
            log.warn("State Garmin com assinatura inválida rejeitado");
            return Optional.empty();
        }

        try {
            long idade = clock.instant().getEpochSecond() - Long.parseLong(partes[2]);
            if (idade > TTL.toSeconds() || idade < -SKEW_TOLERADO.toSeconds()) {
                log.warn("State Garmin fora do prazo rejeitado: idade={}s", idade);
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(partes[1]));
        } catch (IllegalArgumentException e) {
            log.warn("State Garmin com assinatura válida e conteúdo ilegível: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String mac(String payload) {
        try {
            Mac hmac = Mac.getInstance(ALGORITMO);
            hmac.init(new SecretKeySpec(chave, ALGORITMO));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // SecretKeySpec rejeita chave vazia (IllegalArgumentException): tratado como falha de validação.
            log.error("Falha ao calcular HMAC do state Garmin: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
