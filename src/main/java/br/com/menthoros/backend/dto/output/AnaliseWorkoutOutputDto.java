package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.enums.PrimaryAnalysisCause;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resultado da análise pós-treino gerada por IA")
public record AnaliseWorkoutOutputDto(

        @Schema(description = "ID da análise", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "ID do treino realizado analisado")
        UUID treinoRealizadoId,

        @Schema(description = "Status da análise", example = "COMPLETED")
        AnaliseStatus status,

        @Schema(description = "Resumo da análise em português", example = "Execução mais difícil que o esperado")
        String summary,

        @Schema(description = "Interpretação técnica detalhada")
        String technicalInterpretation,

        @Schema(description = "Causa principal identificada", example = "ACCUMULATED_FATIGUE")
        PrimaryAnalysisCause primaryCause,

        @Schema(description = "Recomendação acionável", example = "Recuperação ativa nos próximos 2 dias")
        String recommendation,

        @Schema(description = "Tags de classificação", example = "[\"FATIGUE_DETECTED\", \"RECOVERY_NEEDED\"]")
        List<String> tags,

        @Schema(description = "Score de qualidade de execução (1-10)", example = "6")
        Integer executionScore,

        @Schema(description = "Justificativa do score e achados principais")
        String rationale,

        @Schema(description = "Data/hora em que a análise foi concluída")
        Instant analyzedAt
) {}
