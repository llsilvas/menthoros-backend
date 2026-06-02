package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.SkillExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de persistência para execuções de skills de domínio.
 *
 * <p>Todos os métodos são tenant-aware pelo parâmetro {@code tenantId} —
 * nunca consultar sem filtragem por tenant em contexto multi-tenant.</p>
 */
public interface SkillExecutionRepository extends JpaRepository<SkillExecution, UUID> {

    /**
     * Retorna todas as execuções de skills para um atleta em um tenant,
     * ordenadas da mais recente para a mais antiga.
     *
     * <p><b>Idempotente:</b> SIM — operação de leitura.</p>
     * <p><b>Side Effects:</b> NONE</p>
     * <p><b>Tenant-aware:</b> SIM — filtrado por tenantId.</p>
     *
     * @param atletaId ID do atleta
     * @param tenantId ID do tenant
     * @return lista de execuções ordenadas por executedAt DESC
     */
    List<SkillExecution> findByAtletaIdAndTenantIdOrderByExecutedAtDesc(UUID atletaId, UUID tenantId);

    /**
     * Retorna todas as execuções de uma skill específica em um tenant,
     * ordenadas da mais recente para a mais antiga.
     *
     * <p><b>Idempotente:</b> SIM — operação de leitura.</p>
     * <p><b>Side Effects:</b> NONE</p>
     * <p><b>Tenant-aware:</b> SIM — filtrado por tenantId.</p>
     *
     * @param skillKey chave identificadora da skill
     * @param tenantId ID do tenant
     * @return lista de execuções da skill ordenadas por executedAt DESC
     */
    List<SkillExecution> findBySkillKeyAndTenantIdOrderByExecutedAtDesc(String skillKey, UUID tenantId);

    /**
     * Retorna a execução mais recente de uma skill específica para um atleta.
     * Útil para verificar o último estado calculado de uma skill sem histórico completo.
     *
     * <p><b>Idempotente:</b> SIM — operação de leitura.</p>
     * <p><b>Side Effects:</b> NONE</p>
     * <p><b>Tenant-aware:</b> PARCIALMENTE — filtrado por atletaId; use com tenantId verificado no contexto.</p>
     *
     * @param atletaId ID do atleta
     * @param skillKey chave identificadora da skill
     * @return Optional com a execução mais recente, ou vazio se não houver
     */
    Optional<SkillExecution> findTopByAtletaIdAndSkillKeyOrderByExecutedAtDesc(UUID atletaId, String skillKey);
}
