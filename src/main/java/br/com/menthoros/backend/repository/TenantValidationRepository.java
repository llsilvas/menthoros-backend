package br.com.menthoros.backend.repository;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Repositório para validar se um recurso pertence a um tenant específico.
 *
 * Centraliza a lógica de validação de isolamento de tenant.
 * Usado pelo TenantValidationAspect para validar acesso.
 *
 * Porquê centralizar aqui?
 * - Todos os repositórios de domínio dependem de um
 * - Um único ponto de mudança se a estrutura de tenant mudar
 * - Auditável e testável
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantValidationRepository {

    private final AtletaRepository atletaRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final ProvaRepository provaRepository;
    private final TreinoReconciliacaoRepository treinoReconciliacaoRepository;
    private final SugestaoCoachRepository sugestaoCoachRepository;

    /**
     * Valida se um recurso UUID qualquer pertence ao tenant especificado.
     *
     * Estratégia:
     * 1. Tenta encontrar o recurso em cada repositório tenant-aware
     * 2. Retorna true se encontrado em algum (significa que pertence ao tenant)
     * 3. Retorna false se não encontrado em nenhum
     *
     * Porquê dessa forma?
     * - Genérico para qualquer tipo de entidade
     * - Centraliza validação
     * - Simples de manter quando novas entidades são adicionadas
     *
     * @param resourceId UUID do recurso
     * @param tenantId UUID do tenant proprietário
     * @return true se o recurso pertence ao tenant, false caso contrário
     */
    public boolean resourceBelongsToTenant(UUID resourceId, UUID tenantId) {
        if (resourceId == null || tenantId == null) {
            log.warn("TenantValidation: resourceId ou tenantId é null");
            return false;
        }

        // Tenta Atleta
        if (atletaRepository.findByIdAndTenantId(resourceId, tenantId).isPresent()) {
            log.debug("TenantValidation: resourceId {} pertence a tenant {} (Atleta)",
                    resourceId, tenantId);
            return true;
        }

        // Tenta TreinoPlanejado (relacionado via Atleta)
        if (treinoPlanejadoRepository.existsByIdAndAtleta_TenantId(resourceId, tenantId)) {
            log.debug("TenantValidation: resourceId {} pertence a tenant {} (TreinoPlanejado)",
                    resourceId, tenantId);
            return true;
        }

        // Tenta TreinoRealizado (relacionado via Atleta)
        if (treinoRealizadoRepository.existsByIdAndAtleta_TenantId(resourceId, tenantId)) {
            log.debug("TenantValidation: resourceId {} pertence a tenant {} (TreinoRealizado)",
                    resourceId, tenantId);
            return true;
        }

        // Tenta PlanoSemanal (relacionado via Atleta)
        if (planoSemanalRepository.existsByIdAndAtleta_TenantId(resourceId, tenantId)) {
            log.debug("TenantValidation: resourceId {} pertence a tenant {} (PlanoSemanal)",
                    resourceId, tenantId);
            return true;
        }

        // Tenta Prova (relacionado via Atleta)
        if (provaRepository.existsByIdAndAtleta_TenantId(resourceId, tenantId)) {
            log.debug("TenantValidation: resourceId {} pertence a tenant {} (Prova)",
                    resourceId, tenantId);
            return true;
        }

        // Tenta TreinoReconciliacao (relacionado via TreinoRealizado -> Atleta)
        if (treinoReconciliacaoRepository.existsByIdAndTreinoRealizado_Atleta_TenantId(
                resourceId, tenantId)) {
            log.debug("TenantValidation: resourceId {} pertence a tenant {} (TreinoReconciliacao)",
                    resourceId, tenantId);
            return true;
        }

        // Tenta SugestaoCoach
        if (sugestaoCoachRepository.existsByIdAndTenantId(resourceId, tenantId)) {
            log.debug("TenantValidation: resourceId {} pertence a tenant {} (SugestaoCoach)",
                    resourceId, tenantId);
            return true;
        }

        // Não encontrado em nenhuma entidade
        log.debug("TenantValidation: resourceId {} NÃO pertence a tenant {} (nenhuma entidade encontrada)",
                resourceId, tenantId);
        return false;
    }
}
