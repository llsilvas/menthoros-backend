package br.com.menthoros.backend.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test do profile Maven {@code -Peval} (plan-generation-eval-set, task 1.5) — prova que
 * {@code @Tag("eval")} é excluído por padrão (`./mvnw clean test`) e incluído só sob
 * {@code -Peval} (`./mvnw -Peval test`). O runner de verdade (carregar fixtures, rodar os graders,
 * imprimir a tabela) é implementado na task 1.9 nesta mesma classe/pacote.
 */
@Tag("eval")
@DisplayName("EvalRunner (smoke — profile -Peval)")
class EvalRunnerSmokeTest {

    @Test
    @DisplayName("roda só sob -Peval — se este teste executou, o profile incluiu a tag corretamente")
    void rodaSoSobPerfilEval() {
        assertThat(true).isTrue();
    }
}
