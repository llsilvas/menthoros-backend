package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.InjuryRiskLevel;
import br.com.menthoros.backend.dto.output.SemanaAdesaoDto;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.events.AtletaPresoEmCalibracaoEvent;
import br.com.menthoros.backend.services.MetricasAdesaoService;
import br.com.menthoros.backend.services.onboarding.impl.CalibrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalibrationServiceTest {

    @Mock
    private BaselineCalculator baselineCalculator;

    @Mock
    private ConfidenceScorer confidenceScorer;

    @Mock
    private MetricasAdesaoService metricasAdesaoService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CalibrationServiceImpl service;

    private UUID atletaId;
    private UUID tenantId;
    private LocalDate dataReferencia;

    @BeforeEach
    void setUp() {
        service = new CalibrationServiceImpl(baselineCalculator, confidenceScorer, metricasAdesaoService, eventPublisher);
        atletaId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        dataReferencia = LocalDate.of(2026, 5, 4);
    }

    @Nested
    @DisplayName("determinarEstagio")
    class DeterminarEstagio {

        @Test
        @DisplayName("semana 1 -> OBSERVATION")
        void semana1() {
            assertThat(service.determinarEstagio(1)).isEqualTo(CalibrationStage.OBSERVATION);
        }

        @Test
        @DisplayName("semana 2 -> CALIBRATION")
        void semana2() {
            assertThat(service.determinarEstagio(2)).isEqualTo(CalibrationStage.CALIBRATION);
        }

        @Test
        @DisplayName("semanas 3 e 4 -> STABILIZATION")
        void semanas3e4() {
            assertThat(service.determinarEstagio(3)).isEqualTo(CalibrationStage.STABILIZATION);
            assertThat(service.determinarEstagio(4)).isEqualTo(CalibrationStage.STABILIZATION);
        }

        @Test
        @DisplayName("alem da semana 4 permanece STABILIZATION (preso)")
        void alemDaSemana4() {
            assertThat(service.determinarEstagio(6)).isEqualTo(CalibrationStage.STABILIZATION);
        }
    }

    @Nested
    @DisplayName("avaliarSemana")
    class AvaliarSemana {

        private final NivelExperiencia nivel = NivelExperiencia.INTERMEDIARIO;
        private final List<NormalizedActivity> historico = List.of();

        @Test
        @DisplayName("re-calcula baseline e score a cada chamada (nao usa cache)")
        void reCalculaBaselineEScore() {
            BaselineResult baseline = new BaselineResult(50, OrigemDado.MEASURED, 45, OrigemDado.MEASURED, 5, OrigemDado.MEASURED);
            ConfidenceScoreResult score = new ConfidenceScoreResult(80, ConfidenceTier.A, false);
            when(baselineCalculator.calcular(eq(atletaId), eq(nivel), eq(historico))).thenReturn(baseline);
            when(confidenceScorer.calcular(any())).thenReturn(score);
            when(metricasAdesaoService.getAdesaoSemana(anyString(), eq(dataReferencia)))
                    .thenReturn(semanaAdesao(80.0));

            CalibrationEvaluation resultado = service.avaliarSemana(
                    atletaId, tenantId, 2, dataReferencia, nivel, historico,
                    confidenceInput(historico), InjuryRiskLevel.SAFE);

            assertThat(resultado.baseline()).isEqualTo(baseline);
            assertThat(resultado.confidenceScore()).isEqualTo(score);
            verify(baselineCalculator).calcular(atletaId, nivel, historico);
            verify(confidenceScorer).calcular(any());
        }

        @Test
        @DisplayName("elegivel para sair: score>=45 E sem HIGH_RISK E percentualRealizacao>=70% na semana de referencia")
        void elegivelParaSairQuandoTodosOsCriteriosOk() {
            stubBaselineEScore(50, ConfidenceTier.B); // score bruto 50 >= 45
            when(metricasAdesaoService.getAdesaoSemana(atletaId.toString(), dataReferencia))
                    .thenReturn(semanaAdesao(75.0)); // >= 70%

            CalibrationEvaluation resultado = service.avaliarSemana(
                    atletaId, tenantId, 3, dataReferencia, nivel, historico,
                    confidenceInput(historico), InjuryRiskLevel.SAFE);

            assertThat(resultado.elegivelParaSairDaCalibracao()).isTrue();
        }

        @Test
        @DisplayName("nao elegivel quando score < 45")
        void naoElegivelQuandoScoreBaixo() {
            stubBaselineEScore(30, ConfidenceTier.C);
            when(metricasAdesaoService.getAdesaoSemana(atletaId.toString(), dataReferencia))
                    .thenReturn(semanaAdesao(90.0));

            CalibrationEvaluation resultado = service.avaliarSemana(
                    atletaId, tenantId, 3, dataReferencia, nivel, historico,
                    confidenceInput(historico), InjuryRiskLevel.SAFE);

            assertThat(resultado.elegivelParaSairDaCalibracao()).isFalse();
        }

        @Test
        @DisplayName("nao elegivel quando HIGH_RISK, mesmo com score e aderencia ok")
        void naoElegivelQuandoHighRisk() {
            stubBaselineEScore(80, ConfidenceTier.A);
            when(metricasAdesaoService.getAdesaoSemana(atletaId.toString(), dataReferencia))
                    .thenReturn(semanaAdesao(90.0));

            CalibrationEvaluation resultado = service.avaliarSemana(
                    atletaId, tenantId, 3, dataReferencia, nivel, historico,
                    confidenceInput(historico), InjuryRiskLevel.HIGH_RISK);

            assertThat(resultado.elegivelParaSairDaCalibracao()).isFalse();
        }

        @Test
        @DisplayName("nao elegivel quando percentualRealizacao < 70%, mesmo com score e risco ok")
        void naoElegivelQuandoAderenciaBaixa() {
            stubBaselineEScore(80, ConfidenceTier.A);
            when(metricasAdesaoService.getAdesaoSemana(atletaId.toString(), dataReferencia))
                    .thenReturn(semanaAdesao(50.0));

            CalibrationEvaluation resultado = service.avaliarSemana(
                    atletaId, tenantId, 3, dataReferencia, nivel, historico,
                    confidenceInput(historico), InjuryRiskLevel.SAFE);

            assertThat(resultado.elegivelParaSairDaCalibracao()).isFalse();
        }

        @Test
        @DisplayName("usa a semana de referencia informada, nao LocalDate.now()")
        void usaSemanaDeReferenciaNaoAgora() {
            stubBaselineEScore(80, ConfidenceTier.A);
            when(metricasAdesaoService.getAdesaoSemana(atletaId.toString(), dataReferencia))
                    .thenReturn(semanaAdesao(80.0));

            service.avaliarSemana(atletaId, tenantId, 3, dataReferencia, nivel, historico,
                    confidenceInput(historico), InjuryRiskLevel.SAFE);

            verify(metricasAdesaoService).getAdesaoSemana(atletaId.toString(), dataReferencia);
        }

        @Test
        @DisplayName("publica AtletaPresoEmCalibracaoEvent quando ainda em calibracao alem da semana 4")
        void publicaEventoQuandoPresoAlemDaSemana4() {
            stubBaselineEScore(30, ConfidenceTier.C); // nao elegivel para sair
            when(metricasAdesaoService.getAdesaoSemana(anyString(), eq(dataReferencia)))
                    .thenReturn(semanaAdesao(50.0));

            service.avaliarSemana(atletaId, tenantId, 5, dataReferencia, nivel, historico,
                    confidenceInput(historico), InjuryRiskLevel.SAFE);

            ArgumentCaptor<AtletaPresoEmCalibracaoEvent> captor = ArgumentCaptor.forClass(AtletaPresoEmCalibracaoEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().atletaId()).isEqualTo(atletaId);
            assertThat(captor.getValue().tenantId()).isEqualTo(tenantId);
            assertThat(captor.getValue().semanasEmCalibracao()).isEqualTo(5);
        }

        @Test
        @DisplayName("nao publica evento quando dentro das 4 semanas esperadas")
        void naoPublicaEventoDentroDoEsperado() {
            stubBaselineEScore(30, ConfidenceTier.C);
            when(metricasAdesaoService.getAdesaoSemana(anyString(), eq(dataReferencia)))
                    .thenReturn(semanaAdesao(50.0));

            service.avaliarSemana(atletaId, tenantId, 3, dataReferencia, nivel, historico,
                    confidenceInput(historico), InjuryRiskLevel.SAFE);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("nao publica evento quando ja elegivel para sair, mesmo alem da semana 4")
        void naoPublicaEventoQuandoJaElegivel() {
            stubBaselineEScore(80, ConfidenceTier.A);
            when(metricasAdesaoService.getAdesaoSemana(anyString(), eq(dataReferencia)))
                    .thenReturn(semanaAdesao(90.0));

            service.avaliarSemana(atletaId, tenantId, 6, dataReferencia, nivel, historico,
                    confidenceInput(historico), InjuryRiskLevel.SAFE);

            verify(eventPublisher, never()).publishEvent(any());
        }

        private void stubBaselineEScore(int scoreBruto, ConfidenceTier tier) {
            BaselineResult baseline = new BaselineResult(50, OrigemDado.ESTIMATED, 45, OrigemDado.ESTIMATED, 5, OrigemDado.ESTIMATED);
            ConfidenceScoreResult score = new ConfidenceScoreResult(scoreBruto, tier, false);
            when(baselineCalculator.calcular(any(), any(), any())).thenReturn(baseline);
            when(confidenceScorer.calcular(any())).thenReturn(score);
        }
    }

    private ConfidenceScorerInput confidenceInput(List<NormalizedActivity> historico) {
        return new ConfidenceScorerInput(historico, true, null, null, null, false, false, null);
    }

    private SemanaAdesaoDto semanaAdesao(double percentual) {
        return new SemanaAdesaoDto("2026-W18", "2026-05-03", "2026-05-09", 10, 8, percentual, 4);
    }
}
