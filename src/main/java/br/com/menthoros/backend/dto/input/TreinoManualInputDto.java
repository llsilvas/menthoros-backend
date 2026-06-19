package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.TipoTreino;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Dados para registro manual de treino pelo próprio atleta")
public record TreinoManualInputDto(

    @Schema(description = "Tipo do treino", example = "CONTINUO")
    @NotNull(message = "Tipo do treino é obrigatório")
    TipoTreino tipo,

    @Schema(description = "Data do treino (máximo 7 dias no passado)", example = "2026-06-19")
    @NotNull(message = "Data do treino é obrigatória")
    @PastOrPresent(message = "Data do treino não pode ser no futuro")
    LocalDate data,

    @Schema(description = "Duração do treino em minutos", example = "45")
    @NotNull(message = "Duração é obrigatória")
    @Positive(message = "Duração deve ser positiva")
    Integer duracaoMinutos,

    @Schema(description = "Distância percorrida em km (opcional)", example = "8.5")
    @DecimalMin(value = "0.0", inclusive = false, message = "Distância deve ser positiva quando informada")
    BigDecimal distanciaKm,

    @Schema(description = "Percepção subjetiva de esforço (1–10)", example = "6")
    @NotNull(message = "Percepção de esforço é obrigatória")
    @Min(value = 1, message = "RPE mínimo é 1")
    @Max(value = 10, message = "RPE máximo é 10")
    Integer percepcaoEsforco,

    @Schema(description = "Observações livres sobre o treino (máx 500 caracteres)")
    @Size(max = 500, message = "Observações devem ter no máximo 500 caracteres")
    String observacoes
) {}
