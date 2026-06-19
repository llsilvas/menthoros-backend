package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.SugestaoCoach;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SugestaoCoachRepository extends JpaRepository<SugestaoCoach, UUID> {

    /** Busca sugestão por id e tenant — garante isolamento ao resolver detalhe/aprovar/rejeitar. */
    Optional<SugestaoCoach> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Lista sugestões de um tenant filtradas por status — base de {@code listar()}. */
    List<SugestaoCoach> findByTenantIdAndStatus(UUID tenantId, StatusSugestao status);

    /** Verifica ownership para @RequireTenant (TenantValidationRepository). */
    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    /** Idempotência na camada Java: evita INSERT quando já existe pending para (atleta, tipo). */
    boolean existsByAtletaIdAndTipoAndStatus(UUID atletaId, TipoSugestao tipo, StatusSugestao status);
}
