package com.menthoros.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Dados de entrada para uma etapa realizada do treino")
public record EtapaRealizadaInputDto(

        @Schema(description = "ID da etapa planejada correspondente (opcional, para comparação)", example = "123e4567-e89b-12d3-a456-426614174010")
        UUID etapaPlanejadaId,

        @Schema(description = "Ordem da etapa no treino", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer ordem,

        @Schema(description = "Tipo da etapa", example = "INTERVALADO",
                allowableValues = {"AQUECIMENTO", "PRINCIPAL", "INTERVALADO", "RECUPERACAO", "DESAQUECIMENTO"})
        String tipoEtapa,

        @Schema(description = "Descrição da etapa", example = "Tiro 1 - 1000m")
        String descricao,

        @Schema(description = "Duração da etapa (formato MM:SS ou HH:MM:SS)", example = "04:00")
        String duracao,

        @Schema(description = "Distância percorrida na etapa em km", example = "1.0")
        Double distanciaKm,

        @Schema(description = "Frequência cardíaca média na etapa (bpm)", example = "175")
        Integer fcMedia,

        @Schema(description = "Frequência cardíaca máxima na etapa (bpm)", example = "185")
        Integer fcMax,

        @Schema(description = "Pace médio na etapa (formato MM:SS)", example = "04:15")
        String paceMedia,

        @Schema(description = "Velocidade média na etapa em km/h", example = "14.1")
        Double velocidadeMedia,

        @Schema(description = "Percepção de esforço da etapa (1-10)", example = "8", minimum = "1", maximum = "10")
        Integer percepcaoEsforco,

        @Schema(description = "Cadência média na etapa (passos por minuto)", example = "180")
        Integer cadenciaMedia,

        @Schema(description = "Potência média na etapa em watts", example = "250")
        Integer potenciaMedia,

        @Schema(description = "Observações sobre a etapa", example = "Senti pernas pesadas")
        String observacao
) {}
