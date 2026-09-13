package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.planner.ConstraintValidationResult;
import br.com.menthoros.backend.domain.planner.InjuryRiskAssessment;
import br.com.menthoros.backend.domain.planner.InjuryRiskLevel;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.domain.planner.WeeklyLoadTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkeletonPrePromptTest {

    private WeekPlanSkeleton skeleton() {
        return new WeekPlanSkeleton(
                TrainingPhase.BASE,
                new WeeklyLoadTarget(200.0, 180.0, 220.0, "teste"),
                List.of(),
                new InjuryRiskAssessment(InjuryRiskLevel.SAFE, false, null),
                new ConstraintValidationResult(true, List.of()),
                false, null, LocalDate.now(), null, Optional.empty());
    }

    @Nested
    @DisplayName("factories")
    class Factories {

        @Test
        @DisplayName("desligado: skeleton nulo, fallback false")
        void desligado() {
            SkeletonPrePrompt resultado = SkeletonPrePrompt.desligado();

            assertThat(resultado.skeleton()).isNull();
            assertThat(resultado.fallback()).isFalse();
        }

        @Test
        @DisplayName("sucesso: skeleton presente, fallback false")
        void sucesso() {
            WeekPlanSkeleton skeleton = skeleton();

            SkeletonPrePrompt resultado = SkeletonPrePrompt.sucesso(skeleton);

            assertThat(resultado.skeleton()).isSameAs(skeleton);
            assertThat(resultado.fallback()).isFalse();
        }

        @Test
        @DisplayName("viaFallback: skeleton nulo, fallback true")
        void viaFallback() {
            SkeletonPrePrompt resultado = SkeletonPrePrompt.viaFallback();

            assertThat(resultado.skeleton()).isNull();
            assertThat(resultado.fallback()).isTrue();
        }
    }

    @Nested
    @DisplayName("invariante (achado do clean-code-reviewer no /qa de 8.5.h)")
    class Invariante {

        @Test
        @DisplayName("rejeita fallback=true com skeleton presente — combinacao inconsistente")
        void rejeitaFallbackComSkeletonPresente() {
            assertThatThrownBy(() -> new SkeletonPrePrompt(skeleton(), true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fallback");
        }

        @Test
        @DisplayName("aceita skeleton nulo com fallback true ou false")
        void aceitaSkeletonNuloComQualquerFallback() {
            assertThat(new SkeletonPrePrompt(null, true).fallback()).isTrue();
            assertThat(new SkeletonPrePrompt(null, false).fallback()).isFalse();
        }
    }
}
