package br.com.menthoros.backend.services.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.metadata.DefaultUsage;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvalCostCalculator")
class EvalCostCalculatorTest {

    @ParameterizedTest(name = "{0}")
    // nomes que a API devolve em metadata.model (gate 0 / bake-off): o gpt-6-luna vem sem sufixo datado
    @ValueSource(strings = {"gpt-6-luna", "gpt-5.2", "gpt-5.2-2025-12-11", "gpt-4o-2024-08-06"})
    @DisplayName("resolve o preço do nome servido pela API (CA5)")
    void resolvePrecoDoNomeServido(String modelo) {
        assertThat(EvalCostCalculator.custoUsd(modelo, new DefaultUsage(1_000_000, 0))).isPresent();
    }
}
