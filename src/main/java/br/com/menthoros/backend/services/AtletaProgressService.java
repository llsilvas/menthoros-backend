package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.Aderencia4SemanasDto;
import br.com.menthoros.backend.dto.output.AderenciasSemanalDto;
import br.com.menthoros.backend.dto.output.DistanceSummaryDto;
import br.com.menthoros.backend.dto.output.AtletaHomeDto;
import br.com.menthoros.backend.dto.output.PmcPontoDto;
import br.com.menthoros.backend.dto.output.ReadinessDto;
import br.com.menthoros.backend.dto.output.RecordeDto;
import br.com.menthoros.backend.dto.output.ZonaDistribuicaoDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Leitura de progresso do atleta para o shell: PMC, zonas, recordes, readiness e resumo "hoje".
 * Todas as operações são read-only e tenant-aware.
 */
public interface AtletaProgressService {

    /** Limite superior de {@code semanas} aceito por {@link #getAderenciaSemanal} (2 anos). */
    int MAX_SEMANAS_ADERENCIA = 104;

    /**
     * Série PMC diária no intervalo (default: últimos 90 dias quando {@code from}/{@code to} ausentes).
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     */
    List<PmcPontoDto> getHistoricoPmc(UUID atletaId, LocalDate from, LocalDate to);

    /**
     * Distribuição de tempo por zona de FC (z1–z5) no intervalo (default: últimos 90 dias).
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     */
    ZonaDistribuicaoDto getDistribuicaoZonas(UUID atletaId, LocalDate from, LocalDate to);

    /**
     * Recordes pessoais (5k/10k/21k) derivados dos treinos realizados.
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     */
    List<RecordeDto> getRecordes(UUID atletaId);

    /**
     * Readiness atual (heurística objetiva provisória — ver {@link ReadinessDto}).
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     */
    ReadinessDto getReadinessAtual(UUID atletaId);

    /**
     * Resumo "hoje": próximo treino planejado + métricas-chave.
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     */
    AtletaHomeDto getHome(UUID atletaId);

    /**
     * Aderência semanal ao plano nas últimas {@code semanas} semanas (segunda a domingo, ISO-8601).
     *
     * <p>Retorna lista vazia quando nenhuma semana tem {@code totalPlanejado > 0} — o caller
     * deve exibir estado "sem dados" em vez de barras de 0%.
     *
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     *
     * @param atletaId ID do atleta
     * @param semanas  número de semanas a analisar (recomendado: 8)
     */
    List<AderenciasSemanalDto> getAderenciaSemanal(UUID atletaId, int semanas);

    /**
     * Aderência da semana atual + 3 anteriores — mesma janela e mesmo predicado
     * (`contaNaCarga()`) usados pelo roster do coach ({@code CoachAtletaResumoDto.aderenciaPercentual}).
     * Única fonte desse cálculo: roster e perfil chamam este método, nunca reimplementam a consulta
     * (achado do Codex review, fix-athlete-profile-aderencia-4-semanas, 2026-10-01 — o perfil
     * esperava um campo que nunca foi implementado no backend).
     *
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     */
    Aderencia4SemanasDto getAderencia4Semanas(UUID atletaId);

    /**
     * Distância realizada nas últimas {@code weeks} semanas ISO (contínuas, 0 onde não houve treino)
     * e nos últimos 7 dias vs. os 7 anteriores. Mesma janela de semanas de
     * {@link #getAderenciaSemanal}, para o front alinhar pela segunda-feira.
     *
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     *
     * @param atletaId ID do atleta
     * @param weeks    número de semanas, em [1, {@link #MAX_SEMANAS_ADERENCIA}] (recomendado: 8)
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException com {@code weeks} fora do intervalo
     */
    DistanceSummaryDto getDistanceSummary(UUID atletaId, int weeks);

    /**
     * Resolve o {@code atletaId} do usuário autenticado (endpoints {@code me/*}).
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     */
    UUID resolverAtletaIdAtual();
}
