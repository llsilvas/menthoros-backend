package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.AthleteThresholdUpdater;
import br.com.menthoros.backend.services.helper.TsbRecalculoExecutor;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TsbServiceImpl implements TsbService {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final PlanoMetadadosRepository planoMetaDadosRepository;
    private final MetricasDiariasRepository metricasDiariasRepository;
    private final AtletaRepository atletaRepository;
    private final TssCalculatorService tssCalculatorService;
    private final MetricasAlertaService metricasAlertaService;
    private final AthleteThresholdUpdater athleteThresholdUpdater;
    private final TsbRecalculoExecutor tsbRecalculoExecutor;

    private static final int CTL_TIME_CONSTANT = 42;
    private static final int ATL_TIME_CONSTANT = 7;

    /** Tamanho do bloco transacional do recálculo histórico. */
    static final int DIAS_POR_BLOCO = 30;

    private record IntervaloRecalculo(LocalDate inicio, LocalDate fim) {}

    /** Quanto do período foi efetivamente reconstruído, para a mensagem de falha. */
    private record ProgressoRecalculo(int blocos, LocalDate ultimoDiaReconstruido) {}

    /**
     * Atualiza o TSB de um único dia, em transação própria.
     *
     * <p><b>Este {@code @Transactional} não vale no fluxo de recálculo histórico.</b> Lá o laço chama
     * a sobrecarga privada de 3 argumentos diretamente — auto-invocação não passa pelo proxy do
     * Spring, então a anotação é ignorada. Isso é intencional: no recálculo, a fronteira transacional
     * é o bloco de {@value #DIAS_POR_BLOCO} dias em {@link TsbRecalculoExecutor}, não o dia. A
     * anotação existe para os chamadores externos deste método público, que precisam de transação
     * própria.</p>
     *
     * Idempotent: YES — recalcular o mesmo dia produz o mesmo resultado.
     * Side Effects: Database insert/update da métrica do dia e do PlanoMetaDados.
     * Tenant-aware: NO
     */
    @Transactional
    public void atualizarTsbDia(UUID atletaId, LocalDate data) {
        atualizarTsbDia(atletaId, data, true);
    }

    /**
     * Idempotent: YES — recalcular o mesmo intervalo produz o mesmo resultado.
     * Side Effects: Database insert/update das métricas de cada dia do intervalo e do
     * PlanoMetaDados, no último dia.
     * Tenant-aware: NO
     */
    @Override
    @Transactional
    public void recalcularDesde(UUID atletaId, LocalDate data) {
        validarEntrada(atletaId, data);

        LocalDate ultimoDia = metricasDiariasRepository.findDataUltimaMetrica(atletaId);
        LocalDate fim = (ultimoDia == null || ultimoDia.isBefore(LocalDate.now()))
                ? LocalDate.now()
                : ultimoDia;

        log.info("Recalculando TSB de {} para atleta {} até {}", data, atletaId, fim);

        for (LocalDate dia = data; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            atualizarTsbDia(atletaId, dia, dia.equals(fim));
        }
    }

    private void atualizarTsbDia(UUID atletaId, LocalDate data, boolean atualizarMetaDadosHoje) {
        validarEntrada(atletaId, data);

        log.info("Atualizando TSB para atleta {} no dia {}", atletaId, data);

        Atleta atleta = buscarAtleta(atletaId);
        List<TreinoRealizado> treinosDoDia = buscarTreinosDia(atletaId, data);
        Integer tssHoje = tssCalculatorService.calcularTssDia(treinosDoDia);

        MetricasDiarias metricasHoje = obterOuCriarMetricasDia(atleta, data);
        atualizarVolumeDiario(metricasHoje, treinosDoDia);

        MetricasDiarias metricasOntem = buscarMetricasDiaAnterior(atletaId, data);
        calcularEAtualizarMetricas(metricasHoje, metricasOntem, tssHoje, atletaId, data);

        metricasDiariasRepository.save(metricasHoje);
        if (atualizarMetaDadosHoje) {
            atualizarMetaDados(atletaId, metricasHoje);
        }

        logResultado(data, metricasHoje);
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

    private Atleta buscarAtleta(UUID atletaId) {
        return atletaRepository.findById(atletaId)
                .orElseThrow(() -> new IllegalArgumentException("Atleta não encontrado: " + atletaId));
    }

    private List<TreinoRealizado> buscarTreinosDia(UUID atletaId, LocalDate data) {
        return treinoRealizadoRepository.findQueContamByAtletaIdAndDataTreino(atletaId, data);
    }

    private MetricasDiarias obterOuCriarMetricasDia(Atleta atleta, LocalDate data) {
        return metricasDiariasRepository
                .findByAtletaIdAndData(atleta.getId(), data)
                .orElseGet(() -> MetricasDiarias.builder()
                        .atleta(atleta)
                        .tenantId(atleta.getAssessoria().getId())
                        .data(data)
                        .volumeKm(BigDecimal.ZERO)
                        .treinosRealizados(0)
                        .build());
    }

    private MetricasDiarias buscarMetricasDiaAnterior(UUID atletaId, LocalDate data) {
        return metricasDiariasRepository
                .findByAtletaIdAndData(atletaId, data.minusDays(1))
                .orElse(null);
    }

    private void calcularEAtualizarMetricas(MetricasDiarias metricasHoje, MetricasDiarias metricasOntem,
                                            Integer tssHoje, UUID atletaId, LocalDate data) {
        Atleta atleta = metricasHoje.getAtleta();
        double ctlAnterior = obterCtlAnterior(metricasOntem);
        double atlAnterior = obterAtlAnterior(metricasOntem);

        // === SEMÂNTICA CORRETA: início do dia = estado de ontem (pré-treino) ===
        double ctlInicio = ctlAnterior;
        double atlInicio = atlAnterior;
        double tsbInicio = ctlInicio - atlInicio;

        // === Fim do dia = estado após absorver carga de hoje ===
        double ctlFim = calcularCtlCorreto(ctlAnterior, tssHoje, atleta);
        double atlFim = calcularAtlCorreto(atlAnterior, tssHoje, atleta);
        double tsbFim = ctlFim - atlFim;

        double rampRate = calcularRampRate(atletaId, data, ctlFim);

        // Campos de início de dia (prontidão pré-treino)
        metricasHoje.setCtlInicioDia(round(ctlInicio, 2));
        metricasHoje.setAtlInicioDia(round(atlInicio, 2));
        metricasHoje.setTsbInicioDia(round(tsbInicio, 2));

        // Campos de fim de dia (estado pós-carga)
        metricasHoje.setCtlFimDia(round(ctlFim, 2));
        metricasHoje.setAtlFimDia(round(atlFim, 2));
        metricasHoje.setTsbFimDia(round(tsbFim, 2));

        // Campos legados — mantidos para compatibilidade, mapeados para fim de dia
        metricasHoje.setTss(tssHoje);
        metricasHoje.setCtl(round(ctlFim, 2));
        metricasHoje.setAtl(round(atlFim, 2));
        metricasHoje.setTsb(round(tsbFim, 2));
        metricasHoje.setRampRate(round(rampRate, 2));
    }

    private double obterCtlAnterior(MetricasDiarias metricasOntem) {
        return metricasOntem != null && metricasOntem.getCtl() != null
                ? metricasOntem.getCtl()
                : 0.0;
    }

    private double obterAtlAnterior(MetricasDiarias metricasOntem) {
        return metricasOntem != null && metricasOntem.getAtl() != null
                ? metricasOntem.getAtl()
                : 0.0;
    }

    private void logResultado(LocalDate data, MetricasDiarias metricas) {
        log.info("TSB atualizado para {} - CTL: {}, ATL: {}, TSB: {}, Volume: {}km",
                data, metricas.getCtl(), metricas.getAtl(), metricas.getTsb(), metricas.getVolumeKm());
    }

    private void atualizarVolumeDiario(MetricasDiarias metricas, List<TreinoRealizado> treinosDoDia) {
        if (treinosDoDia.isEmpty()) {
            metricas.setVolumeKm(BigDecimal.ZERO);
            metricas.setTreinosRealizados(0);
            return;
        }

        // SOMA o volume de TODOS os treinos do dia
        BigDecimal volumeTotal = treinosDoDia.stream()
                .map(treino -> treino.getDistanciaKm() != null
                        ? treino.getDistanciaKm()
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        metricas.setVolumeKm(volumeTotal);
        metricas.setTreinosRealizados(treinosDoDia.size());
    }

    /**
     * FÓRMULA CORRETA de CTL usando média móvel exponencial
     * CTL = (TSS × (1 - e^(-1/τ))) + (CTL_anterior × e^(-1/τ))
     *
     * @param ctlAnterior CTL do dia anterior
     * @param tss TSS do dia atual
     * @param atleta Atleta com constante de tempo personalizada (ou null para padrão)
     */
    private double calcularCtlCorreto(Double ctlAnterior, Integer tss, Atleta atleta) {
        if (ctlAnterior == null) ctlAnterior = 0.0;
        if (tss == null) tss = 0;

        double tau = obterCtlTimeConstant(atleta);
        double exp = Math.exp(-1.0 / tau);

        // Fórmula correta
        return (tss * (1 - exp)) + (ctlAnterior * exp);
    }

    /**
     * FÓRMULA CORRETA de ATL usando média móvel exponencial
     * ATL = (TSS × (1 - e^(-1/τ))) + (ATL_anterior × e^(-1/τ))
     *
     * @param atlAnterior ATL do dia anterior
     * @param tss TSS do dia atual
     * @param atleta Atleta com constante de tempo personalizada (ou null para padrão)
     */
    private double calcularAtlCorreto(Double atlAnterior, Integer tss, Atleta atleta) {
        if (atlAnterior == null) atlAnterior = 0.0;
        if (tss == null) tss = 0;

        double tau = obterAtlTimeConstant(atleta);
        double exp = Math.exp(-1.0 / tau);

        // Fórmula correta
        return (tss * (1 - exp)) + (atlAnterior * exp);
    }

    /**
     * Calcula Ramp Rate (mudança semanal de CTL)
     */
    private double calcularRampRate(UUID atletaId, LocalDate data, double ctlAtual) {
        MetricasDiarias metricasSemanaPassada = metricasDiariasRepository
                .findByAtletaIdAndData(atletaId, data.minusDays(7))
                .orElse(null);

        if (metricasSemanaPassada == null) {
            return 0.0;
        }

        return ctlAtual - metricasSemanaPassada.getCtl();
    }

    /**
     * Atualiza valores atuais no PlanoMetaDados
     */
    private void atualizarMetaDados(UUID atletaId, MetricasDiarias metricas) {
        PlanoMetaDados metaDados = planoMetaDadosRepository
                .findByAtletaId(atletaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "MetaDados não encontrado para atleta: " + atletaId));


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
                contarDiasConsecutivosTreino(atletaId, metricas.getData(), hojeTemTreino));

        // Analisar métricas e aplicar alertas/status/recomendação (com nível de experiência para thresholds adaptativos)
        metaDados.aplicarAnalise(metricasAlertaService.analisarMetricas(metaDados, metricas.getAtleta().getNivelExperiencia()));

        athleteThresholdUpdater.atualizarLimiares(metricas.getAtleta(), metaDados, LocalDate.now());

        planoMetaDadosRepository.save(metaDados);
    }

    /**
     * Conta dias consecutivos de treino até a data informada (inclusive).
     *
     * <p>Percorre os dias para trás a partir de {@code data}, verificando se
     * há treinos registrados. Para no primeiro dia sem treino ou após 14 dias
     * (limite de segurança).
     *
     * @param atletaId  ID do atleta
     * @param data      data do dia sendo processado
     * @param hojeTemTreino se o dia atual possui treinos
     * @return número de dias consecutivos com treino (0 se hoje é descanso)
     */
    private int contarDiasConsecutivosTreino(UUID atletaId, LocalDate data, boolean hojeTemTreino) {
        if (!hojeTemTreino) {
            return 0;
        }

        LocalDate janela = data.minusDays(14);
        List<TreinoRealizado> historico = treinoRealizadoRepository
                .findByAtletaIdAndDataTreinoBetween(atletaId, janela, data.minusDays(1));

        java.util.Set<LocalDate> diasComTreino = historico.stream()
                .map(TreinoRealizado::getDataTreino)
                .collect(java.util.stream.Collectors.toSet());

        int consecutivos = 1; // hoje conta
        LocalDate dia = data.minusDays(1);
        for (int i = 0; i < 14; i++) {
            if (!diasComTreino.contains(dia)) break;
            consecutivos++;
            dia = dia.minusDays(1);
        }

        return consecutivos;
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
                atualizarMetaDados(atletaId, ultimaMetrica);
                recalcularSemanasProgressao(atletaId);
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
                        (id, data) -> atualizarTsbDia(id, data, false));
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

    /**
     * Obtém constante de tempo CTL adaptativa baseada no nível de experiência
     *
     * <p><b>Fundamento Fisiológico:</b>
     * <ul>
     *   <li>Iniciantes adaptam mais rápido (30 dias) mas também perdem forma mais rápido</li>
     *   <li>Avançados adaptam mais lento (42 dias) mas mantêm forma por mais tempo</li>
     *   <li>Elite tem adaptação muito gradual (50 dias) e forma muito estável</li>
     * </ul>
     *
     * <p>Se o atleta tiver valor personalizado definido, usa esse valor.
     * Caso contrário, usa valor padrão baseado no {@code NivelExperiencia}.
     *
     * @param atleta Atleta com nível de experiência e/ou constante personalizada
     * @return Constante de tempo para CTL (em dias)
     */
    private int obterCtlTimeConstant(Atleta atleta) {
        if (atleta == null) {
            return CTL_TIME_CONSTANT; // 42 dias (padrão)
        }

        // Se tem valor personalizado, usar ele
        if (atleta.getCtlTimeConstant() != null) {
            return atleta.getCtlTimeConstant();
        }

        // Caso contrário, usar valor baseado na experiência
        return switch (atleta.getNivelExperiencia()) {
            case INICIANTE -> 30;      // Adapta rápido
            case INTERMEDIARIO -> 35;  // Moderado
            case AVANCADO -> 42;       // Padrão clássico
            case ELITE -> 50;          // Adapta lento, mais estável
        };
    }

    /**
     * Obtém constante de tempo ATL adaptativa baseada no nível de experiência
     *
     * <p><b>Fundamento Fisiológico:</b>
     * <ul>
     *   <li>Iniciantes recuperam mais rápido (5 dias) mas sobrecarregam fácil</li>
     *   <li>Avançados recuperam mais lento (7 dias) mas têm maior resiliência</li>
     *   <li>Elite tem recuperação mais lenta (8 dias) mas suporta maior carga</li>
     * </ul>
     *
     * <p>Se o atleta tiver valor personalizado definido, usa esse valor.
     * Caso contrário, usa valor padrão baseado no {@code NivelExperiencia}.
     *
     * @param atleta Atleta com nível de experiência e/ou constante personalizada
     * @return Constante de tempo para ATL (em dias)
     */
    private int obterAtlTimeConstant(Atleta atleta) {
        if (atleta == null) {
            return ATL_TIME_CONSTANT; // 7 dias (padrão)
        }

        // Se tem valor personalizado, usar ele
        if (atleta.getAtlTimeConstant() != null) {
            return atleta.getAtlTimeConstant();
        }

        // Caso contrário, usar valor baseado na experiência
        return switch (atleta.getNivelExperiencia()) {
            case INICIANTE -> 5;       // Recupera rápido
            case INTERMEDIARIO -> 6;   // Moderado
            case AVANCADO -> 7;        // Padrão clássico
            case ELITE -> 8;           // Recupera lento, maior resiliência
        };
    }

    /**
     * Recalcula {@code semanasProgressaoContinua} no {@link PlanoMetaDados} com base nos
     * {@link MetricasDiarias} realizados, sem depender do volume planejado.
     *
     * <p>Agrupa os registros diários por semana (início = segunda-feira), soma o volume
     * de cada semana e percorre cronologicamente: se a semana atual teve volume maior que
     * a anterior, incrementa o streak; caso contrário, reseta para zero. O valor final
     * representa as semanas consecutivas de progressão de volume até hoje.
     *
     * @param atletaId ID do atleta a recalcular
     */
    private void recalcularSemanasProgressao(UUID atletaId) {
        PlanoMetaDados metaDados = planoMetaDadosRepository
                .findByAtletaId(atletaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "MetaDados não encontrado para atleta: " + atletaId));



        List<MetricasDiarias> todasMetricas = metricasDiariasRepository
                .findByAtletaIdOrderByDataAsc(atletaId);

        if (todasMetricas.isEmpty()) {
            metaDados.setSemanasProgressaoContinua(0);
            planoMetaDadosRepository.save(metaDados);
            log.info("Nenhuma métrica encontrada — semanasProgressaoContinua zerada para atleta {}", atletaId);
            return;
        }

        // Agrupar por início da semana (segunda-feira) somando volumeKm.
        // TreeMap garante ordem cronológica das chaves.
        TreeMap<LocalDate, BigDecimal> volumePorSemana = todasMetricas.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getData().with(DayOfWeek.MONDAY),
                        TreeMap::new,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                m -> m.getVolumeKm() != null ? m.getVolumeKm() : BigDecimal.ZERO,
                                BigDecimal::add
                        )
                ));

        // Percorrer semanas cronologicamente e contar streak de aumento de volume
        List<BigDecimal> volumes = new ArrayList<>(volumePorSemana.values());
        int semanasConsecutivas = 0;

        for (int i = 1; i < volumes.size(); i++) {
            if (volumes.get(i).compareTo(volumes.get(i - 1)) > 0) {
                semanasConsecutivas++;
            } else {
                semanasConsecutivas = 0;
            }
        }



        metaDados.setSemanasProgressaoContinua(semanasConsecutivas);
        metaDados.setDataUltimaAtualizacao(LocalDate.now());

        // Reaplicar análise de alertas e recomendação considerando o nível de experiência do atleta
        Atleta atleta = buscarAtleta(atletaId);
        metaDados.aplicarAnalise(metricasAlertaService.analisarMetricas(metaDados, atleta.getNivelExperiencia()));

        planoMetaDadosRepository.save(metaDados);
        log.info("semanasProgressaoContinua recalculadas: {} para atleta {} (nível: {})",
                semanasConsecutivas, atletaId, atleta.getNivelExperiencia());
    }

    /**
     * Arredonda valor para N casas decimais
     */
    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

}
