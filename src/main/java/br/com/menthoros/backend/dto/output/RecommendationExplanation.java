package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.ExplanationConfidence;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Contrato canônico de explicabilidade de um sinal de atenção ou recomendação.
 *
 * <p>Descreve <strong>apenas o sinal principal</strong> da priorização. Os sinais secundários
 * estão acessíveis via {@code evidence[]} no DTO pai.
 *
 * <p>Reutilizável em fila de atenção, prescrição de plano (Sprint 10+) e inbox de sugestões
 * (Sprint 15). Na v1, {@code confidence} é sempre {@link ExplanationConfidence#HIGH}
 * (todos os sinais da fila de atenção são determinísticos).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Explicabilidade estruturada de uma recomendação ou sinal de atenção")
public record RecommendationExplanation(

        @Schema(description = "Sentença explicando por que este sinal foi acionado (PT-BR, determinístico na v1)")
        String rationale,

        @Schema(description = "Regras/classificadores que dispararam o sinal, no formato ClassName.methodOrConstant")
        List<String> sourceRules,

        @Schema(description = "Grau de confiança: HIGH=determinístico, MEDIUM=heurístico, LOW=derivado de LLM")
        ExplanationConfidence confidence
) {}
