package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Dados de saída de uma etapa realizada do treino")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EtapaRealizadaOutputDto(

        @Schema(description = "ID da etapa realizada")
        UUID id,

        @Schema(description = "ID da etapa planejada correspondente")
        UUID etapaPlanejadaId,

        @Schema(description = "Ordem da etapa no treino", example = "1")
        Integer ordem,

        @Schema(description = "Tipo da etapa", example = "INTERVALADO")
        String tipoEtapa,

        @Schema(description = "Descrição da etapa", example = "Tiro 1 - 1000m")
        String descricao,

        @Schema(description = "Duração da etapa (formato MM:SS ou HH:MM:SS)", example = "04:00")
        String duracao,

        @Schema(description = "Distância percorrida em km", example = "1.0")
        Double distanciaKm,

        @Schema(description = "Frequência cardíaca média (bpm)", example = "175")
        Integer fcMedia,

        @Schema(description = "Frequência cardíaca máxima (bpm)", example = "185")
        Integer fcMax,

        @Schema(description = "Pace médio (formato MM:SS)", example = "04:15")
        String paceMedia,

        @Schema(description = "Velocidade média em km/h", example = "14.1")
        Double velocidadeMedia,

        @Schema(description = "Percepção de esforço (1-10)", example = "8")
        Integer percepcaoEsforco,

        @Schema(description = "Cadência média (passos por minuto)", example = "180")
        Integer cadenciaMedia,

        @Schema(description = "Potência média em watts", example = "250")
        Integer potenciaMedia,

        @Schema(description = "Observações sobre a etapa", example = "Senti pernas pesadas")
        String observacao,

        @Schema(description = "Running dynamics da etapa (GCT, equilíbrio, passada, oscilação, proporção "
                        + "vertical, temperatura, tempo em movimento) — calorias sempre ausente aqui, só "
                        + "existe a nível de sessão")
        RunningDynamicsOutputDto runningDynamics
) {}
