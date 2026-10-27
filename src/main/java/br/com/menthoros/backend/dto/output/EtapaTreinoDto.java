package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.AlvoPrimario;
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

        @Schema(description = "Ritmo alvo da etapa (ex: '5:00-5:30/km')", example = "5:00-5:30/km")
        String ritmoAlvo,

        @Schema(description = "Número de repetições da etapa", example = "1")
        Integer repeticoes,

        @Schema(description = "UUID do bloco repetido ao qual esta etapa pertence (nulo para etapas avulsas)")
        UUID blocoId,

        @Schema(description = "Total de repetições do bloco ao qual esta etapa pertence", example = "4")
        Integer blocoRepeticoes,

        @Schema(description = "Eixo que o relógio controla nesta etapa; nulo em etapa legada (derivado do conteúdo)")
        AlvoPrimario alvoPrimario
) {

    public EtapaTreinoDto(Integer ordem, String tipoEtapa, String descricaoEtapa, Integer duracaoMin,
                          Double distanciaKm, String fcAlvoEtapa, String ritmoAlvo, Integer repeticoes,
                          UUID blocoId, Integer blocoRepeticoes) {
        this(ordem, tipoEtapa, descricaoEtapa, duracaoMin, distanciaKm, fcAlvoEtapa, ritmoAlvo, repeticoes,
                blocoId, blocoRepeticoes, null);
    }
}
