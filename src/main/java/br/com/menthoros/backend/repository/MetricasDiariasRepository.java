package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.repository.projection.SerieMetricasAtleta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetricasDiariasRepository extends JpaRepository<MetricasDiarias, UUID> {

    Optional<MetricasDiarias> findByAtletaIdAndData(UUID atletaId, LocalDate data);

    /** Última métrica estritamente anterior a {@code data}: base do seed quando o dia anterior não tem linha. */
    Optional<MetricasDiarias> findTopByAtletaIdAndDataBeforeOrderByDataDesc(UUID atletaId, LocalDate data);

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

    /**
     * Serializa os recálculos de um atleta (ingestão, job de decaimento e reparo) com um advisory
     * lock de transação. O lock é liberado no commit/rollback da transação que o adquiriu, então
     * quem espera só lê treinos e métricas depois que a ingestão concorrente confirmou
     * (fix-tsb-rest-days-materialization, design.md R2).
     *
     * <p>{@code MANDATORY}: fora de uma transação o lock seria solto na hora e a serialização
     * seria falsa — melhor falhar alto. {@code hashtext} colidindo entre dois atletas só serializa
     * a mais, nunca de menos. O retorno em texto existe só porque {@code pg_advisory_xact_lock}
     * devolve {@code void}, que o mapeamento nativo não sabe ler.</p>
     *
     * Idempotent: YES — adquirir um lock que a transação já tem é no-op.
     * Side Effects: bloqueia outras transações que peçam o mesmo lock até o commit desta.
     * Tenant-aware: NO
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT CAST(pg_advisory_xact_lock(hashtext(CAST(:atletaId AS text))) AS text)",
            nativeQuery = true)
    String lockAtleta(UUID atletaId);

    /**
     * Uma linha por atleta com métricas: última data e primeiro dia ausente entre a primeira e a
     * última métrica. Uma query para o lote inteiro (o job horário não faz N consultas). O
     * {@code generate_series} só roda para quem tem menos linhas que dias — a comparação
     * {@code COUNT(*)} contra o intervalo evita varrer a série de quem está densa. A unicidade
     * {@code (atleta_id, data)} (índice {@code idx_atleta_data}) é o que torna a contagem válida.
     *
     * <p>O "hoje" é do atleta (fuso dele), então a decisão de "atrasado" fica no chamador, via
     * {@link SerieMetricasAtleta#primeiroDiaPendente}.</p>
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: NO — cross-tenant por natureza (job de manutenção).
     */
    @Query(value = """
            SELECT t.atleta_id AS "atletaId",
                   t.ultima    AS "ultimaData",
                   CASE WHEN t.total < (t.ultima - t.primeira + 1) THEN (
                       SELECT MIN(g.dia)::date
                       FROM generate_series(CAST(t.primeira AS timestamp), CAST(t.ultima AS timestamp),
                                            INTERVAL '1 day') AS g(dia)
                       WHERE NOT EXISTS (SELECT 1 FROM tb_metricas_diarias x
                                         WHERE x.atleta_id = t.atleta_id AND x.data = g.dia::date)
                   ) END AS "primeiroBuraco"
            FROM (SELECT atleta_id, MIN(data) AS primeira, MAX(data) AS ultima, COUNT(*) AS total
                  FROM tb_metricas_diarias GROUP BY atleta_id) t
            """, nativeQuery = true)
    List<SerieMetricasAtleta> findSeriePorAtleta();
}
