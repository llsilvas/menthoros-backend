package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.IntervalsIcuConnectionStatusDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;

import java.util.Optional;
import java.util.UUID;

/**
 * Conexão do atleta com o intervals.icu (push de treinos planejados ao relógio).
 * Credencial é uma API key pessoal — validada contra a API do intervals.icu antes de persistir.
 */
public interface IntervalsIcuConnectionService {

    /**
     * Valida a API key contra o intervals.icu e persiste a conexão. Reconecta (reusa o registro
     * existente da unique atleta+plataforma) quando o atleta já teve uma conexão.
     *
     * <p>Idempotent: NO — cada chamada revalida a key contra a API externa; uma key inválida
     * lança exceção sem alterar estado, mas uma key válida repetida reescreve o registro existente.
     * <p>Side Effects: chamada HTTP externa (validação da key) + persistência (insert ou update).
     * <p>Tenant-aware: YES — resolve o atleta via {@code TenantContext.getRequiredTenantId()}.
     *
     * @param atletaId ID do atleta dono da conexão
     * @param apiKey   API key pessoal gerada em intervals.icu
     * @return status da conexão recém-criada/atualizada (nunca contém a key)
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException se a key for inválida (422)
     */
    IntervalsIcuConnectionStatusDto conectar(UUID atletaId, String apiKey);

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
}
