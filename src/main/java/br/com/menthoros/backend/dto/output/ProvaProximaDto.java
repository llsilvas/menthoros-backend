package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Dados de uma prova próxima (simplificado para listagem)")
public record ProvaProximaDto(
    @Schema(description = "Identificador único da prova")
    UUID id,

    @Schema(description = "Identificador do atleta")
    UUID atletaId,

    @Schema(description = "Nome do atleta")
    String nomeAtleta,

    @Schema(description = "Nome da prova", example = "Maratona Internacional de São Paulo")
    String nomeProva,

    @Schema(description = "Data da prova em formato ISO", example = "2026-05-15")
    String dataProva,

    @Schema(description = "Tipo da prova", example = "MEIA_MARATONA")
    String tipoProva,

    @Schema(description = "Distância da prova", example = "MEIA_MARATONA")
    String distancia,

    @Schema(description = "Distância em quilômetros", example = "21.1")
    Double distanciaKm,

    @Schema(description = "Objetivo/Meta da prova")
    String objetivo,

    @Schema(description = "Status da prova", example = "PLANEJADA")
    String statusProva,

    @Schema(description = "Dias faltando para a prova", example = "5")
    Integer diasFaltando
) {}
