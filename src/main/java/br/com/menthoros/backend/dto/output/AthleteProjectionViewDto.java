package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Visão simplificada de projeção de prova para o atleta (sem confiança numérica, sem gap e sem coach_note)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AthleteProjectionViewDto(
    @Schema(description = "ID da prova associada") UUID provaId,
    @Schema(description = "Data e hora de geração") Instant generatedAt,
    @Schema(description = "Projeções por distância (chave: metros)") Map<Integer, ProjectionSummary> projections,
    @Schema(description = "Narrativa de progressão gerada pelo LLM") String progressionNarrative,
    @Schema(description = "Premissas-chave consideradas na projeção") List<String> keyAssumptions
) {

    @Schema(description = "Resumo de projeção para o atleta")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProjectionSummary(
        @Schema(description = "Distância em metros") int distanceM,
        @Schema(description = "Tempo projetado em segundos") long projectedTimeSeconds,
        @Schema(description = "Pace projetado em seg/km") int projectedPaceSecPerKm,
        @Schema(description = "Se há potencial de PR") boolean prPotential
    ) {}
}
