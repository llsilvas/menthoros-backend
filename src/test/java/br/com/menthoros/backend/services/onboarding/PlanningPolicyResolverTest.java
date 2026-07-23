package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.PlanningPolicy;
import br.com.menthoros.backend.domain.planner.ReviewMode;
import br.com.menthoros.backend.services.onboarding.impl.PlanningPolicyResolverImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningPolicyResolverTest {

    private final PlanningPolicyResolver resolver = new PlanningPolicyResolverImpl();

    @Nested
    @DisplayName("resolver")
    class Resolver {

        @Test
        @DisplayName("tier A -> EXCEPTION_ONLY, progressao normal, explicacao obrigatoria")
        void tierA() {
            PlanningPolicy policy = resolver.resolver(ConfidenceTier.A);

            assertThat(policy.reviewMode()).isEqualTo(ReviewMode.EXCEPTION_ONLY);
            assertThat(policy.maxProgressionAllowed()).isEqualTo(1.0);
            assertThat(policy.explanationRequired()).isTrue();
        }

        @Test
        @DisplayName("tier B -> MANDATORY_NON_BLOCKING, progressao reduzida, explicacao obrigatoria")
        void tierB() {
            PlanningPolicy policy = resolver.resolver(ConfidenceTier.B);

            assertThat(policy.reviewMode()).isEqualTo(ReviewMode.MANDATORY_NON_BLOCKING);
            assertThat(policy.maxProgressionAllowed()).isBetween(0.0, 1.0).isNotEqualTo(1.0).isNotEqualTo(0.0);
            assertThat(policy.explanationRequired()).isTrue();
        }

        @Test
        @DisplayName("tier C -> MANDATORY_BLOCKING, progressao zero, explicacao obrigatoria")
        void tierC() {
            PlanningPolicy policy = resolver.resolver(ConfidenceTier.C);

            assertThat(policy.reviewMode()).isEqualTo(ReviewMode.MANDATORY_BLOCKING);
            assertThat(policy.maxProgressionAllowed()).isEqualTo(0.0);
            assertThat(policy.explanationRequired()).isTrue();
        }

        @Test
        @DisplayName("lanca IllegalArgumentException quando tier e null")
        void lancaExcecaoQuandoTierNulo() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.resolver(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
