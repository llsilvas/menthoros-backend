package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.FonteDados;
import com.menthoros.enums.TipoTreino;
import com.menthoros.enums.TreinoExecucaoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Dados de saída de um treino realizado")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreinoRealizadoOutputDto(
        @Schema(description = "Identificador único do treino realizado", example = "123e4567-e89b-12d3-a456-426614174004")
        UUID id,

        @Schema(description = "Data do treino", example = "2024-01-15")
        LocalDate dataTreino,

        @Schema(description = "Dia da semana", example = "SEGUNDA")
        DiaSemana diaSemana,

        @Schema(description = "Tipo do treino", example = "TREINO_LONGO")
        TipoTreino tipoTreino,

        @Schema(description = "Duração em minutos", example = "65")
        Integer duracaoMin,

        @Schema(description = "Distância em quilômetros", example = "10.8")
        Double distanciaKm,

        @Schema(description = "Frequência cardíaca média (bpm)", example = "152")
        Integer fcMedia,

        @Schema(description = "Frequência cardíaca máxima (bpm)", example = "178")
        Integer fcMax,

        @Schema(description = "Ritmo médio", example = "5:45 min/km")
        String ritmoMedio,

        @Schema(description = "Potência média em watts", example = "245")
        Integer potenciaMedia,

        @Schema(description = "Comentário sobre o treino", example = "Treino intenso, últimos 2km foram difíceis")
        String comentario,

        @Schema(description = "Fonte dos dados", example = "GARMIN")
        FonteDados fonteDados,

        @Schema(description = "Status do treino", example = "CONCLUIDO")
        TreinoExecucaoStatus status,

        @Schema(description = "Percepção de esforço (1-10)", example = "8")
        Integer percepcaoEsforco
) {}
