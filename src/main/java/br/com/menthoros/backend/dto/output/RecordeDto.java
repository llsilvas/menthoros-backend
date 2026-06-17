package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Recorde pessoal (PR) do atleta para uma distância de referência.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Recorde pessoal (PR) por distância")
public record RecordeDto(

        @Schema(description = "Distância de referência", example = "10k")
        String distancia,

        @Schema(description = "Melhor tempo registrado (ISO-8601)", example = "PT45M30S")
        Duration tempo,

        @Schema(description = "Data do treino que estabeleceu o PR", example = "2026-05-12")
        LocalDate data,

        @Schema(description = "ID do treino realizado de origem")
        UUID treinoRealizadoId
) {}
