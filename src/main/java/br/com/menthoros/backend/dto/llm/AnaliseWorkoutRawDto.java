package br.com.menthoros.backend.dto.llm;

import br.com.menthoros.backend.enums.PrimaryAnalysisCause;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Resposta bruta do LLM para análise de treino (em inglês).
 * Campos textuais são traduzidos antes de persistir.
 */
public record AnaliseWorkoutRawDto(
        String summary,

        @JsonProperty("technical_interpretation")
        String technicalInterpretation,

        @JsonProperty("primary_cause")
        PrimaryAnalysisCause primaryCause,

        String recommendation,

        List<String> tags,

        @JsonProperty("execution_score")
        Integer executionScore,

        String rationale
) {}
