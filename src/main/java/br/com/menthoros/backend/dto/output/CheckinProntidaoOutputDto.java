package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.NivelProntidao;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Dados de saída de um checkin de prontidão")
public record CheckinProntidaoOutputDto(

    @Schema(description = "ID do checkin")
    UUID id,

    @Schema(description = "ID do atleta")
    UUID atletaId,

    @Schema(description = "Data do checkin", example = "2026-07-02")
    LocalDate data,

    @Schema(description = "Qualidade do sono (1–10)", example = "8")
    Integer qualidadeSono,

    @Schema(description = "Humor (1–10)", example = "7")
    Integer humor,

    @Schema(description = "Dores musculares (0–10)", example = "2")
    Integer doresMusculares,

    @Schema(description = "Nível de energia (1–10)", example = "6")
    Integer nivelEnergia,

    @Schema(description = "Estresse (0–10)", example = "3")
    Integer estresse,

    @Schema(description = "Observações livres")
    String observacoes,

    @Schema(description = "Score de prontidão calculado (0–1)", example = "0.82")
    BigDecimal readinessScore,

    @Schema(description = "Nível de prontidão classificado", example = "PRONTO")
    NivelProntidao nivelProntidao
) {}
