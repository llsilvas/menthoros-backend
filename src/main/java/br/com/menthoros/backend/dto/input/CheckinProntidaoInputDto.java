package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Dados de entrada para registro do checkin diário de prontidão")
public record CheckinProntidaoInputDto(

    @Schema(description = "Data do checkin (default: hoje). Não pode ser no futuro.", example = "2026-07-02")
    @PastOrPresent(message = "Data do checkin não pode ser no futuro")
    LocalDate data,

    @Schema(description = "Qualidade do sono (1–10)", example = "8")
    @NotNull(message = "Qualidade do sono é obrigatória")
    @Min(value = 1, message = "Qualidade do sono mínima é 1")
    @Max(value = 10, message = "Qualidade do sono máxima é 10")
    Integer qualidadeSono,

    @Schema(description = "Humor (1–10)", example = "7")
    @NotNull(message = "Humor é obrigatório")
    @Min(value = 1, message = "Humor mínimo é 1")
    @Max(value = 10, message = "Humor máximo é 10")
    Integer humor,

    @Schema(description = "Dores musculares (0–10)", example = "2")
    @NotNull(message = "Dores musculares são obrigatórias")
    @Min(value = 0, message = "Dores musculares mínimas é 0")
    @Max(value = 10, message = "Dores musculares máximas é 10")
    Integer doresMusculares,

    @Schema(description = "Nível de energia (1–10)", example = "6")
    @NotNull(message = "Nível de energia é obrigatório")
    @Min(value = 1, message = "Nível de energia mínimo é 1")
    @Max(value = 10, message = "Nível de energia máximo é 10")
    Integer nivelEnergia,

    @Schema(description = "Estresse (0–10)", example = "3")
    @NotNull(message = "Estresse é obrigatório")
    @Min(value = 0, message = "Estresse mínimo é 0")
    @Max(value = 10, message = "Estresse máximo é 10")
    Integer estresse,

    @Schema(description = "Observações livres (máx 500 caracteres)")
    @Size(max = 500, message = "Observações devem ter no máximo 500 caracteres")
    String observacoes
) {}
