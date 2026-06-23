package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.PlanoMetaDados;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanoMetadadosRepository extends JpaRepository<PlanoMetaDados, UUID> {
    Optional<PlanoMetaDados> findByAtletaId(UUID atletaId);

    @Query("""
    SELECT pm FROM PlanoMetaDados pm
    WHERE pm.atleta.id = :atletaId
    ORDER BY pm.dataCriacao DESC
    LIMIT 1
    """)
    Optional<PlanoMetaDados> findLatestByAtletaId(UUID atletaId);

    /**
     * Busca o metadados mais recente pelo ID do atleta, filtrado pelo tenant via assessoria.
     * Previne cross-tenant data leak.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Query("""
    SELECT pm FROM PlanoMetaDados pm
    WHERE pm.atleta.id = :atletaId
    AND pm.atleta.assessoria.id = :tenantId
    ORDER BY pm.dataCriacao DESC
    LIMIT 1
    """)
    Optional<PlanoMetaDados> findLatestByAtletaIdAndTenantId(
            @Param("atletaId") UUID atletaId,
            @Param("tenantId") UUID tenantId);

    /**
     * Busca metadados por ID e tenant — previne cross-tenant data leak.
     * O tenant é resolvido via atleta.assessoria.id pois PlanoMetaDados
     * não possui coluna tenant_id direta.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     *
     * @param id       ID dos metadados
     * @param tenantId ID da assessoria (tenant)
     * @return metadados se pertencer ao tenant, vazio caso contrário
     */
    @Query("SELECT pm FROM PlanoMetaDados pm WHERE pm.id = :id AND pm.atleta.assessoria.id = :tenantId")
    Optional<PlanoMetaDados> findByIdAndTenantId(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId);

    /**
     * Busca metadados pelo ID do atleta e ID da assessoria — previne cross-tenant data leak.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     *
     * @param atletaId     ID do atleta
     * @param assessoriaId ID da assessoria (tenant)
     * @return metadados se pertencer ao tenant, vazio caso contrário
     */
    @Query("SELECT pm FROM PlanoMetaDados pm WHERE pm.atleta.id = :atletaId AND pm.assessoria.id = :assessoriaId")
    Optional<PlanoMetaDados> findByAtletaIdAndAssessoriaId(
            @Param("atletaId") UUID atletaId,
            @Param("assessoriaId") UUID assessoriaId);
}
