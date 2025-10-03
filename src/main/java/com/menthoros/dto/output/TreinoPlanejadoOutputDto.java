package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.TipoTreino;
import com.menthoros.enums.TreinoExecucaoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados de saída de um treino planejado")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreinoPlanejadoOutputDto(
        @Schema(description = "Identificador único do treino planejado", example = "123e4567-e89b-12d3-a456-426614174003")
        UUID id,

        @Schema(description = "Dia da semana do treino", example = "SEGUNDA")
        DiaSemana diaSemana,

        @Schema(description = "Tipo do treino", example = "TREINO_LONGO")
        TipoTreino tipoTreino,

        @Schema(description = "Descrição do treino", example = "5km aquecimento + 10km ritmo forte + 2km desaquecimento")
        String descricao,

        @Schema(description = "Observações sobre o treino", example = "Atenção ao ritmo nos primeiros 5km")
        String observacao,

        @Schema(description = "Frequência cardíaca alvo", example = "140-155 bpm")
        String fcAlvo,

        @Schema(description = "Data do treino", example = "2024-01-15")
        LocalDate dataTreino,

        @Schema(description = "Percepção de esforço esperada (1-10)", example = "7")
        Integer percepcaoEsforcoEsperada,

        @Schema(description = "Duração em minutos", example = "60")
        Integer duracaoMin,

        @Schema(description = "Distância em quilômetros", example = "10.5")
        Double distanciaKm,

        @Schema(description = "Ritmo alvo", example = "5:30 min/km")
        String ritmoAlvo,

        @Schema(description = "ID do plano semanal", example = "123e4567-e89b-12d3-a456-426614174001")
        UUID planoSemanalId,

        @Schema(description = "Lista de etapas do treino")
        List<EtapaTreinoDto> etapas,

        @Schema(description = "Status de execução do treino", example = "PENDENTE")
        TreinoExecucaoStatus statusTreino
) {}
