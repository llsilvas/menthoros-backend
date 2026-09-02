package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.domain.planner.AthleteBaseline;
import br.com.menthoros.backend.domain.planner.AthleteConstraints;
import br.com.menthoros.backend.domain.planner.OnboardingContext;
import br.com.menthoros.backend.domain.planner.PlanningPolicy;
import br.com.menthoros.backend.domain.planner.ReviewMode;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.dto.input.AtletaOnboardingInputDto;
import br.com.menthoros.backend.dto.output.AtletaOnboardingOutputDto;
import br.com.menthoros.backend.dto.output.CalibracaoStatusOutputDto;
import br.com.menthoros.backend.dto.output.OnboardingConclusaoOutputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.CanalIntegracao;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DispositivoMarca;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.services.onboarding.CalibrationStage;
import br.com.menthoros.backend.services.onboarding.CalibrationStatusResult;
import br.com.menthoros.backend.services.onboarding.ConfidenceTier;
import br.com.menthoros.backend.services.onboarding.OnboardingConclusionResult;
import br.com.menthoros.backend.services.onboarding.OnboardingDraftInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingMapperTest {

    @Mock private ProvaMapper provaMapper;

    private OnboardingMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OnboardingMapper(provaMapper);
    }

    @Nested
    @DisplayName("toDraftInput")
    class ToDraftInput {

        @Test
        @DisplayName("mapeia todos os campos do DTO para o record de dominio")
        void mapeiaCamposCompletos() {
            AtletaOnboardingInputDto dto = new AtletaOnboardingInputDto(
                    "Correr uma maratona", NivelExperiencia.INTERMEDIARIO,
                    List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA), 40, false, null, null, null,
                    null, 60, null, "CORRIDA", "BOA",
                    CanalIntegracao.INTERVALS_ICU, DispositivoMarca.GARMIN, "Forerunner 265");

            OnboardingDraftInput resultado = mapper.toDraftInput(dto, true);

            assertThat(resultado.objetivo()).isEqualTo("Correr uma maratona");
            assertThat(resultado.nivelExperiencia()).isEqualTo(NivelExperiencia.INTERMEDIARIO);
            assertThat(resultado.canalIntegracao()).isEqualTo(CanalIntegracao.INTERVALS_ICU);
            assertThat(resultado.dispositivoMarca()).isEqualTo(DispositivoMarca.GARMIN);
            assertThat(resultado.preenchidoPorCoach()).isTrue();
        }

        @Test
        @DisplayName("lanca IllegalArgumentException quando dto e null")
        void rejeitaDtoNull() {
            assertThatThrownBy(() -> mapper.toDraftInput(null, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("toOutputDto")
    class ToOutputDto {

        @Test
        @DisplayName("mapeia entidade para o DTO de saida")
        void mapeiaEntidade() {
            PerfilOnboardingAtleta entity = new PerfilOnboardingAtleta();
            entity.setId(UUID.randomUUID());
            entity.setStatus("RASCUNHO");
            entity.setObjetivo("Correr uma maratona");
            entity.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
            entity.setCanalIntegracao(CanalIntegracao.MANUAL);
            entity.setDispositivoMarca(DispositivoMarca.COROS);

            AtletaOnboardingOutputDto resultado = mapper.toOutputDto(entity);

            assertThat(resultado.status()).isEqualTo("RASCUNHO");
            assertThat(resultado.objetivo()).isEqualTo("Correr uma maratona");
            assertThat(resultado.canalIntegracao()).isEqualTo(CanalIntegracao.MANUAL);
            assertThat(resultado.dispositivoMarca()).isEqualTo(DispositivoMarca.COROS);
        }

        @Test
        @DisplayName("lanca IllegalArgumentException quando entity e null")
        void rejeitaEntityNull() {
            assertThatThrownBy(() -> mapper.toOutputDto(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("toConclusaoOutputDto")
    class ToConclusaoOutputDto {

        @Test
        @DisplayName("combina perfil, prova e contexto no DTO de conclusao")
        void combinaTudo() {
            PerfilOnboardingAtleta perfil = new PerfilOnboardingAtleta();
            perfil.setStatus("COMPLETO");
            Prova prova = Prova.builder().id(UUID.randomUUID()).provaAlvo(true).build();
            ProvaOutputDto provaDto = new ProvaOutputDto(
                    prova.getId(), "Meia SP", LocalDate.now().plusMonths(3), null, null, null,
                    true, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, false, null);
            when(provaMapper.toOutputDto(prova)).thenReturn(provaDto);
            OnboardingContext context = new OnboardingContext(
                    new AthleteBaseline(50.0, LocalDate.now()), 0.8,
                    new PlanningPolicy(ReviewMode.EXCEPTION_ONLY, 1.0, true),
                    new AthleteConstraints(List.of(), null, null, List.of()));
            OnboardingConclusionResult result = new OnboardingConclusionResult(perfil, prova, context, ConfidenceTier.A);

            OnboardingConclusaoOutputDto resultado = mapper.toConclusaoOutputDto(result);

            assertThat(resultado.status()).isEqualTo("COMPLETO");
            assertThat(resultado.provaAlvo()).isEqualTo(provaDto);
            assertThat(resultado.ctlEstimado()).isEqualTo(50.0);
            assertThat(resultado.confidenceScore()).isEqualTo(0.8);
            assertThat(resultado.confidenceTier()).isEqualTo(ConfidenceTier.A);
        }

        @Test
        @DisplayName("lanca IllegalArgumentException quando result e null")
        void rejeitaResultNull() {
            assertThatThrownBy(() -> mapper.toConclusaoOutputDto(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("toCalibracaoStatusOutputDto")
    class ToCalibracaoStatusOutputDto {

        @Test
        @DisplayName("mapeia CalibrationStatusResult para o DTO de saida")
        void mapeiaStatus() {
            CalibrationStatusResult result = new CalibrationStatusResult(
                    TrainingPhase.CALIBRATION, CalibrationStage.CALIBRATION, 2, 40);

            CalibracaoStatusOutputDto resultado = mapper.toCalibracaoStatusOutputDto(result);

            assertThat(resultado.phase()).isEqualTo(TrainingPhase.CALIBRATION);
            assertThat(resultado.stage()).isEqualTo(CalibrationStage.CALIBRATION);
            assertThat(resultado.weekNumber()).isEqualTo(2);
            assertThat(resultado.confidenceScore()).isEqualTo(40);
        }

        @Test
        @DisplayName("lanca IllegalArgumentException quando result e null")
        void rejeitaResultNull() {
            assertThatThrownBy(() -> mapper.toCalibracaoStatusOutputDto(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
