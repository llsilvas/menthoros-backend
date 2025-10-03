package com.menthoros.dto.input;

import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.TipoTreino;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Dados de entrada para criação de um treino planejado")
public record TreinoPlanejadoInputDto(
        @Schema(description = "Dia da semana do treino", example = "SEGUNDA")
        DiaSemana diaSemana,

        @Schema(description = "Tipo do treino", example = "TREINO_LONGO")
        TipoTreino tipoTreino,

        @Schema(description = "Descrição detalhada do treino", example = "5km aquecimento + 10km ritmo forte + 2km desaquecimento")
        String descricao,

        @Schema(description = "Observações adicionais sobre o treino", example = "Atenção ao ritmo nos primeiros 5km")
        String observacao,

        @Schema(description = "Frequência cardíaca alvo", example = "140-155 bpm")
        String fcAlvo,

        @Schema(description = "Data do treino", example = "2024-01-15")
        LocalDate dataTreino,

        @Schema(description = "Percepção de esforço esperada (escala 1-10)", example = "7", minimum = "1", maximum = "10")
        Integer percepcaoEsforcoEsperada,

        @Schema(description = "Duração do treino em minutos", example = "60")
        Integer duracaoMin,

        @Schema(description = "Distância do treino em quilômetros", example = "10.5")
        Double distanciaKm,

        @Schema(description = "Ritmo alvo do treino", example = "5:30 min/km")
        String ritmoAlvo,

        @Schema(description = "ID do plano semanal vinculado", example = "123e4567-e89b-12d3-a456-426614174001")
        UUID planoSemanalId,

        @Schema(description = "ID do atleta vinculado", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID atletaId
) {}
