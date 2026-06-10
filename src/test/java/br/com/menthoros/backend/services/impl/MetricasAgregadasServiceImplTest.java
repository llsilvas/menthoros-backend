package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.MetricasSemanaisMedias;
import br.com.menthoros.backend.dto.output.PadroesTreino;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricasAgregadasServiceImplTest {

    @Mock
    private MetricasDiariasRepository metricasDiariasRepository;
    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock
    private AtletaRepository atletaRepository;

    private MetricasAgregadasServiceImpl service;

    private UUID atletaId;

    @BeforeEach
    void setUp() {
        service = new MetricasAgregadasServiceImpl(
                metricasDiariasRepository, treinoRealizadoRepository, atletaRepository);
        atletaId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("calcularMetricasSemanais")
    class CalcularMetricasSemanais {

        @Test
        @DisplayName("calcula volume, TSS e frequência médios agrupando por semana")
        void calculaMediasPorSemana() {
            stubAtletaExiste();
            // Duas semanas distintas (mesmo ano, semanas-alinhadas diferentes).
            when(metricasDiariasRepository
                    .findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(eq(atletaId), any(LocalDate.class)))
                    .thenReturn(List.of(
                            metrica(LocalDate.of(2026, 2, 2), "10.0", 50, 1),
                            metrica(LocalDate.of(2026, 2, 9), "20.0", 100, 1)));

            MetricasSemanaisMedias r = service.calcularMetricasSemanais(atletaId, 4);

            assertEquals(0, new BigDecimal("15.00").compareTo(r.volumeMedio())); // (10+20)/2
            assertEquals(75, r.tssMedio());                                       // (50+100)/2
            assertEquals(1.0, r.treinosPorSemanaMedio());                         // 1 treino/semana
        }

        @Test
        @DisplayName("trata volumeKm e tss nulos como zero")
        void trataNulosComoZero() {
            stubAtletaExiste();
            when(metricasDiariasRepository
                    .findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(eq(atletaId), any(LocalDate.class)))
                    .thenReturn(List.of(metrica(LocalDate.of(2026, 2, 2), null, null, 1)));

            MetricasSemanaisMedias r = service.calcularMetricasSemanais(atletaId, 4);

            assertEquals(0, BigDecimal.ZERO.compareTo(r.volumeMedio()));
            assertEquals(0, r.tssMedio());
        }

        @Test
        @DisplayName("retorna zeros quando não há métricas no período")
        void semMetricas() {
            stubAtletaExiste();
            when(metricasDiariasRepository
                    .findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(eq(atletaId), any(LocalDate.class)))
                    .thenReturn(List.of());

            MetricasSemanaisMedias r = service.calcularMetricasSemanais(atletaId, 4);

            assertEquals(0, BigDecimal.ZERO.compareTo(r.volumeMedio()));
            assertEquals(0, r.tssMedio());
            assertEquals(0.0, r.treinosPorSemanaMedio());
        }

        @Test
        @DisplayName("usa 4 semanas como padrão quando numSemanas é inválido")
        void numSemanasInvalidoUsaPadrao() {
            stubAtletaExiste();
            when(metricasDiariasRepository
                    .findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(eq(atletaId), any(LocalDate.class)))
                    .thenReturn(List.of(metrica(LocalDate.of(2026, 2, 2), "12.0", 60, 1)));

            MetricasSemanaisMedias r = service.calcularMetricasSemanais(atletaId, 0);

            assertEquals(0, new BigDecimal("12.00").compareTo(r.volumeMedio()));
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando atletaId é nulo")
        void atletaIdNulo() {
            assertThrows(IllegalArgumentException.class, () ->
                    service.calcularMetricasSemanais(null, 4));
            verifyNoInteractions(metricasDiariasRepository);
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando o atleta não existe")
        void atletaInexistente() {
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () ->
                    service.calcularMetricasSemanais(atletaId, 4));
            verifyNoInteractions(metricasDiariasRepository);
        }
    }

    @Nested
    @DisplayName("calcularPadroesTreino")
    class CalcularPadroesTreino {

        @Test
        @DisplayName("conta sequência consecutiva e dias desde o último descanso quando treina até hoje")
        void streakTerminandoHoje() {
            stubAtletaExiste();
            LocalDate hoje = LocalDate.now();
            when(metricasDiariasRepository
                    .findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(eq(atletaId), any(LocalDate.class)))
                    .thenReturn(List.of(
                            metrica(hoje.minusDays(2), "8.0", 40, 0),   // descanso
                            metrica(hoje.minusDays(1), "8.0", 40, 1),   // treino
                            metrica(hoje, "8.0", 40, 1)));              // treino
            when(treinoRealizadoRepository.findTreinoRealizadosByAtletaId(atletaId)).thenReturn(List.of());

            PadroesTreino r = service.calcularPadroesTreino(atletaId);

            assertEquals(2, r.diasConsecutivos());     // hoje + ontem
            assertEquals(2, r.diasDesdeDescanso());    // treino em hoje e ontem; anteontem foi descanso
        }

        @Test
        @DisplayName("achado B corrigido: mantém a sequência mesmo sem métrica de hoje")
        void robustoSemMetricaDeHoje() {
            stubAtletaExiste();
            LocalDate hoje = LocalDate.now();
            when(metricasDiariasRepository
                    .findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(eq(atletaId), any(LocalDate.class)))
                    .thenReturn(List.of(
                            metrica(hoje.minusDays(2), "8.0", 40, 1),
                            metrica(hoje.minusDays(1), "8.0", 40, 1)));
            when(treinoRealizadoRepository.findTreinoRealizadosByAtletaId(atletaId)).thenReturn(List.of());

            PadroesTreino r = service.calcularPadroesTreino(atletaId);

            assertEquals(2, r.diasConsecutivos());   // sequência ancora no último treino (ontem), não em hoje
            assertEquals(0, r.diasDesdeDescanso());  // não houve treino hoje
        }

        @Test
        @DisplayName("une datas de treino das duas fontes (métricas + treinos realizados)")
        void uneFontes() {
            stubAtletaExiste();
            LocalDate hoje = LocalDate.now();
            when(metricasDiariasRepository
                    .findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(eq(atletaId), any(LocalDate.class)))
                    .thenReturn(List.of(metrica(hoje.minusDays(2), "8.0", 40, 1)));
            when(treinoRealizadoRepository.findTreinoRealizadosByAtletaId(atletaId))
                    .thenReturn(List.of(treino(hoje.minusDays(1)), treino(hoje)));

            PadroesTreino r = service.calcularPadroesTreino(atletaId);

            assertEquals(3, r.diasConsecutivos());   // hoje, -1 (treinos) e -2 (métrica) consecutivos
            assertEquals(3, r.diasDesdeDescanso());
        }

        @Test
        @DisplayName("usa apenas treinos realizados quando não há métricas diárias")
        void apenasTreinosRealizados() {
            stubAtletaExiste();
            LocalDate hoje = LocalDate.now();
            when(metricasDiariasRepository
                    .findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(eq(atletaId), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(treinoRealizadoRepository.findTreinoRealizadosByAtletaId(atletaId))
                    .thenReturn(List.of(
                            treino(hoje.minusDays(5)),
                            treino(hoje.minusDays(4)),   // sequência de 2 (-5, -4)
                            treino(hoje.minusDays(1))));  // treino mais recente isolado

            PadroesTreino r = service.calcularPadroesTreino(atletaId);

            assertEquals(1, r.diasConsecutivos());   // sequência atual = último treino (-1) isolado
            assertEquals(0, r.diasDesdeDescanso());  // não houve treino hoje
        }

        @Test
        @DisplayName("ignora dias fora da janela e treinos sem data")
        void semTreinoNaJanela() {
            stubAtletaExiste();
            LocalDate hoje = LocalDate.now();
            when(metricasDiariasRepository
                    .findByAtletaIdAndDataGreaterThanEqualOrderByDataAsc(eq(atletaId), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(treinoRealizadoRepository.findTreinoRealizadosByAtletaId(atletaId))
                    .thenReturn(List.of(treino(hoje.minusDays(60)), treino(null)));

            PadroesTreino r = service.calcularPadroesTreino(atletaId);

            assertEquals(0, r.diasConsecutivos());
            assertEquals(0, r.diasDesdeDescanso());
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando o atleta não existe")
        void atletaInexistente() {
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () ->
                    service.calcularPadroesTreino(atletaId));
            verifyNoInteractions(metricasDiariasRepository);
        }
    }

    // --- helpers ---

    private void stubAtletaExiste() {
        when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(Atleta.builder().id(atletaId).build()));
    }

    private MetricasDiarias metrica(LocalDate data, String volumeKm, Integer tss, Integer treinosRealizados) {
        return MetricasDiarias.builder()
                .data(data)
                .volumeKm(volumeKm != null ? new BigDecimal(volumeKm) : null)
                .tss(tss)
                .treinosRealizados(treinosRealizados)
                .build();
    }

    private TreinoRealizado treino(LocalDate data) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setDataTreino(data);
        return tr;
    }
}
