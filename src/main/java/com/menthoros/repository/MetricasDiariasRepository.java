package com.menthoros.repository;

import com.menthoros.entity.MetricasDiarias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetricasDiariasRepository extends JpaRepository<MetricasDiarias, UUID> {

    Optional<MetricasDiarias> findByAtletaIdAndData(UUID atletaId, LocalDate data);

    List<MetricasDiarias> findByAtletaIdAndDataBetweenOrderByDataAsc(UUID atletaId, LocalDate dataInicio, LocalDate dataFim);

    @Query("Select m from MetricasDiarias m where m.atleta.id = :atletaId " +
    "ORDER BY m.data DESC LIMIT 1")
    Optional<MetricasDiarias> findLatestByAtletaId(UUID atletaId);

    @Query("Select m from MetricasDiarias m where m.atleta.id = :atleta " +
    "and m.data >= :dataInicio order by m.data asc")
    List<MetricasDiarias> findMetricasDesde(UUID atletaId, LocalDate dataInicio);

    List<MetricasDiarias> findByAtletaIdOrderByDataAsc(UUID atletaId);

    void deleteByAtletaId(UUID atletaId);
}
