package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.enums.TipoTreino;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Resumo de um treino individual para uso na Camada 1 (regressão de pace normalizado).
 * Camada 1 filtra por TipoTreino.LONGO e TipoTreino.TEMPO_RUN com mínimo de 6 sessões.
 */
@Schema(description = "Resumo de um treino individual para entrada na RaceProjectionSkill")
public record WorkoutSummary(

        @Schema(description = "Data do treino", example = "2026-04-15")
        LocalDate date,

        @Schema(description = "Tipo do treino conforme classificação do sistema")
        TipoTreino type,

        @Schema(description = "Distância percorrida em metros", example = "15000")
        Integer distanceM,

        @Schema(description = "Duração total em segundos", example = "4500")
        Integer durationSeconds,

        @Schema(description = "Pace médio em segundos por km", example = "300")
        Integer avgPaceSecPerKm,

        @Schema(description = "Frequência cardíaca média (bpm). Null se monitor não usado.", example = "148")
        Integer avgHr,

        @Schema(description = "Training Stress Score calculado", example = "85")
        Integer tss,

        @Schema(description = "Rating of Perceived Exertion (1–10)", example = "7")
        Integer rpe
) {}
