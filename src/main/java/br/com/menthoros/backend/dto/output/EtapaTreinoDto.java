package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Dados de uma etapa de treino")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EtapaTreinoDto(
        @Schema(description = "Ordem da etapa no treino", example = "1")
        Integer ordem,

        @Schema(description = "Tipo da etapa: AQUECIMENTO, PRINCIPAL, INTERVALADO, RECUPERACAO ou DESAQUECIMENTO", example = "AQUECIMENTO")
        String tipoEtapa,

        @Schema(description = "Descrição da etapa", example = "Corrida leve em ritmo confortável")
        String descricaoEtapa,

        @Schema(description = "Duração da etapa em minutos", example = "10")
        Integer duracaoMin,

        @Schema(description = "Distância da etapa em quilômetros", example = "2.0")
        Double distanciaKm,

        @Schema(description = "Frequência cardíaca alvo da etapa", example = "70-80% FCmáx")
        String fcAlvoEtapa,

        @Schema(description = "Número de repetições da etapa", example = "1")
        Integer repeticoes,

        @Schema(description = "UUID do bloco repetido ao qual esta etapa pertence (nulo para etapas avulsas)")
        UUID blocoId,

        @Schema(description = "Total de repetições do bloco ao qual esta etapa pertence", example = "4")
        Integer blocoRepeticoes
) {}
