package com.menthoros.services.impl;

import com.menthoros.dto.output.MetricasSemanaisMedias;
import com.menthoros.dto.output.PadroesTreino;
import com.menthoros.entity.MetricasDiarias;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.MetricasDiariasRepository;
import com.menthoros.repository.TreinoRealizadoRepository;
import com.menthoros.services.MetricasAgregadasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço de métricas agregadas: estatísticas semanais e padrões de treino.
 *
 * <p>Agrega dados do histórico de treinos para fornecer:
 * <ul>
 *   <li>Volume semanal médio, TSS médio, frequência de treinos</li>
 *   <li>Padrões de dias consecutivos e recuperação</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricasAgregadasServiceImpl implements MetricasAgregadasService {

    private final MetricasDiariasRepository metricasDiariasRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final AtletaRepository atletaRepository;

    /**
     * Calcula métricas semanais médias baseadas no histórico de treinos realizados.
     *
     * <p>Agrega dados das últimas N semanas para fornecer uma visão
     * estatística do padrão de treino do atleta.
     *
     * @param atletaId ID do atleta
     * @param numSemanas Número de semanas para calcular médias (recomendado: 4-6)
     * @return {@link MetricasSemanaisMedias} com médias calculadas
     */
    @Override
    public MetricasSemanaisMedias calcularMetricasSemanais(UUID atletaId, int numSemanas) {
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
            return new MetricasSemanaisMedias(BigDecimal.ZERO, 0, 0.0);
        }

        // Agrupar métricas por semana
        Map<Integer, List<MetricasDiarias>> metricasPorSemana = metricas.stream()
            .collect(Collectors.groupingBy(
                m -> m.getData().get(ChronoField.ALIGNED_WEEK_OF_YEAR)
            ));

        // Calcular totais por semana
        List<BigDecimal> volumesPorSemana = new ArrayList<>();
        List<Integer> tssPorSemana = new ArrayList<>();
        List<Integer> treinosPorSemana = new ArrayList<>();

        for (List<MetricasDiarias> metricasSemana : metricasPorSemana.values()) {
            BigDecimal volumeSemanal = metricasSemana.stream()
                .map(m -> m.getVolumeKm() != null ? m.getVolumeKm() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            volumesPorSemana.add(volumeSemanal);

            Integer tssSemanal = metricasSemana.stream()
                .mapToInt(m -> m.getTss() != null ? m.getTss() : 0)
                .sum();
            tssPorSemana.add(tssSemanal);

            Integer treinosSemana = (int) metricasSemana.stream()
                .filter(m -> m.getTreinosRealizados() != null && m.getTreinosRealizados() > 0)
                .count();
            treinosPorSemana.add(treinosSemana);
        }

        // Calcular médias
        int semanas = volumesPorSemana.size();

        BigDecimal volumeMedio = volumesPorSemana.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(semanas), 2, RoundingMode.HALF_UP);

        Integer tssMedio = tssPorSemana.stream()
            .mapToInt(Integer::intValue)
            .sum() / semanas;

        Double treinosPorSemanaMedio = treinosPorSemana.stream()
            .mapToDouble(Integer::doubleValue)
            .average()
            .orElse(0.0);

        log.info("Métricas semanais calculadas - Volume: {}km/semana, TSS: {}/semana, Treinos: {}/semana",
            volumeMedio, tssMedio, String.format("%.1f", treinosPorSemanaMedio));

        return new MetricasSemanaisMedias(
            volumeMedio,
            tssMedio,
            Math.round(treinosPorSemanaMedio * 10.0) / 10.0
        );
    }

    /**
     * Calcula padrões de treino baseados em dias consecutivos e recuperação.
     *
     * @param atletaId ID do atleta
     * @return {@link PadroesTreino} com padrões identificados
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
            List<TreinoRealizado> treinosRealizados = treinoRealizadoRepository.findTreinoRealizadosByAtletaId(atletaId).stream()
                    .filter(p -> p.dataTreino.isAfter(LocalDate.now().minusDays(14)))
                    .toList();
            var diasConsecutivos = contarDiasConsecutivos(treinosRealizados);
            var diasDescanso = contarDiasDeDescansoNoPeriodo(treinosRealizados);

            return new PadroesTreino(diasConsecutivos, diasDescanso);
        }

        // Ordenar do mais recente para o mais antigo
        List<MetricasDiarias> metricasDesc = new ArrayList<>(metricas);
        metricasDesc.sort((m1, m2) -> m2.getData().compareTo(m1.getData()));

        // Calcular dias consecutivos (sequência de hoje para trás)
        int diasConsecutivos = 0;
        LocalDate dataEsperada = LocalDate.now();

        for (MetricasDiarias metrica : metricasDesc) {
            if (!metrica.getData().equals(dataEsperada)) {
                break;
            }
            if (metrica.getTreinosRealizados() != null && metrica.getTreinosRealizados() > 0) {
                diasConsecutivos++;
                dataEsperada = dataEsperada.minusDays(1);
            } else {
                break;
            }
        }

        // Calcular dias desde último descanso
        int diasDesdeDescanso = 0;
        for (MetricasDiarias metrica : metricasDesc) {
            if (metrica.getTreinosRealizados() == null || metrica.getTreinosRealizados() == 0) {
                break;
            }
            diasDesdeDescanso++;
        }

        log.info("Padrões de treino calculados - Dias consecutivos: {}, Dias desde descanso: {}",
            diasConsecutivos, diasDesdeDescanso);

        return new PadroesTreino(diasConsecutivos, diasDesdeDescanso);
    }

    public static int contarDiasConsecutivos(List<TreinoRealizado> treinoRealizadosByAtletaId) {
        List<LocalDate> dateList = treinoRealizadosByAtletaId.stream()
                .map(TreinoRealizado::getDataTreino)
                .distinct()
                .sorted()
                .toList();

        if (dateList.isEmpty()) {
            return 0;
        }

        int maiorSequencia = 1;
        int sequenciaAtual = 1;

        for (int i = 1; i < dateList.size(); i++) {
            if (dateList.get(i).equals(dateList.get(i - 1).plusDays(1))) {
                sequenciaAtual++;
                maiorSequencia = Math.max(maiorSequencia, sequenciaAtual);
            } else {
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

        int totalDiasNoIntervalo = (int) (ChronoUnit.DAYS.between(inicio, fim) + 1);
        int diasComTreino = datas.size();

        return totalDiasNoIntervalo - diasComTreino;
    }

    private void validarAtletaExiste(UUID atletaId) {
        if (atletaId == null) {
            throw new IllegalArgumentException("atletaId não pode ser nulo");
        }
        if (!atletaRepository.findById(atletaId).isPresent()) {
            throw new IllegalArgumentException("Atleta não encontrado: " + atletaId);
        }
    }
}
