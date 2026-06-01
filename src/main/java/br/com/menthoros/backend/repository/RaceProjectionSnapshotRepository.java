package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.RaceProjectionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceProjectionSnapshotRepository extends JpaRepository<RaceProjectionSnapshot, UUID> {

    /**
     * Snapshot mais recente de um atleta, independente de prova.
     * Usado para dashboard do coach com visão geral.
     */
    @Query("SELECT s FROM RaceProjectionSnapshot s WHERE s.athlete.id = :athleteId ORDER BY s.generatedAt DESC LIMIT 1")
    Optional<RaceProjectionSnapshot> findLatestByAthleteId(@Param("athleteId") UUID athleteId);

    /**
     * Histórico completo de snapshots de um atleta para uma prova específica,
     * ordenado do mais recente ao mais antigo.
     */
    @Query("SELECT s FROM RaceProjectionSnapshot s WHERE s.athlete.id = :athleteId AND s.race.id = :raceId ORDER BY s.generatedAt DESC")
    List<RaceProjectionSnapshot> findByAthleteIdAndRaceIdOrderByGeneratedAtDesc(
            @Param("athleteId") UUID athleteId,
            @Param("raceId") UUID raceId);

    /**
     * Snapshot oficial atual de um atleta para uma prova.
     * Retorna no máximo um resultado (regra de negócio garantida via código, não constraint).
     */
    @Query("SELECT s FROM RaceProjectionSnapshot s WHERE s.athlete.id = :athleteId AND s.race.id = :raceId AND s.official = true ORDER BY s.generatedAt DESC LIMIT 1")
    Optional<RaceProjectionSnapshot> findOfficialByAthleteIdAndRaceId(
            @Param("athleteId") UUID athleteId,
            @Param("raceId") UUID raceId);

    /**
     * Desmarca todos os snapshots oficiais de um atleta/prova.
     * Executado ANTES de marcar um novo snapshot como oficial (regra D5 do design.md).
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RaceProjectionSnapshot s SET s.official = false WHERE s.athlete.id = :athleteId AND s.race.id = :raceId AND s.official = true")
    void clearOfficialByAthleteIdAndRaceId(
            @Param("athleteId") UUID athleteId,
            @Param("raceId") UUID raceId);
}
