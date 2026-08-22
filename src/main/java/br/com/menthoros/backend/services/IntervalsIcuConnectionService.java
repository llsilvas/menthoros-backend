package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.IntervalsIcuConnectionStatusDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;

import java.util.Optional;
import java.util.UUID;

/**
 * Conexão do atleta com o intervals.icu (push de treinos planejados ao relógio).
 *
 * <p>A credencial é um <b>token OAuth2</b>. A criação da conexão pertence ao
 * {@code IntervalsIcuOAuthService}: o fluxo de API key foi removido e não convive com o OAuth
 * (D6). Aqui ficam consulta, desconexão e o hook de pausa do Strava.
 */
public interface IntervalsIcuConnectionService {

    /**
     * Status atual da conexão intervals.icu do atleta.
     *
     * <p>Idempotent: YES — leitura pura.
     * <p>Side Effects: NONE.
     * <p>Tenant-aware: YES — resolve o atleta via {@code TenantContext.getRequiredTenantId()}.
     *
     * @param atletaId ID do atleta
     * @return {@link Optional#empty()} quando o atleta nunca conectou; presente (ativo ou não) caso contrário
     */
    Optional<IntervalsIcuConnectionStatusDto> status(UUID atletaId);

    /**
     * Desconecta a conta intervals.icu do atleta (soft-disconnect, mesmo padrão do Strava):
     * mantém o registro histórico, zera as credenciais e desativa.
     *
     * <p>Idempotent: YES — desconectar duas vezes é seguro (já desconectado / nunca conectado).
     * <p>Side Effects: Database update (ativo=false, accessToken/refreshToken=null) quando existir conexão.
     * <p>Tenant-aware: YES — resolve o atleta via {@code TenantContext.getRequiredTenantId()}.
     *
     * @param atletaId ID do atleta
     */
    void desconectar(UUID atletaId);

    /**
     * Conexão ativa do atleta com o intervals.icu, para uso por colaboradores internos (ex.: o
     * listener de push de treinos) que já possuem o tenant resolvido e não devem depender do
     * {@code TenantContext} de request.
     *
     * <p>Idempotent: YES — leitura pura.
     * <p>Side Effects: NONE.
     * <p>Tenant-aware: YES — tenant recebido explicitamente por parâmetro (não via TenantContext).
     *
     * @param atletaId ID do atleta
     * @param tenantId ID do tenant do atleta
     * @return a entidade {@link IntegracaoExterna} ativa, se houver
     */
    Optional<IntegracaoExterna> conexaoAtiva(UUID atletaId, UUID tenantId);

    /**
     * Hook D5.2 — ao conectar o intervals.icu com o Strava ativo, pausa a sincronização automática
     * do Strava daquele atleta, eliminando a colisão cross-fonte na origem. Sem Strava conectado é
     * um no-op; se já pausado, não regrava.
     *
     * <p><b>Por que é público na interface e não privado no impl:</b> quem cria a conexão passou a
     * ser o {@code IntervalsIcuOAuthService}, e D9 é explícito em preservar este hook em vez de
     * reimplementá-lo. Duas cópias da regra divergiriam no primeiro ajuste.
     *
     * <p><b>Monotônico de propósito</b> (decisão do founder, "nunca auto-retomar"): só seta
     * {@code autoSyncPausado=true}, nunca reverte. Retomar é ação manual do coach.
     *
     * <p>Idempotent: YES — pausar duas vezes é no-op na segunda.
     * <p>Side Effects: Database update na linha do Strava, apenas quando havia Strava ativo e não pausado.
     * <p>Tenant-aware: YES — tenant recebido explicitamente por parâmetro.
     *
     * @param atletaId ID do atleta
     * @param tenantId ID do tenant do atleta
     */
    void pausarStravaAutomaticamente(UUID atletaId, UUID tenantId);
}
