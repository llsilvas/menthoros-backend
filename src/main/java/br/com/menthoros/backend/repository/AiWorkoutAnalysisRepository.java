package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.enums.AnaliseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiWorkoutAnalysisRepository extends JpaRepository<AnaliseWorkout, UUID> {

    Optional<AnaliseWorkout> findByTreinoRealizadoIdAndTenantId(UUID treinoRealizadoId, UUID tenantId);

    boolean existsByTreinoRealizadoIdAndStatus(UUID treinoRealizadoId, AnaliseStatus status);

    /**
     * Carimbo atômico da primeira visualização (analise-ia-treino-atleta, Codex #6/QA): duas
     * leituras concorrentes do primeiro COMPLETED não podem contar duas vezes — só quem
     * transiciona null → agora incrementa a métrica (linhas afetadas == 1).
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update AnaliseWorkout a set a.atletaPrimeiraVisualizacaoEm = :agora "
                    + "where a.id = :id and a.atletaPrimeiraVisualizacaoEm is null")
    int marcarPrimeiraVisualizacao(@org.springframework.data.repository.query.Param("id") UUID id,
                                   @org.springframework.data.repository.query.Param("agora") java.time.Instant agora);

    /** Flag `analiseAtletaDisponivel` do plano (analise-ia-treino-atleta): uma consulta por plano, não N. */
    java.util.List<AnaliseWorkout> findByTreinoRealizadoIdInAndTenantId(java.util.Collection<UUID> treinoRealizadoIds, UUID tenantId);
}
