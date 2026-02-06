package com.menthoros.services.impl;

import com.menthoros.dto.output.PadroesTreino;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.MetricasDiarias;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.MetricasDiariasRepository;
import com.menthoros.repository.PlanoMetadadosRepository;
import com.menthoros.repository.TreinoRealizadoRepository;
import com.menthoros.services.TsbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    private static final int CTL_TIME_CONSTANT = 42;
    private static final int ATL_TIME_CONSTANT = 7;


    @Transactional
    public void atualizarTsbDia(UUID atletaId, LocalDate data) {
        validarEntrada(atletaId, data);

        log.info("Atualizando TSB para atleta {} no dia {}", atletaId, data);

        Atleta atleta = buscarAtleta(atletaId);
        List<TreinoRealizado> treinosDoDia = buscarTreinosDia(atletaId, data);
        Integer tssHoje = calcularTssDia(treinosDoDia);

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
     * Calcula TSS total do dia (soma de todos os treinos)
     */
    private Integer calcularTssDia(List<TreinoRealizado> treinos) {
        return treinos.stream()
                .mapToInt(this::calcularTssCorreto)
                .sum();
    }

    /**
     * CÁLCULO CORRETO de TSS para corrida
     * Baseado em Frequência Cardíaca ou Pace
     */
    private int calcularTssCorreto(TreinoRealizado treino) {
        int tssBase;

        // Se tem dados de FC, usar método de FC
        if (treino.getFcMedia() != null && treino.getFcMedia() > 0) {
            tssBase = calcularTssFrequenciaCardiaca(treino);
        }
        // Se tem dados de pace, usar método de pace
        else if (treino.getPaceMedia() != null) {
            tssBase = calcularTssPace(treino);
        }
        // Fallback: usar RPE (menos preciso mas melhor que nada)
        else {
            tssBase = calcularTssRpe(treino);
        }

        // Aplicar fator de impacto por tipo de treino
        return aplicarFatorImpactoTreino(tssBase, treino);
    }

    /**
     * Aplica fator de impacto fisiológico baseado no tipo de treino
     *
     * <p>Fundamento: Treinos com mesma intensidade (TSS) têm impactos diferentes:
     * <ul>
     *   <li>Intervalados causam maior fadiga neuromuscular</li>
     *   <li>Longões causam maior depleção de glicogênio</li>
     *   <li>Tiros de subida causam maior fadiga muscular</li>
     * </ul>
     *
     * @param tssBase TSS calculado por FC/Pace/RPE
     * @param treino Treino com tipo definido
     * @return TSS ajustado pelo fator de impacto
     */
    private int aplicarFatorImpactoTreino(int tssBase, TreinoRealizado treino) {
        if (treino.getTipoTreino() == null) {
            log.debug("Treino {} sem tipo definido. Usando TSS base: {}", treino.getId(), tssBase);
            return tssBase;
        }

        double fator = treino.getTipoTreino().getFatorImpacto();
        int tssAjustado = (int) Math.round(tssBase * fator);

        log.debug("TSS ajustado para treino {}: {} (base) × {} ({}) = {}",
                treino.getId(), tssBase, fator,
                treino.getTipoTreino().getLabel(), tssAjustado);

        return tssAjustado;
    }

    /**
     * TSS baseado em Frequência Cardíaca
     * Método mais preciso quando disponível
     */
    private int calcularTssFrequenciaCardiaca(TreinoRealizado treino) {
        Atleta atleta = treino.getAtleta();

        // Validar dados necessários
        if (atleta.getFcMaxima() == null || atleta.getFcRepouso() == null) {
            log.warn("Atleta {} sem FC máxima/repouso configurada", atleta.getId());
            return calcularTssRpe(treino);
        }

        Integer fcMax = atleta.getFcMaxima();
        Integer fcRepouso = atleta.getFcRepouso();
        Integer fcLimiar = atleta.getFcLimiar() != null
                ? atleta.getFcLimiar()
                : (int) (fcRepouso + (fcMax - fcRepouso) * 0.85); // Estimativa 85%

        double fcMedia = treino.getFcMedia();
        double duracaoHoras = treino.getDuracaoMin() != null
            ? treino.getDuracaoMin().toMinutes() / 60.0
            : 0.0;

        // Calcular HR Reserve %
        double hrReserve = fcMax - fcRepouso;
        double workingHR = fcMedia - fcRepouso;
        double hrReservePercent = workingHR / hrReserve;

        // Calcular Intensity Factor
        double thresholdPercent = (fcLimiar - fcRepouso) / (double) hrReserve;
        double intensityFactor = hrReservePercent / thresholdPercent;

        // Limitar IF entre 0.5 e 1.5 (valores realistas)
        intensityFactor = Math.max(0.5, Math.min(1.5, intensityFactor));

        // TSS = duração_horas × IF × 100 × IF
        double tss = duracaoHoras * intensityFactor * 100 * intensityFactor;

        return (int) Math.round(tss);
    }

    /**
     * TSS baseado em Pace (velocidade)
     * Útil quando não há dados de FC
     */
    private int calcularTssPace(TreinoRealizado treino) {
        Atleta atleta = treino.getAtleta();

        if (atleta.getPaceLimiar() == null) {
            log.warn("Atleta {} sem pace limiar configurado", atleta.getId());
            return calcularTssRpe(treino);
        }

        // Converter Duration (pace) para minutos decimais (5:30 → 5.5)
        double paceMedia = treino.getPaceMedia() != null
            ? treino.getPaceMedia().toMillis() / 60000.0 // millis → minutos
            : 0.0;

        if (paceMedia == 0) {
            log.warn("Treino {} sem pace válido", treino.getId());
            return calcularTssRpe(treino);
        }

        double paceLimiar = atleta.getPaceLimiar().doubleValue(); // min/km
        double duracaoHoras = treino.getDuracaoMin() != null
            ? treino.getDuracaoMin().toMinutes() / 60.0
            : 0.0;

        // IF = pace_limiar / pace_media (quanto menor o pace, maior o IF)
        double intensityFactor = paceLimiar / paceMedia;

        // Limitar IF entre 0.5 e 1.5
        intensityFactor = Math.max(0.5, Math.min(1.5, intensityFactor));

        // Aplicar fator de elevação (terreno)
        double fatorElevacao = calcularFatorElevacao(treino);

        // TSS = duração_horas × IF² × 100 × fator_elevação
        double tss = duracaoHoras * intensityFactor * 100 * intensityFactor * fatorElevacao;

        if (fatorElevacao > 1.0) {
            log.debug("TSS ajustado por elevação: {} (fator {})", tss, fatorElevacao);
        }

        return (int) Math.round(tss);
    }

    /**
     * Calcula fator de correção baseado em elevação acumulada
     *
     * <p><b>Fundamento Fisiológico:</b>
     * <ul>
     *   <li>Cada 100m de elevação ≈ 1km extra de corrida plana (custo energético)</li>
     *   <li>Gradiente médio determina o aumento de TSS</li>
     *   <li>Baseado em estudos de Minetti et al. (2002) - Journal of Applied Physiology</li>
     * </ul>
     *
     * <p><b>Fórmula aplicada:</b>
     * <pre>
     * Gradiente (m/km) | Aumento TSS
     * -----------------|--------------
     * 0-20 m/km        | +0.5% por m/km (subidas suaves)
     * 20-50 m/km       | +1.0% por m/km (subidas moderadas)
     * >50 m/km         | +1.5% por m/km (subidas íngremes)
     * </pre>
     *
     * @param treino Treino com dados de elevação e distância
     * @return Fator multiplicador (1.0 = plano, 1.5 = montanha pesada)
     */
    private double calcularFatorElevacao(TreinoRealizado treino) {
        Integer elevacaoMetros = treino.getElevacaoGanhoMetros();
        BigDecimal distanciaKm = treino.getDistanciaKm();

        // Se não tem dados de elevação ou distância, assume plano
        if (elevacaoMetros == null || elevacaoMetros <= 0 ||
                distanciaKm == null || distanciaKm.doubleValue() <= 0) {
            return 1.0;
        }

        double distancia = distanciaKm.doubleValue();
        double gradienteMedio = elevacaoMetros / distancia; // m/km

        // Fórmula baseada em custo energético de subidas
        // Fonte: Minetti, A. E., Moia, C., Roi, G. S., Susta, D., & Ferretti, G. (2002)
        // "Energy cost of walking and running at extreme gradients on a treadmill"
        double fator;

        if (gradienteMedio < 20) {
            // Subidas suaves: +0.5% por m/km
            fator = 1.0 + (gradienteMedio * 0.005);
        } else if (gradienteMedio < 50) {
            // Subidas moderadas: progressão para +1.0% por m/km adicional
            fator = 1.0 + (20 * 0.005) + ((gradienteMedio - 20) * 0.01);
        } else {
            // Subidas íngremes: +1.5% por m/km adicional
            fator = 1.0 + (20 * 0.005) + (30 * 0.01) + ((gradienteMedio - 50) * 0.015);
        }

        // Limitar fator entre 1.0 e 2.0 (evitar valores absurdos)
        fator = Math.min(fator, 2.0);

        log.debug("Fator elevação: {} ({}m D+ em {}km = {} m/km)",
                String.format("%.2f", fator), elevacaoMetros, distancia,
                String.format("%.1f", gradienteMedio));

        return fator;
    }

    /**
     * TSS baseado em RPE (Rating of Perceived Exertion)
     * Método menos preciso, usado como fallback
     */
    private int calcularTssRpe(TreinoRealizado treino) {
        if (treino.getPercepcaoEsforco() == null) {
            log.warn("Treino {} sem dados para calcular TSS", treino.getId());
            return 0;
        }

        double duracaoHoras = treino.getDuracaoMin() != null
            ? treino.getDuracaoMin().toMinutes() / 60.0
            : 0.0;
        double rpe = treino.getPercepcaoEsforco(); // Escala 1-10

        // Converter RPE para IF aproximado
        // RPE 6 = IF 0.6, RPE 10 = IF 1.0
        double intensityFactor = (rpe / 10.0) * 0.9 + 0.1;

        double tss = duracaoHoras * intensityFactor * 100 * intensityFactor;

        return (int) Math.round(tss);
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

        planoMetaDadosRepository.save(metaDados);
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

    /**
     * Calcula métricas semanais médias baseadas no histórico de treinos realizados.
     *
     * <p>Este método agrega dados das últimas N semanas para fornecer uma visão
     * estatística do padrão de treino do atleta, usada para:
     * <ul>
     *   <li>Calcular progressão de carga (Ramp Rate)</li>
     *   <li>Determinar TSS Alvo para próxima semana</li>
     *   <li>Identificar variações no volume de treino</li>
     * </ul>
     *
     * <p><b>Dados calculados:</b>
     * <ul>
     *   <li>Volume semanal médio (km)</li>
     *   <li>TSS semanal médio (pontos)</li>
     *   <li>Frequência média de treinos por semana</li>
     * </ul>
     *
     * @param atletaId ID do atleta
     * @param numSemanas Número de semanas para calcular médias (recomendado: 4-6)
     * @return {@link com.menthoros.dto.output.MetricasSemanaisMedias} com médias calculadas
     * @throws IllegalArgumentException se atletaId for nulo ou atleta não existir
     */
    @Override
    public com.menthoros.dto.output.MetricasSemanaisMedias calcularMetricasSemanais(UUID atletaId, int numSemanas) {
        validarAtletaExiste(atletaId);

        if (numSemanas < 1) {
            log.warn("numSemanas inválido ({}), usando padrão de 4 semanas", numSemanas);
            numSemanas = 4;
        }

        log.debug("Calculando métricas semanais médias para atleta {} (últimas {} semanas)",
            atletaId, numSemanas);

        // Buscar métricas diárias das últimas N semanas
        LocalDate dataLimite = LocalDate.now().minusWeeks(numSemanas);
        List<MetricasDiarias> metricas = metricasDiariasRepository
            .findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(atletaId, dataLimite);

        if (metricas.isEmpty()) {
            log.warn("Nenhuma métrica encontrada para atleta {} nas últimas {} semanas",
                atletaId, numSemanas);
            return new com.menthoros.dto.output.MetricasSemanaisMedias(
                BigDecimal.ZERO,
                0,
                0.0
            );
        }

        // Agrupar métricas por semana
        java.util.Map<Integer, List<MetricasDiarias>> metricasPorSemana = metricas.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                m -> m.getData().get(java.time.temporal.ChronoField.ALIGNED_WEEK_OF_YEAR)
            ));

        // Calcular totais por semana
        List<BigDecimal> volumesPorSemana = new java.util.ArrayList<>();
        List<Integer> tssPorSemana = new java.util.ArrayList<>();
        List<Integer> treinosPorSemana = new java.util.ArrayList<>();

        for (List<MetricasDiarias> metricasSemana : metricasPorSemana.values()) {
            // Volume total da semana
            BigDecimal volumeSemanal = metricasSemana.stream()
                .map(m -> m.getVolumeKm() != null ? m.getVolumeKm() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            volumesPorSemana.add(volumeSemanal);

            // TSS total da semana
            Integer tssSemanal = metricasSemana.stream()
                .mapToInt(m -> m.getTss() != null ? m.getTss() : 0)
                .sum();
            tssPorSemana.add(tssSemanal);

            // Treinos realizados na semana (dias com treinosRealizados > 0)
            Integer treinosSemana = (int) metricasSemana.stream()
                .filter(m -> m.getTreinosRealizados() != null && m.getTreinosRealizados() > 0)
                .count();
            treinosPorSemana.add(treinosSemana);
        }

        // Calcular médias
        int semanas = volumesPorSemana.size();

        BigDecimal volumeMedio = volumesPorSemana.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(semanas), 2, java.math.RoundingMode.HALF_UP);

        Integer tssMedio = tssPorSemana.stream()
            .mapToInt(Integer::intValue)
            .sum() / semanas;

        Double treinosPorSemanaMedio = treinosPorSemana.stream()
            .mapToDouble(Integer::doubleValue)
            .average()
            .orElse(0.0);

        log.info("Métricas semanais calculadas - Volume: {}km/semana, TSS: {}/semana, Treinos: {}/semana",
            volumeMedio, tssMedio, String.format("%.1f", treinosPorSemanaMedio));

        return new com.menthoros.dto.output.MetricasSemanaisMedias(
            volumeMedio,
            tssMedio,
            Math.round(treinosPorSemanaMedio * 10.0) / 10.0 // Arredondar para 1 casa decimal
        );
    }

    /**
     * Calcula padrões de treino baseados em dias consecutivos e recuperação.
     *
     * <p>Este método analisa o histórico recente de treinos para identificar:
     * <ul>
     *   <li><b>Dias consecutivos treinando:</b> Sequência contínua de dias com treino até hoje</li>
     *   <li><b>Dias desde último descanso:</b> Dias passados desde o último dia SEM treino</li>
     * </ul>
     *
     * <p><b>Fundamento Fisiológico:</b>
     * <ul>
     *   <li>Treinar mais de 5-6 dias consecutivos aumenta risco de overtraining</li>
     *   <li>Dias de descanso permitem adaptação e regeneração muscular</li>
     *   <li>Atletas iniciantes necessitam mais descanso (máx 4-5 dias consecutivos)</li>
     *   <li>Atletas avançados podem treinar 6-7 dias se volume/intensidade forem controlados</li>
     * </ul>
     *
     * <p><b>Uso prático:</b>
     * Este método é essencial para o {@link com.menthoros.services.PlanoService} decidir se deve
     * incluir dia de descanso obrigatório no plano semanal.
     *
     * @param atletaId ID do atleta
     * @return {@link com.menthoros.dto.output.PadroesTreino} com padrões identificados
     * @throws IllegalArgumentException se atletaId for nulo ou atleta não existir
     */
    @Override
    public PadroesTreino calcularPadroesTreino(UUID atletaId) {
        validarAtletaExiste(atletaId);

        log.debug("Calculando padrões de treino para atleta {}", atletaId);

        // Buscar últimos 14 dias de métricas
        LocalDate dataLimite = LocalDate.now().minusDays(14);
        List<MetricasDiarias> metricas = metricasDiariasRepository
            .findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(atletaId, dataLimite);

        if (metricas.isEmpty()) {
            log.warn("Nenhuma métrica encontrada para atleta {} nos últimos 14 dias", atletaId);
            List<TreinoRealizado> treinosRealizados = treinoRealizadoRepository.findTreinoRealizadosByAtletaId(atletaId);
            var diasConsecutivos = contarDiasConsecutivos(treinosRealizados);
            var diasDescanso = contarDiasDeDescansoNoPeriodo(treinosRealizados);

            return new PadroesTreino(diasConsecutivos, diasDescanso);
        }

        // Ordenar do mais recente para o mais antigo
        List<MetricasDiarias> metricasDesc = new java.util.ArrayList<>(metricas);
        metricasDesc.sort((m1, m2) -> m2.getData().compareTo(m1.getData()));

        // Calcular dias consecutivos (sequência de hoje para trás)
        int diasConsecutivos = 0;
        LocalDate dataEsperada = LocalDate.now();

        for (MetricasDiarias metrica : metricasDesc) {
            // Se a data não é a esperada, quebra a sequência
            if (!metrica.getData().equals(dataEsperada)) {
                break;
            }

            // Se houve treino neste dia, incrementa
            if (metrica.getTreinosRealizados() != null && metrica.getTreinosRealizados() > 0) {
                diasConsecutivos++;
                dataEsperada = dataEsperada.minusDays(1);
            } else {
                // Encontrou dia sem treino, para a contagem
                break;
            }
        }

        // Calcular dias desde último descanso
        int diasDesdeDescanso = 0;
        for (MetricasDiarias metrica : metricasDesc) {
            // Se encontrou dia sem treino, para
            if (metrica.getTreinosRealizados() == null || metrica.getTreinosRealizados() == 0) {
                break;
            }
            diasDesdeDescanso++;
        }

        log.info("Padrões de treino calculados - Dias consecutivos: {}, Dias desde descanso: {}",
            diasConsecutivos, diasDesdeDescanso);

        return new com.menthoros.dto.output.PadroesTreino(
            diasConsecutivos,
            diasDesdeDescanso
        );
    }

    public static int contarDiasConsecutivos(List<TreinoRealizado> treinoRealizadosByAtletaId) {

        List<LocalDate> dateList = treinoRealizadosByAtletaId.stream()
                .map(TreinoRealizado::getDataTreino)
                .distinct()
                .sorted()
                .toList();

        if(dateList.isEmpty()){
            return 0;
        }

        int maiorSequencia = 1;
        int sequenciaAtual = 1;

        for (int i = 0; i < dateList.size(); i++) {
            if(dateList.get(i).equals(dateList.get(i -1).plusDays(1))){
                sequenciaAtual++;
                maiorSequencia = Math.max(maiorSequencia, sequenciaAtual);
            }else {
                sequenciaAtual = 1;
            }
        }
        return maiorSequencia;
    }

    public static int contarDiasDeDescansoNoPeriodo(List<TreinoRealizado> treinos) {
        var datas = treinos.stream()
                .map(TreinoRealizado::getDataTreino)
                .distinct()
                .sorted()
                .toList();

        if (datas.isEmpty()) return 0;

        LocalDate inicio = datas.get(0);
        LocalDate fim = datas.get(datas.size() - 1);

        int totalDiasNoIntervalo = (int) (ChronoUnit.DAYS.between(inicio, fim) + 1); // inclusivo
        int diasComTreino = datas.size();

        return totalDiasNoIntervalo - diasComTreino;
    }

}

