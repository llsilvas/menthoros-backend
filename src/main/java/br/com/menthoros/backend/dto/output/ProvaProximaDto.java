package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Dados de uma prova próxima (simplificado para listagem)")
public class ProvaProximaDto {

    @Schema(description = "Identificador único da prova")
    private UUID id;

    @Schema(description = "Identificador do atleta")
    private UUID atletaId;

    @Schema(description = "Nome do atleta")
    private String nomeAtleta;

    @Schema(description = "Nome da prova", example = "Maratona Internacional de São Paulo")
    private String nomeProva;

    @Schema(description = "Data da prova em formato ISO", example = "2026-05-15")
    private String dataProva;

    @Schema(description = "Tipo da prova", example = "MEIA_MARATONA")
    private String tipoProva;

    @Schema(description = "Distância da prova", example = "MEIA_MARATONA")
    private String distancia;

    @Schema(description = "Distância em quilômetros", example = "21.1")
    private Double distanciaKm;

    @Schema(description = "Objetivo/Meta da prova")
    private String objetivo;

    @Schema(description = "Status da prova", example = "PLANEJADA")
    private String statusProva;

    @Schema(description = "Dias faltando para a prova", example = "5")
    private Integer diasFaltando;
}
