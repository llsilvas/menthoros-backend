package br.com.menthoros.backend.dto.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bloco do atleta gerado pela segunda chamada da análise pós-treino (skill
 * athlete-workout-motivation, rota simple) — já em PT-BR, não passa pelo tradutor.
 * Separado de {@link AnaliseWorkoutRawDto} de propósito: a análise do coach e o retorno do
 * atleta são artefatos distintos (design D2 de analise-ia-treino-atleta).
 */
public record AthleteMessageDto(
        String recognition,

        @JsonProperty("how_it_went")
        String howItWent,

        @JsonProperty("effort_reading")
        String effortReading,

        @JsonProperty("next_workout_tip")
        String nextWorkoutTip
) {}
