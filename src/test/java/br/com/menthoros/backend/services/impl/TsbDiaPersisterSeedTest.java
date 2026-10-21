package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Seed defensivo (fix-tsb-rest-days-materialization, D3/R6): o seed é a última métrica anterior,
 * decaída pelos dias sem linha — e só quando nenhum treino contabilizável ficou sem registro.
 * Atleta AVANCADO: tau CTL 42, tau ATL 7.
 */
@ExtendWith(MockitoExtension.class)
class TsbDiaPersisterSeedTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 20);
    private static final double DECAI_CTL = Math.exp(-1.0 / 42);
    private static final double DECAI_ATL = Math.exp(-1.0 / 7);

    @Mock private TreinoRealizadoRepository treinoRepo;
    @Mock private MetricasDiariasRepository metricasRepo;
    @Mock private AtletaRepository atletaRepo;

    private TsbDiaPersister persister;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());
        atleta = Atleta.builder()
                .id(UUID.randomUUID())
                .nome("Atleta Seed")
                .objetivo("Teste")
                .nivelExperiencia(NivelExperiencia.AVANCADO)
                .assessoria(assessoria)
                .build();

        persister = new TsbDiaPersister(treinoRepo, null, metricasRepo, atletaRepo, null, null, null);

        when(atletaRepo.findById(atleta.getId())).thenReturn(Optional.of(atleta));
        when(treinoRepo.findQueContamByAtletaIdAndDataTreino(atleta.getId(), DIA)).thenReturn(List.of());
    }

    @Nested
    @DisplayName("atualizarDiaTransacional - seed")
    class Seed {

        @Test
        @DisplayName("k = 1: usa a métrica de ontem sem decair e sem consultar treino")
        void umDiaDeDistancia() {
            anterior(DIA.minusDays(1), 40.0, 30.0);

            MetricasDiarias salva = executar();

            assertThat(salva.getCtlInicioDia()).isEqualTo(40.0);
            assertThat(salva.getAtlInicioDia()).isEqualTo(30.0);
            verify(treinoRepo, never()).existsQueContamEntre(any(), any(), any());
        }

        @Test
        @DisplayName("k = 3 sem treino no intervalo: decai 2 vezes com TSS 0")
        void tresDiasSemTreino() {
            anterior(DIA.minusDays(3), 40.0, 30.0);
            when(treinoRepo.existsQueContamEntre(atleta.getId(), DIA.minusDays(3), DIA)).thenReturn(false);

            MetricasDiarias salva = executar();

            assertThat(salva.getCtlInicioDia()).isCloseTo(40.0 * DECAI_CTL * DECAI_CTL, within(0.01));
            assertThat(salva.getAtlInicioDia()).isCloseTo(30.0 * DECAI_ATL * DECAI_ATL, within(0.01));
        }

        @Test
        @DisplayName("k = 3 com treino no intervalo: seed zero, sem decair")
        void tresDiasComTreino() {
            anterior(DIA.minusDays(3), 40.0, 30.0);
            when(treinoRepo.existsQueContamEntre(atleta.getId(), DIA.minusDays(3), DIA)).thenReturn(true);

            MetricasDiarias salva = executar();

            assertThat(salva.getCtlInicioDia()).isZero();
            assertThat(salva.getAtlInicioDia()).isZero();
        }

        @Test
        @DisplayName("sem linha anterior: seed zero, sem consultar treino")
        void semLinhaAnterior() {
            semAnterior();

            MetricasDiarias salva = executar();

            assertThat(salva.getCtlInicioDia()).isZero();
            assertThat(salva.getAtlInicioDia()).isZero();
            verify(treinoRepo, never()).existsQueContamEntre(any(), any(), any());
        }
    }

    private void semAnterior() {
        when(metricasRepo.findTopByAtletaIdAndDataBeforeOrderByDataDesc(atleta.getId(), DIA))
                .thenReturn(Optional.empty());
    }

    private void anterior(LocalDate data, double ctl, double atl) {
        MetricasDiarias m = new MetricasDiarias();
        m.setData(data);
        m.setCtl(ctl);
        m.setAtl(atl);
        when(metricasRepo.findTopByAtletaIdAndDataBeforeOrderByDataDesc(atleta.getId(), DIA))
                .thenReturn(Optional.of(m));
    }

    private MetricasDiarias executar() {
        persister.atualizarDiaTransacional(atleta.getId(), DIA, false, null);
        ArgumentCaptor<MetricasDiarias> captor = ArgumentCaptor.forClass(MetricasDiarias.class);
        verify(metricasRepo).save(captor.capture());
        return captor.getValue();
    }
}
