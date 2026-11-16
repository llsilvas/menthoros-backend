package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.PlanoTreino;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanoTreinoRepository extends JpaRepository<PlanoTreino, UUID> {

    /** Busca a temporada (ou PlanoTreino legado) dentro do tenant. */
    Optional<PlanoTreino> findByIdAndAssessoriaId(UUID id, UUID tenantId);
}
