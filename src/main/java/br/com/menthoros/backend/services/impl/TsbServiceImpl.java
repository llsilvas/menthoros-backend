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
import br.com.menthoros.backend.services.helper.ThresholdInferenceService;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final ThresholdInferenceService thresholdInferenceService;

    private static final int CTL_TIME_CONSTANT = 42;
    private static final int ATL_TIME_CONSTANT = 7;

    private record IntervaloRecalculo(LocalDate inicio, LocalDate fim) {}

    @Transactional
    public void atualizarTsbDia(UUID atletaId, LocalDate data) {
        atualizarTsbDia(atletaId, data, true);
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
        return treinoRealizadoRepository.findByAtletaIdAndDataTreino(atletaId, data);
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

        atualizarLimiareInferidos(atletaId, metricas.getAtleta(), metaDados, LocalDate.now());

        planoMetaDadosRepository.save(metaDados);
    }

    private void atualizarLimiareInferidos(UUID atletaId, Atleta atleta,
                                            PlanoMetaDados metaDados, LocalDate hoje) {
        boolean fcStale = atleta.getFcLimiar() == null || atleta.getDataUltimoTesteFc() == null
                || ChronoUnit.DAYS.between(atleta.getDataUltimoTesteFc(), hoje)
                        > ThresholdInferenceService.DIAS_LIMIAR_DESATUALIZACAO;
        boolean paceStale = atleta.getPaceLimiar() == null || atleta.getDataUltimoTestePace() == null
                || ChronoUnit.DAYS.between(atleta.getDataUltimoTestePace(), hoje)
                        > ThresholdInferenceService.DIAS_LIMIAR_DESATUALIZACAO;

        if (!fcStale && !paceStale) return;

        UUID tenantId = atleta.getAssessoria().getId();
        List<TreinoRealizado> treinos30d = treinoRealizadoRepository
                .findByAtletaIdAndTenantIdAndDataTreinoBetween(atletaId, tenantId, hoje.minusDays(30), hoje);

        if (fcStale) {
            thresholdInferenceService.inferirFcLimiar(treinos30d, hoje)
                    .ifPresent(est -> {
                        metaDados.setFcLimiarEstimado(est.valor());
                        metaDados.setConfiancaInferenciaFc(est.confianca());
                        metaDados.setDataInferenciaLimiar(hoje);
                    });
        }
        if (paceStale) {
            thresholdInferenceService.inferirPaceLimiar(treinos30d, hoje)
                    .ifPresent(est -> {
                        metaDados.setPaceLimiarEstimado(est.valor());
                        metaDados.setConfiancaInferenciaPace(est.confianca());
                        metaDados.setDataInferenciaLimiar(hoje);
                    });
        }
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
     * <p><b>ATENÇÃO:</b> Operação custosa! Faz backup automático e rollback em caso de erro.
     *
     * @param atletaId ID do atleta para recalcular
     * @throws IllegalArgumentException se atletaId for nulo ou atleta não existir
     * @throws RuntimeException se falhar durante recálculo (dados são restaurados do backup)
     */
    @Transactional
    public void recalcularHistoricoCompleto(UUID atletaId) {
        validarAtletaExiste(atletaId);

        log.warn("🔄 RECALCULANDO HISTÓRICO COMPLETO para atleta {} - operação custosa!", atletaId);

        // 1. Criar backup dos dados atuais
        List<MetricasDiarias> backup = metricasDiariasRepository
                .findByAtletaIdOrderByDataAsc(atletaId);
        log.info("📦 Backup criado: {} registros", backup.size());

        try {
            // 2. Deletar dados antigos
            metricasDiariasRepository.deleteByAtletaId(atletaId);
            metricasDiariasRepository.flush(); // Forçar delete no banco
            log.info("🗑️ Métricas antigas deletadas");

            // 3. Determinar período a recalcular
            IntervaloRecalculo intervalo = determinarIntervaloRecalculo(atletaId, backup);
            if (intervalo == null) {
                zerarMetaDadosSemHistorico(atletaId);
                log.info("ℹ️ Nenhum histórico relevante encontrado para atleta {}. MetaDados zerados.", atletaId);
                return;
            }

            // 4. Recalcular período com tracking de progresso
            recalcularPeriodoComProgresso(atletaId, intervalo.inicio(), intervalo.fim());

            MetricasDiarias ultimaMetrica = metricasDiariasRepository
                    .findByAtletaIdAndData(atletaId, intervalo.fim())
                    .orElseThrow(() -> new IllegalStateException(
                            "Última métrica não encontrada após recálculo para atleta " + atletaId));
            atualizarMetaDados(atletaId, ultimaMetrica);

            // 5. Recalcular progressão contínua de semanas com base nos treinos realizados
            recalcularSemanasProgressao(atletaId);

            log.info("✅ Histórico recalculado com sucesso para atleta {}", atletaId);

        } catch (Exception e) {
            log.error("❌ Erro ao recalcular histórico para atleta {}. A transação será revertida e o banco voltará ao estado anterior.",
                    atletaId, e);

            throw new RuntimeException(
                    "Falha ao recalcular histórico para atleta " + atletaId +
                    ". A transação foi revertida.", e);
        }
    }

    /**
     * Valida se o atleta existe no sistema
     */
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
    private IntervaloRecalculo determinarIntervaloRecalculo(UUID atletaId, List<MetricasDiarias> backup) {
        LocalDate primeiroTreino = treinoRealizadoRepository.findDataPrimeiroTreino(atletaId);
        List<TreinoRealizado> treinosDesc = treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId);
        LocalDate ultimoTreino = treinosDesc.isEmpty() ? null : treinosDesc.getFirst().getDataTreino();

        LocalDate primeiraMetrica = backup.isEmpty() ? null : backup.getFirst().getData();
        LocalDate ultimaMetrica = backup.isEmpty() ? null : backup.get(backup.size() - 1).getData();

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
     * Recalcula período dia a dia com tracking de progresso
     */
    private void recalcularPeriodoComProgresso(UUID atletaId, LocalDate dataInicio, LocalDate dataFim) {
        long totalDias = java.time.temporal.ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
        log.info("📊 Recalculando {} dias (de {} até {})", totalDias, dataInicio, dataFim);

        LocalDate dataAtual = dataInicio;
        long diasProcessados = 0;
        long intervaloLog = Math.max(1, totalDias / 10); // Log a cada 10%

        while (!dataAtual.isAfter(dataFim)) {
            atualizarTsbDia(atletaId, dataAtual, false);
            diasProcessados++;

            // Log de progresso a cada 10% ou no último dia
            if (diasProcessados % intervaloLog == 0 || diasProcessados == totalDias) {
                double progresso = (diasProcessados * 100.0) / totalDias;
                log.info("⏳ Progresso: {}/{} dias ({}%)",
                        diasProcessados, totalDias, String.format("%.1f", progresso));
            }

            dataAtual = dataAtual.plusDays(1);
        }
    }

    private void zerarMetaDadosSemHistorico(UUID atletaId) {
        PlanoMetaDados metaDados = planoMetaDadosRepository
                .findByAtletaId(atletaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "MetaDados não encontrado para atleta: " + atletaId));

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
