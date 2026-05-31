package br.com.menthoros.backend.skills.race;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO de entrada agregado da RaceProjectionSkill.
 * Montado pelo service que chama a skill via AthleteProfileMapper — nunca pela skill.
 */
@Schema(description = "Input completo para geração de projeção de tempos de prova")
public record RaceProjectionInput(

        @NotNull @Valid
        @Schema(description = "Perfil fisiológico do atleta")
        AthleteProfile athleteProfile,

        @NotNull @Valid
        @Schema(description = "Histórico de treinos (60–90 dias recomendados)")
        TrainingHistory trainingHistory,

        @NotNull @Valid
        @Schema(description = "Projeção de carga até o dia da prova")
        LoadProjection loadProjection,

        @Schema(description = "Provas anteriores para calibração do expoente de Riegel")
        List<@Valid PastRace> pastRaces,

        @NotEmpty
        @Schema(description = "Distâncias-alvo em metros para as quais gerar projeções", example = "[5000, 10000, 21097, 42195]")
        List<Integer> targetDistances,

        @Valid
        @Schema(description = "Meta do coach para ativar GoalGapAnalysis. Null = sem gap analysis.")
        CoachGoalOverride coachGoalOverride,

        @Schema(description = "Override manual de semanas até a prova. Null = usar loadProjection.weeksToRace.", example = "10")
        Integer weeksToRaceOverride
) {}
