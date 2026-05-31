package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.DataQuality;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Histórico de treinos do atleta para uso nas camadas 1 e 2 da skill.
 * Camada 1 exige mínimo de 6 sessões LONGO/TEMPO_RUN para regressão válida.
 */
@Schema(description = "Histórico de treinos do atleta (60–90 dias recomendados)")
public record TrainingHistory(

        @NotEmpty
        @Schema(description = "Lista de treinos individuais")
        List<WorkoutSummary> workouts,

        @NotNull
        @Schema(description = "Qualidade geral dos dados disponíveis")
        DataQuality dataQuality,

        @Schema(description = "Número de semanas com dados disponíveis", example = "10")
        Integer weeksAvailable
) {}
