package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.TipoEtapa;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados de uma etapa de treino")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EtapaTreinoDto(
        @Schema(description = "Ordem da etapa no treino", example = "1")
        Integer ordem,

        @Schema(description = "Tipo da etapa", example = "AQUECIMENTO")
        TipoEtapa tipoEtapa,

        @Schema(description = "Descrição da etapa", example = "Corrida leve em ritmo confortável")
        String descricaoEtapa,

        @Schema(description = "Duração da etapa em minutos", example = "10")
        Integer duracaoMin,

        @Schema(description = "Distância da etapa em quilômetros", example = "2.0")
        Double distanciaKm,

        @Schema(description = "Frequência cardíaca alvo da etapa", example = "130-140 bpm")
        String fcAlvoEtapa,

        @Schema(description = "Número de repetições da etapa", example = "1")
        Integer repeticoes
) {}

