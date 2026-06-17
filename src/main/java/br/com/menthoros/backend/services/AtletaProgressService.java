package br.com.menthoros.backend.services;

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

    /** Série PMC diária no intervalo (default: últimos 90 dias quando {@code from}/{@code to} ausentes). */
    List<PmcPontoDto> getHistoricoPmc(UUID atletaId, LocalDate from, LocalDate to);

    /** Distribuição de tempo por zona de FC (z1–z5) no intervalo (default: últimos 90 dias). */
    ZonaDistribuicaoDto getDistribuicaoZonas(UUID atletaId, LocalDate from, LocalDate to);

    /** Recordes pessoais (5k/10k/21k) derivados dos treinos realizados. */
    List<RecordeDto> getRecordes(UUID atletaId);

    /** Readiness atual (heurística objetiva provisória — ver {@link ReadinessDto}). */
    ReadinessDto getReadinessAtual(UUID atletaId);

    /** Resumo "hoje": próximo treino planejado + métricas-chave. */
    AtletaHomeDto getHome(UUID atletaId);

    /** Resolve o {@code atletaId} do usuário autenticado (endpoints {@code me/*}). */
    UUID resolverAtletaIdAtual();
}
