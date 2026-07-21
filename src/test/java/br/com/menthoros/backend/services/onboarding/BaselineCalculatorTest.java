package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.onboarding.impl.BaselineCalculatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaselineCalculatorTest {

    @Mock
    private TsbService tsbService;

    @Mock
    private MetricasDiariasRepository metricasDiariasRepository;

    private BaselineCalculatorImpl calculator;

    private UUID atletaId;

    @BeforeEach
    void setUp() {
        atletaId = UUID.randomUUID();
        calculator = new BaselineCalculatorImpl(tsbService, metricasDiariasRepository);
    }

    @Nested
    @DisplayName("calcular")
    class Calcular {

        @Test
        @DisplayName("Cenario A — 8+ semanas de historico real: baseline direto, MEASURED")
        void cenarioA_HistoricoCompleto() {
            List<NormalizedActivity> historico = historicoDeSemanas(10); // >= 8 semanas
            MetricasDiarias metricas = metricasReais(50.0, 45.0);
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId)).thenReturn(Optional.of(metricas));

            BaselineResult resultado = calculator.calcular(atletaId, NivelExperiencia.INTERMEDIARIO, historico);

            assertThat(resultado.ctl()).isEqualTo(50.0);
            assertThat(resultado.atl()).isEqualTo(45.0);
            assertThat(resultado.tsb()).isEqualTo(5.0);
            assertThat(resultado.ctlOrigem()).isEqualTo(OrigemDado.MEASURED);
            assertThat(resultado.atlOrigem()).isEqualTo(OrigemDado.MEASURED);
            verify(tsbService).recalcularHistoricoCompleto(atletaId);
        }

        @Test
        @DisplayName("Cenario C — sem historico: 100% heuristica por nivelExperiencia, ESTIMATED")
        void cenarioC_SemHistorico() {
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId)).thenReturn(Optional.empty());

            BaselineResult resultado = calculator.calcular(atletaId, NivelExperiencia.INICIANTE, List.of());

            assertThat(resultado.ctlOrigem()).isEqualTo(OrigemDado.ESTIMATED);
            assertThat(resultado.atlOrigem()).isEqualTo(OrigemDado.ESTIMATED);
            assertThat(resultado.ctl()).isGreaterThan(0); // heuristica de INICIANTE, nao zero
        }

        @Test
        @DisplayName("Cenario C — heuristica escala com nivelExperiencia (ELITE > INICIANTE)")
        void cenarioC_HeuristicaEscalaComNivel() {
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId)).thenReturn(Optional.empty());

            BaselineResult iniciante = calculator.calcular(atletaId, NivelExperiencia.INICIANTE, List.of());
            BaselineResult elite = calculator.calcular(atletaId, NivelExperiencia.ELITE, List.of());

            assertThat(elite.ctl()).isGreaterThan(iniciante.ctl());
        }

        @Test
        @DisplayName("Cenario B — historico parcial (4 semanas): blend real+heuristica, ESTIMATED")
        void cenarioB_HistoricoParcial() {
            List<NormalizedActivity> historico = historicoDeSemanas(4); // meio do caminho entre 0 e 8
            MetricasDiarias metricas = metricasReais(20.0, 18.0); // CTL real baixo, EWMA ainda nao convergiu
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId)).thenReturn(Optional.of(metricas));

            BaselineResult resultado = calculator.calcular(atletaId, NivelExperiencia.AVANCADO, historico);

            assertThat(resultado.ctlOrigem()).isEqualTo(OrigemDado.ESTIMATED);
            // blend: nem puramente 20 (real) nem puramente a heuristica de AVANCADO
            assertThat(resultado.ctl()).isNotEqualTo(20.0);
            assertThat(resultado.ctl()).isGreaterThan(20.0); // heuristica de AVANCADO e maior, blend puxa pra cima
        }

        @Test
        @DisplayName("tsb e sempre ctl - atl, mesmo apos o blend")
        void tsbEhSempreCtlMenosAtl() {
            List<NormalizedActivity> historico = historicoDeSemanas(4);
            MetricasDiarias metricas = metricasReais(30.0, 35.0);
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId)).thenReturn(Optional.of(metricas));

            BaselineResult resultado = calculator.calcular(atletaId, NivelExperiencia.INTERMEDIARIO, historico);

            assertThat(resultado.tsb()).isEqualTo(resultado.ctl() - resultado.atl());
        }

        @Test
        @DisplayName("lanca IllegalArgumentException quando atletaId e null")
        void lancaExcecaoQuandoAtletaIdNulo() {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> calculator.calcular(null, NivelExperiencia.INICIANTE, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private List<NormalizedActivity> historicoDeSemanas(int semanas) {
        LocalDate dataMaisAntiga = LocalDate.now().minusWeeks(semanas);
        return List.of(new NormalizedActivity(
                UUID.randomUUID(), "a1", UUID.randomUUID(), dataMaisAntiga, Sport.RUNNING,
                45, 10.0, 150, 170, Duration.ofSeconds(270), null, null, FonteDados.GARMIN, 0.9
        ));
    }

    private MetricasDiarias metricasReais(double ctl, double atl) {
        MetricasDiarias metricas = new MetricasDiarias();
        metricas.setCtl(ctl);
        metricas.setAtl(atl);
        return metricas;
    }
}
