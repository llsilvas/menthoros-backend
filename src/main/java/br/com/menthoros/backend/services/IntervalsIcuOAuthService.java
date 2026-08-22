package br.com.menthoros.backend.services;

import java.util.UUID;

/**
 * Fluxo OAuth2 com o intervals.icu (app 663): autorização, callback e revogação.
 *
 * <p>Substitui o fluxo de API key, que foi removido — não há convivência entre os dois (D6).
 */
public interface IntervalsIcuOAuthService {

    /**
     * Desfecho da troca do {@code code} por token.
     *
     * <p><b>Por que um resultado tipado e não exceções:</b> o callback é uma superfície de
     * redirect, não uma API (D14). Todo caminho precisa terminar em 302, e exceções que subissem
     * viravam erro HTTP — o que quebraria CA3/CA4. O controller traduz cada valor daqui em
     * {@code success} ou {@code error}.
     *
     * <p><b>Consequência que a ordem de {@code exchangeCodeForToken} precisa respeitar:</b> um
     * retorno normal <b>commita</b> a transação. Não há rollback para segurar "nada é persistido"
     * — a garantia vem de nada ter sido mutado antes da checagem que falha.
     */
    enum Resultado {
        /** Token trocado e conexão persistida. */
        SUCESSO,
        /** State ausente, malformado, com assinatura inválida ou expirado — nada persistido. */
        STATE_INVALIDO,
        /** O atleta do state não existe mais — nada persistido. */
        ATLETA_NAO_ENCONTRADO,
        /** A conta intervals.icu já pertence a outro atleta ativo do tenant (D12) — nada persistido. */
        CONTA_JA_VINCULADA,
        /** O provedor recusou o code, ou a chamada falhou — nada persistido. */
        FALHA_NA_TROCA
    }

    /**
     * URL de autorização para o atleta autenticado, com {@code state} assinado (D2).
     *
     * <p>Idempotent: NO — cada chamada gera um state novo (timestamp diferente).
     * <p>Side Effects: NONE — nada é persistido no pedido de autorização.
     * <p>Tenant-aware: YES — resolve o atleta do JWT via {@code resolverAtletaIdAtual()}.
     *
     * @return URL completa de {@code /oauth/authorize} para redirecionar o atleta
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o usuário autenticado
     *         não tiver atleta vinculado no tenant
     */
    String getAuthorizationUrl();

    /**
     * Processa o callback: valida o state, troca o {@code code} por token e persiste a conexão.
     *
     * <p><b>Nunca lança</b> — ver {@link Resultado}. O {@code code} expira em 2 minutos, então a
     * troca é síncrona aqui e não enfileirada (D10): falha é falha, e o atleta refaz a autorização.
     *
     * <p>Idempotent: NO — cada {@code code} só é trocável uma vez pelo provedor.
     * <p>Side Effects: chamada HTTP externa + persistência (insert ou update) apenas em
     * {@link Resultado#SUCESSO}.
     * <p>Tenant-aware: YES — mas <b>não</b> via {@code TenantContext}: o callback é público e não
     * tem JWT. O tenant vem do atleta resolvido a partir do state assinado (CA9).
     *
     * @param code  código de autorização devolvido pelo provedor
     * @param state valor assinado que o Menthoros emitiu em {@link #getAuthorizationUrl()}
     * @return o desfecho, para o controller traduzir em redirect
     */
    Resultado exchangeCodeForToken(String code, String state);

    /**
     * Revoga o acesso no provedor e desconecta localmente.
     *
     * <p>A ordem é remoto → local, e a revogação remota é best-effort: se o provedor estiver fora
     * do ar, a desconexão local acontece mesmo assim (D7). O inverso deixaria o Menthoros usando
     * um token que o atleta já quis descartar.
     *
     * <p>Idempotent: YES — desconectar duas vezes é seguro.
     * <p>Side Effects: chamada HTTP externa (best-effort) + Database update.
     * <p>Tenant-aware: YES — via {@code TenantContext.getRequiredTenantId()}.
     *
     * @param atletaId atleta que está desconectando
     */
    void revogarEDesconectar(UUID atletaId);
}
