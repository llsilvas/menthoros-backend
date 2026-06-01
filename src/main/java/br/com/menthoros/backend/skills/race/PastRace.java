package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.RaceConditions;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Prova passada usada como âncora na Camada 2 (calibração do expoente de Riegel).
 * Provas com menos de 12 meses têm peso 2x na calibração.
 */
@Schema(description = "Prova passada realizada pelo atleta")
public record PastRace(

        @Schema(description = "Distância da prova em metros", example = "21097")
        Integer distanceM,

        @Schema(description = "Tempo de chegada em segundos", example = "6300")
        Long finishTimeSeconds,

        @Schema(description = "Data da prova", example = "2025-09-20")
        LocalDate date,

        @Schema(description = "Condições da prova")
        RaceConditions conditions
) {}
