package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Dados para adição manual de um treino ao plano pelo coach durante revisão")
public record TreinoPlanejadoAddDto(

        @Schema(description = "Tipo do treino (ex: CORRIDA_LONGA, TREINO_FORCA)", example = "CORRIDA_LONGA")
        @NotBlank(message = "tipoTreino é obrigatório")
        String tipoTreino,

        @Schema(description = "Data do treino (deve estar dentro do intervalo do plano)", example = "2026-07-03")
        @NotNull(message = "dataTreino é obrigatória")
        LocalDate dataTreino,

        @Schema(description = "Descrição do treino", example = "5km aquecimento + 10km ritmo forte")
        String descricao,

        @Schema(description = "Distância em quilômetros", example = "12.0")
        @Positive(message = "distanciaKm deve ser positivo")
        Double distanciaKm,

        @Schema(description = "Duração em minutos", example = "75")
        @Positive(message = "duracaoMin deve ser positivo")
        Integer duracaoMin,

        @Schema(description = "Zona alvo do treino", example = "z2-z3")
        String zonaAlvo,

        @Schema(description = "Percepção de esforço esperada (1–10)", example = "7")
        @Min(value = 1, message = "percepcaoEsforcoEsperada mínimo é 1")
        @Max(value = 10, message = "percepcaoEsforcoEsperada máximo é 10")
        Integer percepcaoEsforcoEsperada,

        @Schema(description = "TSS planejado (calculado automaticamente quando ausente e duracaoMin informado)", example = "75")
        @Positive(message = "tssPlanejado deve ser positivo")
        Integer tssPlanejado,

        @Schema(description = "Observações sobre o treino", example = "Atenção ao ritmo nos primeiros 5km")
        String observacoes,

        @Schema(description = "Etapas do treino (opcional)")
        @Valid
        List<EtapaInputDto> etapas
) {}
