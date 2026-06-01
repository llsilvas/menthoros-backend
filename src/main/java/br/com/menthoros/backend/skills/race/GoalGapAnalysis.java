package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.GapAssessment;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Análise de gap entre a meta definida pelo coach e a projeção atual.
 * Exposto somente no painel do coach — nunca para o atleta (D8).
 * Presente apenas quando CoachGoalOverride é fornecido no input.
 */
@Schema(description = "Análise de gap coach-only entre meta e projeção atual")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoalGapAnalysis(

        @Schema(description = "Meta de tempo do coach em segundos", example = "6300")
        Long goalTimeSeconds,

        @Schema(description = "Tempo projetado em segundos para a distância-alvo", example = "6150")
        Long projectedTimeSeconds,

        @Schema(description = "Diferença em segundos (positivo = projeção melhor que meta)", example = "150")
        Long gapSeconds,

        @Schema(description = "Diferença percentual em relação à meta", example = "2.38")
        Double gapPct,

        @Schema(description = "Avaliação qualitativa do gap")
        GapAssessment gapAssessment,

        @Schema(description = "Nota factual gerada pelo LLM sobre o gap", example = "Projeção atual: 1h42. Meta: 1h45 (gap -2.8% — ON_TRACK).")
        String coachNoteGap
) {}
