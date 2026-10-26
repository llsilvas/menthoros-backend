package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.IntegracaoExterna;

import java.util.UUID;

/** Fluxo OAuth2 PKCE com o Garmin: autorização, callback e ciclo de vida dos tokens (D2, D3). */
public interface GarminOAuthService {

    /**
     * Desfecho do callback. Resultado tipado e não exceção: o callback é redirect de navegador e
     * todo caminho termina em 302 (mesmo contrato do intervals.icu). O mapeamento para a tag da
     * métrica é: SUCESSO → sucesso, DENIED → recusado, demais → erro.
     */
    enum Resultado {
        SUCESSO,
        /** O atleta recusou na tela do Garmin (sem {@code code}). */
        DENIED,
        /** State inválido/expirado, ou verifier ausente (inclusive segundo uso do mesmo state). */
        STATE_INVALIDO,
        ATLETA_NAO_ENCONTRADO,
        /** O userId Garmin já está ativo em outro atleta, de qualquer tenant. */
        CONTA_JA_VINCULADA,
        /** O Garmin recusou o code ou a chamada falhou. */
        FALHA_NA_TROCA
    }

    /**
     * Idempotent: NO — state e verifier novos a cada chamada.
     * Side Effects: grava o verifier PKCE no cache {@code garmin-pkce}.
     * Tenant-aware: NO — o atletaId já vem resolvido pelo chamador.
     *
     * @throws br.com.menthoros.backend.exception.GarminCanalIndisponivelException se o canal está
     *         desligado ou sem client ID/secret
     */
    String gerarAuthorizeUrl(UUID atletaId);

    /**
     * Idempotent: NO — o state/verifier só valem uma vez.
     * Side Effects: chamadas HTTP ao Garmin e persistência, apenas em {@link Resultado#SUCESSO}.
     * Tenant-aware: YES — o tenant vem do atleta resolvido pelo state assinado, nunca do request.
     * Nunca lança.
     */
    Resultado concluir(String state, String code);

    /**
     * Access token utilizável: renova antes se faltam menos de 600 s para expirar.
     *
     * <p>Idempotent: YES — sem renovação necessária devolve o token atual.
     * <p>Side Effects: pode chamar o token endpoint e gravar o par novo (um único save).
     * <p>Tenant-aware: NO — opera sobre a conexão recebida.
     *
     * @throws IllegalStateException se o Garmin recusou o refresh (400/401): a conexão é marcada
     *         inativa e o atleta precisa reconectar
     * @throws br.com.menthoros.backend.exception.GarminApiException em falha transitória
     *         (5xx/transporte); a conexão permanece intacta
     */
    String tokenValido(IntegracaoExterna conexao);

    /**
     * Renova incondicionalmente (o job diário precisa trocar o refresh token mesmo com o access
     * token ainda válido). Mesmo contrato de erro de {@link #tokenValido}.
     *
     * <p>Idempotent: NO — o refresh token é rotativo.
     * <p>Side Effects: chamada HTTP ao Garmin + Database update.
     * <p>Tenant-aware: NO.
     */
    String renovar(IntegracaoExterna conexao);
}
