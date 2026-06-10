package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.MetricasSemanaisMedias;
import br.com.menthoros.backend.dto.output.PadroesTreino;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.MetricasAgregadasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
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

    private static final int DIAS_ANALISE_PADROES = 14;
    private static final int SEMANAS_PADRAO = 4;

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
            log.warn("numSemanas inválido ({}), usando padrão de {} semanas", numSemanas, SEMANAS_PADRAO);
            numSemanas = SEMANAS_PADRAO;
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

        // Agrupar por semana ISO (segunda-feira da semana) — robusto a virada de ano,
        // ao contrário de ALIGNED_WEEK_OF_YEAR, que reinicia em 1º de janeiro.
        Map<LocalDate, List<MetricasDiarias>> metricasPorSemana = metricas.stream()
            .collect(Collectors.groupingBy(
                m -> m.getData().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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

        int somaTss = tssPorSemana.stream().mapToInt(Integer::intValue).sum();
        Integer tssMedio = (int) Math.round((double) somaTss / semanas);

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
     * <p>Une as datas com treino de ambas as fontes (métricas diárias e treinos
     * realizados) na janela dos últimos {@value #DIAS_ANALISE_PADROES} dias. Isso
     * evita zerar a sequência quando a métrica diária de hoje ainda não foi gerada.
     *
     * @param atletaId ID do atleta
     * @return {@link PadroesTreino} com a sequência atual e os dias desde o último descanso
     */
    @Override
    public PadroesTreino calcularPadroesTreino(UUID atletaId) {
        validarAtletaExiste(atletaId);

        log.debug("Calculando padrões de treino para atleta {}", atletaId);

        LocalDate hoje = LocalDate.now();
        LocalDate inicio = hoje.minusDays(DIAS_ANALISE_PADROES - 1L);

        SortedSet<LocalDate> diasComTreino = coletarDiasComTreino(atletaId, inicio, hoje);

        int diasConsecutivos = calcularSequenciaAtual(diasComTreino);
        int diasDesdeDescanso = calcularDiasDesdeUltimoDescanso(diasComTreino, hoje);

        log.info("Padrões de treino calculados - Dias consecutivos: {}, Dias desde descanso: {}",
            diasConsecutivos, diasDesdeDescanso);

        return new PadroesTreino(diasConsecutivos, diasDesdeDescanso);
    }

    /**
     * Reúne, sem duplicatas, as datas em que houve treino na janela [inicio, hoje],
     * combinando métricas diárias (treinosRealizados &gt; 0) e treinos realizados.
     */
    private SortedSet<LocalDate> coletarDiasComTreino(UUID atletaId, LocalDate inicio, LocalDate hoje) {
        SortedSet<LocalDate> dias = new TreeSet<>();

        metricasDiariasRepository.findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(atletaId, inicio).stream()
            .filter(m -> m.getTreinosRealizados() != null && m.getTreinosRealizados() > 0)
            .map(MetricasDiarias::getData)
            .filter(d -> d != null && !d.isBefore(inicio) && !d.isAfter(hoje))
            .forEach(dias::add);

        treinoRealizadoRepository.findTreinoRealizadosByAtletaId(atletaId).stream()
            .map(TreinoRealizado::getDataTreino)
            .filter(d -> d != null && !d.isBefore(inicio) && !d.isAfter(hoje))
            .forEach(dias::add);

        return dias;
    }

    /**
     * Sequência de dias consecutivos de treino terminando no treino mais recente.
     * Robusto à ausência de dado para hoje (achado B): ancora no último treino, não em {@code now()}.
     */
    private int calcularSequenciaAtual(SortedSet<LocalDate> diasComTreino) {
        if (diasComTreino.isEmpty()) {
            return 0;
        }
        LocalDate dia = diasComTreino.last();
        int sequencia = 1;
        while (diasComTreino.contains(dia.minusDays(1))) {
            sequencia++;
            dia = dia.minusDays(1);
        }
        return sequencia;
    }

    /**
     * Dias consecutivos de treino contados a partir de hoje para trás (0 se não houve treino hoje).
     * Equivale a "dias desde o último dia de descanso".
     */
    private int calcularDiasDesdeUltimoDescanso(SortedSet<LocalDate> diasComTreino, LocalDate hoje) {
        int dias = 0;
        LocalDate dia = hoje;
        while (diasComTreino.contains(dia)) {
            dias++;
            dia = dia.minusDays(1);
        }
        return dias;
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
