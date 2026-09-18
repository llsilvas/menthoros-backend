package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.projection.LimiarPaceStatusProjection;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.AthleteThresholdUpdater;
import br.com.menthoros.backend.services.helper.PaceLimiarResolvido;
import br.com.menthoros.backend.services.helper.ThresholdInferenceService;
import br.com.menthoros.backend.services.helper.TsbRecalculoExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TsbServiceImpl implements TsbService {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final PlanoMetadadosRepository planoMetaDadosRepository;
    private final MetricasDiariasRepository metricasDiariasRepository;
    private final AtletaRepository atletaRepository;
    private final MetricasAlertaService metricasAlertaService;
    private final AthleteThresholdUpdater athleteThresholdUpdater;
    private final ThresholdInferenceService thresholdInferenceService;
    private final TsbRecalculoExecutor tsbRecalculoExecutor;
    private final PlanoMetadadosService planoMetadadosService;
    private final TsbDiaPersister tsbDiaPersister;

    /** Tamanho do bloco transacional do recálculo histórico. */
    static final int DIAS_POR_BLOCO = 30;

    private record IntervaloRecalculo(LocalDate inicio, LocalDate fim) {}

    /** Quanto do período foi efetivamente reconstruído, para a mensagem de falha. */
    private record ProgressoRecalculo(int blocos, LocalDate ultimoDiaReconstruido) {}

    /**
     * Atualiza o TSB de um único dia.
     *
     * <p><b>Sem {@code @Transactional} neste método</b> (refactor-threshold-call-outside-
     * transaction, design.md D2) — a resolução de fonte de pace
     * ({@link AthleteThresholdUpdater#resolverFontePace}) roda aqui, fora de qualquer transação;
     * a persistência do dia (métrica + metadados) roda em {@link TsbDiaPersister}, bean à parte,
     * cuja `@Transactional` só é respeitada porque a chamada passa pelo proxy Spring de um bean
     * diferente — tirar a anotação deste método sem trocar de bean não encolheria nada (chamar um
     * método anotado via {@code this.} dentro da mesma classe não passa pelo proxy).
     *
     * Idempotent: YES — recalcular o mesmo dia produz o mesmo resultado.
     * Side Effects: Database insert/update da métrica do dia e do PlanoMetaDados.
     * Tenant-aware: NO
     */
    public void atualizarTsbDia(UUID atletaId, LocalDate data) {
        PaceLimiarResolvido paceResolvido = resolverPaceSeNecessario(atletaId, data);
        tsbDiaPersister.atualizarDiaTransacional(atletaId, data, true, paceResolvido);
    }

    /**
     * Sem {@code @Transactional} neste método pelo mesmo motivo de {@link #atualizarTsbDia}
     * (design.md D2) — a resolução de fonte de pace roda **uma vez, com `hoje=fim`** (não a cada
     * dia do laço: só o último dia persiste metadados, `dia.equals(fim)`).
     *
     * Idempotent: YES — recalcular o mesmo intervalo produz o mesmo resultado.
     * Side Effects: Database insert/update das métricas de cada dia do intervalo e do
     * PlanoMetaDados, no último dia.
     * Tenant-aware: NO
     */
    @Override
    public void recalcularDesde(UUID atletaId, LocalDate data) {
        validarEntrada(atletaId, data);

        LocalDate ultimoDia = metricasDiariasRepository.findDataUltimaMetrica(atletaId);
        LocalDate fim = (ultimoDia == null || ultimoDia.isBefore(LocalDate.now()))
                ? LocalDate.now()
                : ultimoDia;

        log.info("Recalculando TSB de {} para atleta {} até {}", data, atletaId, fim);

        PaceLimiarResolvido paceResolvido = resolverPaceSeNecessario(atletaId, fim);
        for (LocalDate dia = data; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            boolean ultimoDiaDoLaco = dia.equals(fim);
            tsbDiaPersister.atualizarDiaTransacional(
                    atletaId, dia, ultimoDiaDoLaco, ultimoDiaDoLaco ? paceResolvido : null);
        }
    }

    /**
     * Decide se `paceLimiarEstimado` precisa ser recalculado e, se sim, resolve a fonte — fora de
     * qualquer transação (refactor-threshold-call-outside-transaction, design.md D2/4.1). Usa
     * {@link AtletaRepository#findLimiarPaceStatusById} em vez de carregar o agregado `Atleta`
     * inteiro (design.md D1): a entidade só existiria dentro da transação de escrita, que ainda
     * não abriu neste ponto.
     *
     * @return {@code null} quando pace não está desatualizado, o atleta não tem assessoria, ou
     *         nenhuma fonte foi encontrada — equivalente a "nenhuma mudança" em
     *         {@link AthleteThresholdUpdater#aplicarPaceLimiar}.
     */
    private PaceLimiarResolvido resolverPaceSeNecessario(UUID atletaId, LocalDate hoje) {
        Optional<LimiarPaceStatusProjection> status = atletaRepository.findLimiarPaceStatusById(atletaId);
        if (status.isEmpty()) {
            log.warn("resolverPaceSeNecessario: atleta {} sem assessoria/inexistente — inferência ignorada", atletaId);
            return null;
        }
        LimiarPaceStatusProjection statusValor = status.get();
        if (!thresholdInferenceService.isPaceLimiarDesatualizado(
                statusValor.getPaceLimiar(), statusValor.getDataUltimoTestePace(), hoje)) {
            return null;
        }

        UUID tenantId = statusValor.getAssessoriaId();
        // D8 (ingestao-treino-realizado): cancelado não conta na carga — mesmo predicado usado por
        // TsbDiaPersister/produtores.
        List<TreinoRealizado> treinos30d = treinoRealizadoRepository
                .findByAtletaIdAndTenantIdAndDataTreinoBetween(atletaId, tenantId, hoje.minusDays(30), hoje)
                .stream()
                .filter(TreinoRealizado::contaNaCarga)
                .toList();
        BigDecimal paceLimiarAnterior = planoMetaDadosRepository.findPaceLimiarEstimadoByAtletaId(atletaId).orElse(null);

        return athleteThresholdUpdater
                .resolverFontePace(atletaId, tenantId, hoje, treinos30d, paceLimiarAnterior)
                .orElse(null);
    }

    private void validarEntrada(UUID atletaId, LocalDate data) {
        if (atletaId == null) {
            throw new IllegalArgumentException("atletaId não pode ser nulo");
        }
        if (data == null) {
            throw new IllegalArgumentException("data não pode ser nula");
        }
        if (data.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("data não pode ser futura: " + data);
        }
    }

    /**
     * Atualiza valores atuais no PlanoMetaDados — usada só pela consolidação de
     * {@link #recalcularHistoricoCompleto} (design.md D2b: fora do escopo do split de fronteira
     * transacional, resolve fonte de pace **dentro** da transação, como sempre fez — operação
     * rara/admin-only, não o caminho de alta frequência que motivou
     * refactor-threshold-call-outside-transaction). O caminho normal (sync de treino) usa
     * {@link TsbDiaPersister#atualizarDiaTransacional} em vez deste método.
     *
     * <p>{@code contarDiasConsecutivosTreino}/{@code recalcularSemanasProgressao} reaproveitados
     * de {@link TsbDiaPersister} (package-private lá) em vez de duplicados aqui.
     */
    private void atualizarMetaDados(UUID atletaId, MetricasDiarias metricas) {
        // Achado do /qa do Bloco 2 (Codex plain review, 2026-08-24): antes desta change,
        // PlanoMetaDados só nascia via PlanoServiceImpl.getPreparaDadosPlano (ao gerar o primeiro
        // plano). Com todos os caminhos de mutação agora convergindo em recalcularDesde/
        // atualizarTsbDia, um atleta com treino mas sem plano ainda gerado 500ava aqui. Usa o
        // mesmo seam de lazy-creation que o resto do sistema (PlanoMetadadosService).
        PlanoMetaDados metaDados = planoMetadadosService.buscarOuCriarMetadados(metricas.getAtleta());

        metaDados.setCtlAtual(metricas.getCtl());
        metaDados.setAtlAtual(metricas.getAtl());
        metaDados.setRampRateAtual(metricas.getRampRate());
        metaDados.setDataUltimaAtualizacao(LocalDate.now());

        // Semântica correta: tsbProntidaoAtual = pré-treino (início do dia)
        Double tsbProntidao = metricas.getTsbInicioDia();
        Double tsbPosCarga  = metricas.getTsbFimDia();
        metaDados.setTsbProntidaoAtual(tsbProntidao != null ? tsbProntidao : 0.0);
        metaDados.setTsbPosCargaAtual(tsbPosCarga   != null ? tsbPosCarga  : 0.0);
        // Compatibilidade legada: tsbAtual aponta para tsbProntidaoAtual
        metaDados.setTsbAtual(tsbProntidao != null ? tsbProntidao : 0.0);

        // Atualizar dias consecutivos ANTES da análise (ISSUE-06)
        boolean hojeTemTreino = metricas.getTreinosRealizados() != null && metricas.getTreinosRealizados() > 0;
        metaDados.setDiasConsecutivosTreino(
                tsbDiaPersister.contarDiasConsecutivosTreino(atletaId, metricas.getData(), hojeTemTreino));

        // Analisar métricas e aplicar alertas/status/recomendação (com nível de experiência para thresholds adaptativos)
        metaDados.aplicarAnalise(metricasAlertaService.analisarMetricas(metaDados, metricas.getAtleta().getNivelExperiencia()));

        athleteThresholdUpdater.atualizarLimiares(metricas.getAtleta(), metaDados, LocalDate.now());

        planoMetaDadosRepository.save(metaDados);

        // fix-progressao-continua-incremental: mantém semanasProgressaoContinua em dia a cada
        // treino real registrado, não só quando recalcularHistoricoCompleto roda — este método já
        // é chamado tanto pelo caminho incremental (recalcularDesde) quanto pelo completo.
        tsbDiaPersister.recalcularSemanasProgressao(atletaId);
    }

    /**
     * Processa dias de descanso (ATL e CTL decaem naturalmente)
     */
    @Transactional
    public void processarDiasDescanso(UUID atletaId, LocalDate dataInicio, LocalDate dataFim) {
        LocalDate dataAtual = dataInicio;

        while (!dataAtual.isAfter(dataFim)) {
            // Verificar se já tem treino nesse dia
            List<TreinoRealizado> treinos = treinoRealizadoRepository
                    .findByAtletaIdAndDataTreino(atletaId, dataAtual);

            if (treinos.isEmpty()) {
                // Dia de descanso - TSS = 0, mas ATL/CTL decaem
                atualizarTsbDia(atletaId, dataAtual);
            }

            dataAtual = dataAtual.plusDays(1);
        }
    }

    /**
     * Recalcula histórico completo (usar apenas em caso de migração)
     *
     * <p>Este método reconstrói todo o histórico de métricas TSB/CTL/ATL do zero.
     * Use quando:
     * <ul>
     *   <li>Mudou algoritmo de cálculo de TSS</li>
     *   <li>Corrigiu dados históricos de treinos</li>
     *   <li>Importou histórico de outro sistema (Strava, Garmin, etc)</li>
     *   <li>Precisa garantir consistência total dos dados</li>
     * </ul>
     *
     * <p><b>ATENÇÃO:</b> Operação custosa! O período é processado em blocos de
     * {@value #DIAS_POR_BLOCO} dias, cada um numa transação própria.
     *
     * <p><b>Não há rollback global.</b> Blocos já comitados permanecem comitados se um bloco
     * posterior falhar — é o preço do chunking, e é deliberado: sem ele, 400+ dias ficariam numa
     * transação só, segurando uma conexão do pool do início ao fim. Em compensação, o delete de cada
     * intervalo acontece dentro da transação que o reconstrói, então <b>nenhum intervalo fica apagado
     * e não reconstruído</b>. Em caso de falha, a exceção informa até onde o histórico foi
     * efetivamente reconstruído.
     *
     * <p><b>Leitores concorrentes:</b> durante o recálculo o histórico fica parcialmente
     * reconstruído e visível. Os consumidores (PMC, home do atleta, dashboard do coach, fila de
     * atenção, agregados semanais) servem o dado disponível, sem bloqueio — contrato explícito, não
     * omissão.
     *
     * Idempotent: YES — recalcular duas vezes produz exatamente o mesmo resultado.
     * Side Effects: Database delete + insert/update das métricas diárias e do PlanoMetaDados.
     * Tenant-aware: NO — o atleta é resolvido pelo id.
     *
     * @param atletaId ID do atleta para recalcular
     * @throws IllegalArgumentException se atletaId for nulo ou atleta não existir
     * @throws RuntimeException se falhar durante o recálculo; a mensagem informa o intervalo
     *         efetivamente reconstruído e o ponto de parada
     */
    public void recalcularHistoricoCompleto(UUID atletaId) {
        validarAtletaExiste(atletaId);

        log.warn("🔄 RECALCULANDO HISTÓRICO COMPLETO para atleta {} - operação custosa!", atletaId);

        // 1. Determinar período a recalcular. Os limites vêm de min/max das métricas existentes
        //    (consulta, não a lista inteira em memória) combinados com o primeiro/último treino.
        IntervaloRecalculo intervalo = determinarIntervaloRecalculo(atletaId);
        if (intervalo == null) {
            tsbRecalculoExecutor.consolidar(() -> zerarMetaDadosSemHistorico(atletaId));
            tsbRecalculoExecutor.invalidarCacheMetadados(atletaId, tenantDe(atletaId));
            log.info("ℹ️ Nenhum histórico relevante encontrado para atleta {}. MetaDados zerados.", atletaId);
            return;
        }

        // 2. Reconstruir em blocos. Cada bloco apaga e reconstrói o próprio intervalo numa
        //    transação própria; não há delete antecipado do histórico inteiro.
        ProgressoRecalculo progresso = recalcularPeriodoComProgresso(
                atletaId, intervalo.inicio(), intervalo.fim());

        // 3. Consolidar metadados. Fase transacional própria: se falhar aqui, os blocos continuam
        //    comitados e os metadados ficam no estado anterior — stale, mas não ambíguo.
        try {
            tsbRecalculoExecutor.consolidar(() -> {
                MetricasDiarias ultimaMetrica = metricasDiariasRepository
                        .findByAtletaIdAndData(atletaId, intervalo.fim())
                        .orElseThrow(() -> new IllegalStateException(
                                "Última métrica não encontrada após recálculo para atleta " + atletaId));
                // fix-progressao-continua-incremental: recalcularSemanasProgressao já roda dentro
                // de atualizarMetaDados — não chamar de novo aqui (duplicaria o trabalho).
                atualizarMetaDados(atletaId, ultimaMetrica);
            });
        } catch (Exception e) {
            tsbRecalculoExecutor.registrarAborto("metadados");
            log.error("❌ Histórico do atleta {} foi reconstruído de {} até {} ({} blocos), "
                            + "mas a consolidação dos metadados falhou. O histórico diário permanece "
                            + "comitado; PlanoMetaDados ficou no estado anterior (stale). "
                            + "Re-disparar o recálculo é seguro — a operação é idempotente.",
                    atletaId, intervalo.inicio(), intervalo.fim(), progresso.blocos(), e);

            throw new RuntimeException(String.format(
                    "Recálculo do atleta %s reconstruiu o histórico de %s até %s (%d blocos), "
                            + "mas falhou ao consolidar os metadados. PlanoMetaDados está no estado "
                            + "anterior ao recálculo.",
                    atletaId, intervalo.inicio(), intervalo.fim(), progresso.blocos()), e);
        }

        tsbRecalculoExecutor.invalidarCacheMetadados(atletaId, tenantDe(atletaId));

        log.info("✅ Histórico recalculado com sucesso para atleta {}: {} até {} ({} blocos)",
                atletaId, intervalo.inicio(), intervalo.fim(), progresso.blocos());
    }

    /**
     * Valida se o atleta existe no sistema
     */
    /** Tenant do atleta — a assessoria. Necessario para a chave do cache de metadados. */
    private UUID tenantDe(UUID atletaId) {
        return atletaRepository.findById(atletaId)
                .map(a -> a.getAssessoria() != null ? a.getAssessoria().getId() : null)
                .orElse(null);
    }

    private void validarAtletaExiste(UUID atletaId) {
        if (atletaId == null) {
            throw new IllegalArgumentException("atletaId não pode ser nulo");
        }
        if (!atletaRepository.findById(atletaId).isPresent()) {
            throw new IllegalArgumentException("Atleta não encontrado: " + atletaId);
        }
    }

    /**
     * Determina data de início para recálculo (data do primeiro treino ou 3 meses atrás)
     */
    private IntervaloRecalculo determinarIntervaloRecalculo(UUID atletaId) {
        LocalDate primeiroTreino = treinoRealizadoRepository.findDataPrimeiroTreino(atletaId);
        List<TreinoRealizado> treinosDesc = treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId);
        LocalDate ultimoTreino = treinosDesc.isEmpty() ? null : treinosDesc.getFirst().getDataTreino();

        // As métricas existentes delimitam o intervalo junto com os treinos: um atleta pode ter dias
        // de descanso materializados depois do último treino, e eles precisam continuar sendo
        // reconstruídos. Consulta de limites em vez de carregar a lista inteira em memória.
        LocalDate primeiraMetrica = metricasDiariasRepository.findDataPrimeiraMetrica(atletaId);
        LocalDate ultimaMetrica = metricasDiariasRepository.findDataUltimaMetrica(atletaId);

        LocalDate dataInicio = menorData(primeiroTreino, primeiraMetrica);
        LocalDate dataFim = maiorData(ultimoTreino, ultimaMetrica);

        if (dataInicio == null && dataFim == null) {
            return null;
        }

        if (dataInicio == null) {
            dataInicio = dataFim;
        }
        if (dataFim == null) {
            dataFim = dataInicio;
        }

        if (dataFim.isAfter(LocalDate.now())) {
            dataFim = LocalDate.now();
        }

        log.info("📅 Intervalo de recálculo: {} até {}", dataInicio, dataFim);
        return new IntervaloRecalculo(dataInicio, dataFim);
    }

    /**
     * Recalcula o período em blocos de {@value #DIAS_POR_BLOCO} dias, cada um numa transação própria.
     *
     * <p><b>A ordem sequencial não é opcional.</b> O CTL/ATL de um dia é calculado a partir do dia
     * anterior, lido do banco; e o {@code rampRate} lê D-7. Um bloco só produz o valor correto se o
     * anterior já tiver comitado. Paralelizar os blocos quebraria o cálculo.</p>
     *
     * <p>Se um bloco falhar, a exceção informa quantos blocos foram efetivamente reconstruídos e
     * onde parou — os anteriores permanecem comitados, e nenhum intervalo fica apagado sem
     * reconstrução, porque o delete de cada bloco vive na transação que o reconstrói.</p>
     */
    private ProgressoRecalculo recalcularPeriodoComProgresso(UUID atletaId, LocalDate dataInicio, LocalDate dataFim) {
        long totalDias = java.time.temporal.ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
        long totalBlocos = (totalDias + DIAS_POR_BLOCO - 1) / DIAS_POR_BLOCO;
        log.info("📊 Recalculando {} dias em {} blocos de até {} dias (de {} até {})",
                totalDias, totalBlocos, DIAS_POR_BLOCO, dataInicio, dataFim);

        LocalDate blocoInicio = dataInicio;
        LocalDate ultimoDiaReconstruido = null;
        int blocosConcluidos = 0;

        while (!blocoInicio.isAfter(dataFim)) {
            LocalDate blocoFim = blocoInicio.plusDays(DIAS_POR_BLOCO - 1L);
            if (blocoFim.isAfter(dataFim)) {
                blocoFim = dataFim;
            }

            try {
                tsbRecalculoExecutor.recalcularBloco(atletaId, blocoInicio, blocoFim,
                        (id, data) -> tsbDiaPersister.atualizarDiaTransacional(id, data, false, null));
            } catch (Exception e) {
                tsbRecalculoExecutor.registrarAborto("blocos");
                String reconstruido = ultimoDiaReconstruido == null
                        ? "nenhum dia"
                        : dataInicio + " até " + ultimoDiaReconstruido;
                log.error("❌ Recálculo do atleta {} falhou no bloco {} até {}. "
                                + "Histórico reconstruído: {} ({} de {} blocos). "
                                + "Os blocos anteriores permanecem comitados e nenhum intervalo ficou "
                                + "apagado sem reconstrução.",
                        atletaId, blocoInicio, blocoFim, reconstruido, blocosConcluidos, totalBlocos, e);

                throw new RuntimeException(String.format(
                        "Recálculo do atleta %s falhou no bloco %s até %s. "
                                + "Histórico efetivamente reconstruído: %s (%d de %d blocos). "
                                + "Os blocos anteriores permanecem comitados.",
                        atletaId, blocoInicio, blocoFim, reconstruido, blocosConcluidos, totalBlocos), e);
            }

            ultimoDiaReconstruido = blocoFim;
            blocosConcluidos++;
            log.info("⏳ Progresso: bloco {}/{} ({} até {})",
                    blocosConcluidos, totalBlocos, blocoInicio, blocoFim);

            blocoInicio = blocoFim.plusDays(1);
        }

        return new ProgressoRecalculo(blocosConcluidos, ultimoDiaReconstruido);
    }

    /**
     * Zera as métricas quando o atleta não tem histórico nenhum.
     *
     * <p><b>Ausência de metadados aqui é normal, não erro.</b> Este caminho roda justamente quando
     * o atleta não tem treino algum — tipicamente um atleta recém-cadastrado —, e nesse momento
     * ninguém criou metadados ainda: quem cria é {@code buscarOuCriarMetadados}, mais adiante no
     * fluxo de geração de plano.
     *
     * <p>Até 2026-08-15 este método lançava {@code IllegalArgumentException} quando não encontrava,
     * o que <b>impedia a geração do primeiro plano de qualquer atleta novo</b>: o erro subia como
     * falha de LLM e o coach via "erro ao gerar plano" sem nenhuma pista. Não havia o que zerar —
     * um atleta sem metadados já está no estado que este método existe para produzir.
     */
    private void zerarMetaDadosSemHistorico(UUID atletaId) {
        PlanoMetaDados metaDados = planoMetaDadosRepository
                .findByAtletaId(atletaId)
                .orElse(null);

        if (metaDados == null) {
            log.debug("Atleta {} ainda não tem metadados; nada a zerar", atletaId);
            return;
        }

        metaDados.setCtlAtual(0.0);
        metaDados.setAtlAtual(0.0);
        metaDados.setTsbAtual(0.0);
        metaDados.setRampRateAtual(0.0);
        metaDados.setDiasConsecutivosTreino(0);
        metaDados.setSemanasProgressaoContinua(0);
        metaDados.aplicarAnalise(metricasAlertaService.analisarMetricas(metaDados, metaDados.getAtleta().getNivelExperiencia()));

        planoMetaDadosRepository.save(metaDados);
    }

    private LocalDate menorData(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private LocalDate maiorData(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

}
