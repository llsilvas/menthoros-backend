package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import br.com.menthoros.backend.services.helper.AthleteThresholdUpdater;
import br.com.menthoros.backend.services.helper.PaceLimiarResolvido;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fase transacional da atualização diária de TSB/CTL/ATL/limiares — extraída de
 * {@code TsbServiceImpl} (refactor-threshold-call-outside-transaction, design.md D2) pra que a
 * resolução de fonte de pace (`AthleteThresholdUpdater.resolverFontePace`) possa rodar **fora**
 * desta transação, num bean diferente, evitando o auto-invocação que tiraria `@Transactional` sem
 * efeito se ficasse no mesmo método de {@code TsbServiceImpl}.
 *
 * <p>Bean novo, mesmo padrão de {@link AthleteThresholdUpdater} (já extraído de
 * {@code TsbServiceImpl} em {@code refactor-threshold-orchestration}). Sem dependência de volta
 * pra {@code TsbServiceImpl} — só o inverso.
 *
 * <p>Idempotent: YES (por dia) — recalcular o mesmo dia produz o mesmo resultado.
 * <p>Side Effects: Database insert/update da métrica do dia e do {@code PlanoMetaDados}.
 * <p>Tenant-aware: NO — resolvido via `atleta.getAssessoria()`.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TsbDiaPersister {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final PlanoMetadadosRepository planoMetaDadosRepository;
    private final MetricasDiariasRepository metricasDiariasRepository;
    private final AtletaRepository atletaRepository;
    private final MetricasAlertaService metricasAlertaService;
    private final AthleteThresholdUpdater athleteThresholdUpdater;
    private final PlanoMetadadosService planoMetadadosService;

    private static final int CTL_TIME_CONSTANT = 42;
    private static final int ATL_TIME_CONSTANT = 7;

    /**
     * Atualiza o TSB de um único dia, em transação própria. `paceResolvido` já vem decidido de
     * fora da transação (design.md D2) — `null` quando o pace não estava desatualizado ou nenhuma
     * fonte foi encontrada, equivalente a "nenhuma mudança" em
     * {@link AthleteThresholdUpdater#aplicarPaceLimiar}.
     *
     * Idempotent: YES — recalcular o mesmo dia produz o mesmo resultado.
     * Side Effects: Database insert/update da métrica do dia e do PlanoMetaDados.
     * Tenant-aware: NO
     */
    @Transactional
    public void atualizarDiaTransacional(UUID atletaId, LocalDate data, boolean atualizarMetaDadosHoje,
                                          PaceLimiarResolvido paceResolvido) {
        validarEntrada(atletaId, data);

        log.info("Atualizando TSB para atleta {} no dia {}", atletaId, data);

        Atleta atleta = buscarAtleta(atletaId);
        List<TreinoRealizado> treinosDoDia = buscarTreinosDia(atletaId, data);
        Integer tssHoje = somarTssContabilizado(treinosDoDia);

        MetricasDiarias metricasHoje = obterOuCriarMetricasDia(atleta, data);
        atualizarVolumeDiario(metricasHoje, treinosDoDia);

        MetricasDiarias metricasOntem = buscarMetricasDiaAnterior(atletaId, data);
        calcularEAtualizarMetricas(metricasHoje, metricasOntem, tssHoje, atletaId, data);

        metricasDiariasRepository.save(metricasHoje);
        if (atualizarMetaDadosHoje) {
            atualizarMetaDados(atletaId, metricasHoje, paceResolvido);
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

    /**
     * Soma {@code tssCalculado} dos treinos do dia — D3: o campo persistido é a única verdade,
     * não um recálculo ao vivo. {@code tssCalculado} nulo conta como 0.
     */
    int somarTssContabilizado(List<TreinoRealizado> treinos) {
        int total = 0;
        for (TreinoRealizado treino : treinos) {
            Integer tss = treino.getTssCalculado();
            total += tss != null ? tss : 0;
        }
        return total;
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

        double ctlInicio = ctlAnterior;
        double atlInicio = atlAnterior;
        double tsbInicio = ctlInicio - atlInicio;

        double ctlFim = calcularCtlCorreto(ctlAnterior, tssHoje, atleta);
        double atlFim = calcularAtlCorreto(atlAnterior, tssHoje, atleta);
        double tsbFim = ctlFim - atlFim;

        double rampRate = calcularRampRate(atletaId, data, ctlFim);

        metricasHoje.setCtlInicioDia(round(ctlInicio, 2));
        metricasHoje.setAtlInicioDia(round(atlInicio, 2));
        metricasHoje.setTsbInicioDia(round(tsbInicio, 2));

        metricasHoje.setCtlFimDia(round(ctlFim, 2));
        metricasHoje.setAtlFimDia(round(atlFim, 2));
        metricasHoje.setTsbFimDia(round(tsbFim, 2));

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
     */
    private double calcularCtlCorreto(Double ctlAnterior, Integer tss, Atleta atleta) {
        if (ctlAnterior == null) ctlAnterior = 0.0;
        if (tss == null) tss = 0;

        double tau = obterCtlTimeConstant(atleta);
        double exp = Math.exp(-1.0 / tau);

        return (tss * (1 - exp)) + (ctlAnterior * exp);
    }

    /**
     * FÓRMULA CORRETA de ATL usando média móvel exponencial
     * ATL = (TSS × (1 - e^(-1/τ))) + (ATL_anterior × e^(-1/τ))
     */
    private double calcularAtlCorreto(Double atlAnterior, Integer tss, Atleta atleta) {
        if (atlAnterior == null) atlAnterior = 0.0;
        if (tss == null) tss = 0;

        double tau = obterAtlTimeConstant(atleta);
        double exp = Math.exp(-1.0 / tau);

        return (tss * (1 - exp)) + (atlAnterior * exp);
    }

    /**
     * Calcula Ramp Rate (mudança semanal de CTL)
     */
    double calcularRampRate(UUID atletaId, LocalDate data, double ctlAtual) {
        MetricasDiarias metricasSemanaPassada = metricasDiariasRepository
                .findByAtletaIdAndData(atletaId, data.minusDays(7))
                .orElse(null);

        if (metricasSemanaPassada == null) {
            return 0.0;
        }

        return ctlAtual - metricasSemanaPassada.getCtl();
    }

    /**
     * Atualiza valores atuais no PlanoMetaDados. `paceResolvido` já veio decidido de fora da
     * transação (design.md D2) — FC continua resolvido aqui dentro
     * ({@link AthleteThresholdUpdater#atualizarFcLimiar}, D3: sem 3ª fonte de FC, sem a mesma
     * separação).
     */
    private void atualizarMetaDados(UUID atletaId, MetricasDiarias metricas, PaceLimiarResolvido paceResolvido) {
        PlanoMetaDados metaDados = planoMetadadosService.buscarOuCriarMetadados(metricas.getAtleta());

        metaDados.setCtlAtual(metricas.getCtl());
        metaDados.setAtlAtual(metricas.getAtl());
        metaDados.setRampRateAtual(metricas.getRampRate());
        metaDados.setDataUltimaAtualizacao(LocalDate.now());

        Double tsbProntidao = metricas.getTsbInicioDia();
        Double tsbPosCarga  = metricas.getTsbFimDia();
        metaDados.setTsbProntidaoAtual(tsbProntidao != null ? tsbProntidao : 0.0);
        metaDados.setTsbPosCargaAtual(tsbPosCarga   != null ? tsbPosCarga  : 0.0);
        metaDados.setTsbAtual(tsbProntidao != null ? tsbProntidao : 0.0);

        boolean hojeTemTreino = metricas.getTreinosRealizados() != null && metricas.getTreinosRealizados() > 0;
        metaDados.setDiasConsecutivosTreino(
                contarDiasConsecutivosTreino(atletaId, metricas.getData(), hojeTemTreino));

        metaDados.aplicarAnalise(metricasAlertaService.analisarMetricas(metaDados, metricas.getAtleta().getNivelExperiencia()));

        athleteThresholdUpdater.atualizarFcLimiar(metricas.getAtleta(), metaDados, LocalDate.now());
        athleteThresholdUpdater.aplicarPaceLimiar(metaDados, paceResolvido, LocalDate.now());

        planoMetaDadosRepository.save(metaDados);

        recalcularSemanasProgressao(atletaId);
    }

    /**
     * Conta dias consecutivos de treino até a data informada (inclusive). Package-private:
     * reaproveitado por {@code TsbServiceImpl.atualizarMetaDados} (caminho de
     * {@code recalcularHistoricoCompleto}, fora do escopo desta change — design.md D2b) em vez de
     * duplicado.
     */
    int contarDiasConsecutivosTreino(UUID atletaId, LocalDate data, boolean hojeTemTreino) {
        if (!hojeTemTreino) {
            return 0;
        }

        LocalDate janela = data.minusDays(14);
        List<TreinoRealizado> historico = treinoRealizadoRepository
                .findByAtletaIdAndDataTreinoBetween(atletaId, janela, data.minusDays(1));

        Set<LocalDate> diasComTreino = historico.stream()
                .map(TreinoRealizado::getDataTreino)
                .collect(Collectors.toSet());

        int consecutivos = 1;
        LocalDate dia = data.minusDays(1);
        for (int i = 0; i < 14; i++) {
            if (!diasComTreino.contains(dia)) break;
            consecutivos++;
            dia = dia.minusDays(1);
        }

        return consecutivos;
    }

    /**
     * Recalcula {@code semanasProgressaoContinua} no {@link PlanoMetaDados} com base nos
     * {@link MetricasDiarias} realizados, sem depender do volume planejado. Package-private:
     * reaproveitado por {@code TsbServiceImpl.atualizarMetaDados} (caminho de
     * {@code recalcularHistoricoCompleto} — design.md D2b) em vez de duplicado.
     */
    void recalcularSemanasProgressao(UUID atletaId) {
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

        Atleta atleta = buscarAtleta(atletaId);
        metaDados.aplicarAnalise(metricasAlertaService.analisarMetricas(metaDados, atleta.getNivelExperiencia()));

        planoMetaDadosRepository.save(metaDados);
        log.info("semanasProgressaoContinua recalculadas: {} para atleta {} (nível: {})",
                semanasConsecutivas, atletaId, atleta.getNivelExperiencia());
    }

    /**
     * Obtém constante de tempo CTL adaptativa baseada no nível de experiência.
     */
    private int obterCtlTimeConstant(Atleta atleta) {
        if (atleta == null) {
            return CTL_TIME_CONSTANT;
        }
        if (atleta.getCtlTimeConstant() != null) {
            return atleta.getCtlTimeConstant();
        }
        return switch (atleta.getNivelExperiencia()) {
            case INICIANTE -> 30;
            case INTERMEDIARIO -> 35;
            case AVANCADO -> 42;
            case ELITE -> 50;
        };
    }

    /**
     * Obtém constante de tempo ATL adaptativa baseada no nível de experiência.
     */
    private int obterAtlTimeConstant(Atleta atleta) {
        if (atleta == null) {
            return ATL_TIME_CONSTANT;
        }
        if (atleta.getAtlTimeConstant() != null) {
            return atleta.getAtlTimeConstant();
        }
        return switch (atleta.getNivelExperiencia()) {
            case INICIANTE -> 5;
            case INTERMEDIARIO -> 6;
            case AVANCADO -> 7;
            case ELITE -> 8;
        };
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
