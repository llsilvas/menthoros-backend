package br.com.menthoros.backend.services.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvalJudgeSchemaBuilder")
class EvalJudgeSchemaBuilderTest {

    private final EvalJudgeSchemaBuilder builder = new EvalJudgeSchemaBuilder();

    @Nested
    @DisplayName("optionsCompleta")
    class OptionsCompleta {

        @Test
        @DisplayName("envolve o schema de NotaJuizCompleta em ResponseFormat strict:true")
        void envolveOSchema() {
            var options = builder.optionsCompleta();
            assertThat(options.getResponseFormat()).isNotNull();
        }
    }

    @Nested
    @DisplayName("optionsReduzida")
    class OptionsReduzida {

        @Test
        @DisplayName("envolve o schema de NotaJuizReduzida em ResponseFormat strict:true")
        void envolveOSchema() {
            var options = builder.optionsReduzida();
            assertThat(options.getResponseFormat()).isNotNull();
        }
    }
}
