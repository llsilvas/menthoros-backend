package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.MetricasDiarias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetricasDiariasRepository extends JpaRepository<MetricasDiarias, UUID> {

    Optional<MetricasDiarias> findByAtletaIdAndData(UUID atletaId, LocalDate data);

    /**
     * Variante tenant-aware de {@link #findByAtletaIdAndData}, para callers que ainda não
     * validaram {@code atletaId} contra o tenant antes de chamar este repository (defesa em
     * profundidade — evita depender apenas do caller para o isolamento).
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    Optional<MetricasDiarias> findByAtletaIdAndDataAndTenantId(UUID atletaId, LocalDate data, UUID tenantId);

    List<MetricasDiarias> findByAtletaIdAndDataBetweenOrderByDataAsc(UUID atletaId, LocalDate dataInicio, LocalDate dataFim);

    @Query("Select m from MetricasDiarias m where m.atleta.id = :atletaId " +
    "ORDER BY m.data DESC LIMIT 1")
    Optional<MetricasDiarias> findLatestByAtletaId(UUID atletaId);

    @Query("Select m from MetricasDiarias m where m.atleta.id = :atleta " +
    "and m.data >= :dataInicio order by m.data asc")
    List<MetricasDiarias> findMetricasDesde(UUID atletaId, LocalDate dataInicio);

    List<MetricasDiarias> findByAtletaIdOrderByDataAsc(UUID atletaId);

    List<MetricasDiarias> findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(UUID atletaId, LocalDate dataLimite);

    void deleteByAtletaId(UUID atletaId);
}
