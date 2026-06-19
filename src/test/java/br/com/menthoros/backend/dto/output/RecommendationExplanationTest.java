package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.ExplanationConfidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecommendationExplanation")
class RecommendationExplanationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("ExplanationConfidence")
    class ExplanationConfidenceTest {

        @ParameterizedTest
        @EnumSource(ExplanationConfidence.class)
        @DisplayName("todos os valores do enum existem e são não-nulos")
        void valoresExistem(ExplanationConfidence confidence) {
            assertThat(confidence).isNotNull();
            assertThat(confidence.name()).isNotBlank();
        }

        @Test
        @DisplayName("enum contém exatamente HIGH, MEDIUM, LOW")
        void valoresExatos() {
            assertThat(ExplanationConfidence.values())
                    .containsExactly(ExplanationConfidence.HIGH, ExplanationConfidence.MEDIUM, ExplanationConfidence.LOW);
        }
    }

    @Nested
    @DisplayName("construção e serialização")
    class ConstrucaoSerializacao {

        @Test
        @DisplayName("record constrói com todos os campos presentes")
        void constroiComCamposValidos() {
            var explanation = new RecommendationExplanation(
                    "TSB em -40.0 situa-se na zona CRITICO, indicando fadiga excessiva.",
                    List.of("CoachAttentionSignalEvaluator.avaliarFadiga", "FaixaTsb.CRITICO"),
                    ExplanationConfidence.HIGH
            );

            assertThat(explanation.rationale()).isNotBlank();
            assertThat(explanation.sourceRules()).containsExactly(
                    "CoachAttentionSignalEvaluator.avaliarFadiga", "FaixaTsb.CRITICO");
            assertThat(explanation.confidence()).isEqualTo(ExplanationConfidence.HIGH);
        }

        @Test
        @DisplayName("serialização Jackson inclui os 3 campos quando não-nulos")
        void serializaComCamposPresentes() throws Exception {
            var explanation = new RecommendationExplanation(
                    "Atleta sem plano ativo; impossível avaliar carga ou progressão.",
                    List.of("CoachAttentionSignalEvaluator.avaliarSemPlano"),
                    ExplanationConfidence.HIGH
            );

            String json = mapper.writeValueAsString(explanation);

            assertThat(json).contains("\"rationale\"");
            assertThat(json).contains("\"sourceRules\"");
            assertThat(json).contains("\"confidence\"");
            assertThat(json).contains("\"HIGH\"");
        }

        @Test
        @DisplayName("@JsonInclude(NON_NULL): campo null é excluído do JSON")
        void campoNuloExcluidoDoJson() throws Exception {
            // rationale null — não deveria ocorrer em produção, mas o contrato de NON_NULL deve ser honrado
            var explanation = new RecommendationExplanation(
                    null,
                    List.of("CoachAttentionSignalEvaluator.avaliarSemPlano"),
                    ExplanationConfidence.HIGH
            );

            String json = mapper.writeValueAsString(explanation);

            assertThat(json).doesNotContain("\"rationale\"");
            assertThat(json).contains("\"sourceRules\"");
            assertThat(json).contains("\"confidence\"");
        }
    }
}
