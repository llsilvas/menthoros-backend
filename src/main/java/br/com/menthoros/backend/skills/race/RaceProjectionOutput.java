package br.com.menthoros.backend.skills.race;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * DTO de saída completo da RaceProjectionSkill.
 * Montado pelo RaceProjectionAssembler e persistido como snapshot imutável.
 */
@Schema(description = "Output completo da RaceProjectionSkill")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RaceProjectionOutput(

        @Schema(description = "Projeções por distância-alvo (chave = distância em metros)")
        Map<Integer, RaceProjection> projections,

        @Schema(description = "Narrativa de progressão gerada pelo LLM (máx 500 chars)", example = "João mantém progressão consistente nas últimas 10 semanas...")
        String progressionNarrative,

        @Schema(description = "Previsão de CTL até o dia da prova")
        CTLForecast ctlForecast,

        @Schema(description = "Premissas explícitas da projeção (máx 5 itens)")
        List<String> keyAssumptions,

        @Schema(description = "Nota do coach gerada pelo LLM (máx 400 chars)")
        String coachNote,

        @Schema(description = "Análise de gap vs meta do coach. Null quando CoachGoalOverride não fornecido.")
        GoalGapAnalysis goalGapAnalysis
) {}
