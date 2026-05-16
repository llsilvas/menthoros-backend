package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.enums.AnaliseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiWorkoutAnalysisRepository extends JpaRepository<AnaliseWorkout, UUID> {

    Optional<AnaliseWorkout> findByTreinoRealizadoIdAndTenantId(UUID treinoRealizadoId, UUID tenantId);

    boolean existsByTreinoRealizadoIdAndStatus(UUID treinoRealizadoId, AnaliseStatus status);
}
