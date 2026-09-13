package br.com.menthoros.backend.domain.compliance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As constantes são o que aparece em query e em conversa ("a v2 rejeita menos"); o hash pega
 * mudança acidental sem bump. Espelham {@link PlannerVersion} (add-plan-generation-ledger, D9).
 */
class PromptVersionTest {

    @Test
    @DisplayName("prompt e schema têm versões independentes, no mesmo formato do planner")
    void versoesIndependentes() {
        assertThat(PromptVersion.CURRENT).isEqualTo("plano-v1");
        assertThat(SchemaVersion.CURRENT).isEqualTo("schema-v1");
        assertThat(PlannerVersion.CURRENT).isEqualTo("planner-v1");
    }
}
