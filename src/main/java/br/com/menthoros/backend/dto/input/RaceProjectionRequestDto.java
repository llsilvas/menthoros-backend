package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.skills.race.enums.PeriodizationPhase;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Parâmetros para geração de projeção de tempo de prova")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RaceProjectionRequestDto(

    @Schema(description = "ID da prova alvo (opcional — projeção pode ser independente de prova)")
    UUID provaId,

    @NotEmpty(message = "Pelo menos uma distância-alvo é obrigatória")
    @Schema(description = "Distâncias-alvo em metros", example = "[10000, 21097]")
    List<Integer> targetDistances,

    @NotNull(message = "Dados de carga são obrigatórios")
    @Valid
    @Schema(description = "Dados de carga e periodização fornecidos pelo coach")
    LoadProjectionData loadProjection,

    @Schema(description = "Meta definida pelo coach para análise de gap (opcional)")
    @Valid
    CoachGoalData coachGoalOverride,

    @Schema(description = "Override de semanas até a prova (opcional — usa loadProjection.weeksToRace por padrão)")
    Integer weeksToRaceOverride

) {

    @Schema(description = "Dados de carga e periodização atuais do atleta")
    public record LoadProjectionData(
        @NotNull(message = "CTL atual é obrigatório") Double currentCtl,
        @NotNull(message = "ATL atual é obrigatório") Double currentAtl,
        @NotNull(message = "TSB atual é obrigatório") Double currentTsb,
        @NotNull(message = "Data-alvo da prova é obrigatória") LocalDate targetRaceDate,
        @NotNull(message = "Semanas até a prova é obrigatório") Integer weeksToRace,
        @NotNull(message = "Fase de periodização é obrigatória") PeriodizationPhase plannedPeriodizationPhase,
        @NotNull(message = "CTL projetado no dia da prova é obrigatório") Double projectedCtlOnRaceDay,
        @NotNull(message = "TSB projetado no dia da prova é obrigatório") Double projectedTsbOnRaceDay
    ) {}

    @Schema(description = "Meta do coach para análise de gap")
    public record CoachGoalData(
        @NotNull(message = "Tempo-meta em segundos é obrigatório") Long goalTimeSeconds,
        @NotNull(message = "Distância-alvo da meta é obrigatória") Integer targetDistanceM
    ) {}
}
