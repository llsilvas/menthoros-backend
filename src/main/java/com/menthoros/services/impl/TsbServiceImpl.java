package com.menthoros.services.impl;

import com.menthoros.entity.Atleta;
import com.menthoros.entity.MetricasDiarias;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.MetricasDiariasRepository;
import com.menthoros.repository.PlanoMetadadosRepository;
import com.menthoros.repository.TreinoRealizadoRepository;
import com.menthoros.services.TsbService;
import com.menthoros.services.helper.TssCalculatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

    private static final int CTL_TIME_CONSTANT = 42;
    private static final int ATL_TIME_CONSTANT = 7;


    @Transactional
    public void atualizarTsbDia(UUID atletaId, LocalDate data) {
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
        atualizarMetaDados(atletaId, metricasHoje);

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

        double novoCtl = calcularCtlCorreto(ctlAnterior, tssHoje, atleta);
        double novoAtl = calcularAtlCorreto(atlAnterior, tssHoje, atleta);
        double novoTsb = novoCtl - novoAtl;
        double rampRate = calcularRampRate(atletaId, data, novoCtl);

        metricasHoje.setTss(tssHoje);
        metricasHoje.setCtl(round(novoCtl, 2));
        metricasHoje.setAtl(round(novoAtl, 2));
        metricasHoje.setTsb(round(novoTsb, 2));
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
        metaDados.setTsbAtual(metricas.getTsb());
        metaDados.setRampRateAtual(metricas.getRampRate());
        metaDados.setDataUltimaAtualizacao(LocalDate.now());

        // Atualizar dias consecutivos ANTES da análise (ISSUE-06)
        boolean hojeTemTreino = metricas.getTreinosRealizados() != null && metricas.getTreinosRealizados() > 0;
        metaDados.setDiasConsecutivosTreino(
                contarDiasConsecutivosTreino(atletaId, metricas.getData(), hojeTemTreino));

        // Analisar métricas e aplicar alertas/status/recomendação
        metaDados.aplicarAnalise(metricasAlertaService.analisarMetricas(metaDados));

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

        int consecutivos = 1; // hoje conta
        LocalDate dia = data.minusDays(1);

        for (int i = 0; i < 14; i++) {
            List<TreinoRealizado> treinos = treinoRealizadoRepository
                    .findByAtletaIdAndDataTreino(atletaId, dia);
            if (treinos.isEmpty()) break;
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
            LocalDate dataInicio = determinarDataInicio(atletaId);
            LocalDate dataFim = LocalDate.now();

            // 4. Recalcular período com tracking de progresso
            recalcularPeriodoComProgresso(atletaId, dataInicio, dataFim);

            log.info("✅ Histórico recalculado com sucesso para atleta {}", atletaId);

        } catch (Exception e) {
            log.error("❌ Erro ao recalcular histórico. Restaurando backup de {} registros...",
                    backup.size(), e);

            try {
                metricasDiariasRepository.saveAll(backup);
                metricasDiariasRepository.flush();
                log.info("♻️ Backup restaurado com sucesso");
            } catch (Exception restoreError) {
                log.error("🚨 CRÍTICO: Falha ao restaurar backup!", restoreError);
            }

            throw new RuntimeException(
                    "Falha ao recalcular histórico para atleta " + atletaId +
                    ". Dados foram restaurados do backup.", e);
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
    private LocalDate determinarDataInicio(UUID atletaId) {
        LocalDate dataInicio = treinoRealizadoRepository.findDataPrimeiroTreino(atletaId);
        if (dataInicio == null) {
            log.warn("⚠️ Nenhum treino encontrado. Usando 3 meses atrás como data inicial");
            dataInicio = LocalDate.now().minusMonths(3);
        } else {
            log.info("📅 Data do primeiro treino: {}", dataInicio);
        }
        return dataInicio;
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
            atualizarTsbDia(atletaId, dataAtual);
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
     * Arredonda valor para N casas decimais
     */
    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

}

