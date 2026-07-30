package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.MetricasDiarias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Apaga as métricas de um intervalo fechado, para o delete-por-bloco do recálculo histórico.
     *
     * <p>Substitui o {@code deleteByAtletaId} antecipado: cada bloco apaga o próprio intervalo
     * dentro da mesma transação que o reconstrói, de modo que nunca exista uma janela em que o
     * intervalo está apagado e não reconstruído.</p>
     *
     * Idempotent: YES — apagar duas vezes o mesmo intervalo é seguro.
     * Side Effects: Database delete.
     * Tenant-aware: NO — o caller resolve o atleta antes.
     */
    @Modifying
    @Query("DELETE FROM MetricasDiarias m WHERE m.atleta.id = :atletaId "
            + "AND m.data BETWEEN :inicio AND :fim")
    int deleteByAtletaIdAndDataBetween(UUID atletaId, LocalDate inicio, LocalDate fim);

    /**
     * Data da primeira métrica registrada, ou {@code null} se não houver nenhuma.
     *
     * <p>Substitui o carregamento da lista inteira de métricas só para descobrir o limite inferior
     * do intervalo de recálculo.</p>
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: NO
     */
    @Query("SELECT MIN(m.data) FROM MetricasDiarias m WHERE m.atleta.id = :atletaId")
    LocalDate findDataPrimeiraMetrica(UUID atletaId);

    /**
     * Data da última métrica registrada, ou {@code null} se não houver nenhuma.
     *
     * <p>É o limite superior do intervalo quando o atleta tem dias de descanso materializados
     * depois do último treino.</p>
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: NO
     */
    @Query("SELECT MAX(m.data) FROM MetricasDiarias m WHERE m.atleta.id = :atletaId")
    LocalDate findDataUltimaMetrica(UUID atletaId);
}
