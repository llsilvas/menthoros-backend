package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.enums.DistanciaProva;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * Temporada aprovada vista pelo atleta (add-season-plan-approve-athlete-view, A2). Sem validações, CTL,
 * TSS nem dados do treinador: o atleta vê para onde a preparação vai, não o raciocínio dela.
 */
@Schema(description = "Temporada aprovada do atleta: prova-alvo, fase atual e próximos marcos")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AthleteSeasonPlanDto(
        @Schema(description = "Nome da prova-alvo", example = "Maratona de São Paulo") String raceName,
        @Schema(description = "Data da prova-alvo", example = "2027-04-04") LocalDate raceDate,
        @Schema(description = "Distância da prova-alvo", example = "KM_42") DistanciaProva distance,
        @Schema(description = "Dias até a prova", example = "139") long daysToRace,
        @Schema(description = "Fase da semana corrente; ausente fora do horizonte da temporada", example = "BASE")
        TrainingPhase currentPhase,
        @Schema(description = "Provas preparatórias de hoje em diante, em ordem; vazia se não há")
        List<Milestone> upcomingMilestones
) {

    @Schema(description = "Prova preparatória da temporada")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Milestone(
            @Schema(description = "Nome da prova", example = "Meia de São Paulo") String name,
            @Schema(description = "Data da prova", example = "2026-12-27") LocalDate date,
            @Schema(description = "Distância da prova", example = "KM_21") DistanciaProva distance
    ) {
    }
}
