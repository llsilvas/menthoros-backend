package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.PeriodizationPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Projeção de carga de treino (CTL/ATL/TSB) até o dia da prova.
 * Fornecida manualmente pelo coach na v0.1 — automatizada via plano aprovado na v0.2.
 */
@Schema(description = "Projeção de carga de treino até o dia da prova")
public record LoadProjection(

        @Schema(description = "CTL atual no momento da geração", example = "52.4")
        Double currentCtl,

        @Schema(description = "ATL atual no momento da geração", example = "58.1")
        Double currentAtl,

        @Schema(description = "TSB atual (CTL - ATL) no momento da geração", example = "-5.7")
        Double currentTsb,

        @NotNull
        @Schema(description = "Data da prova alvo", example = "2026-09-20")
        LocalDate targetRaceDate,

        @Schema(description = "Semanas restantes até a prova no momento da geração", example = "8")
        Integer weeksToRace,

        @NotNull
        @Schema(description = "Fase de periodização planejada para o dia da prova")
        PeriodizationPhase plannedPeriodizationPhase,

        @Schema(description = "CTL projetado para o dia da prova (entrada manual do coach)", example = "61.0")
        Double projectedCtlOnRaceDay,

        @Schema(description = "TSB projetado para o dia da prova (entrada manual do coach)", example = "5.0")
        Double projectedTsbOnRaceDay
) {}
