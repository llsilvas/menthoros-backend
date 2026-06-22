package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Etapa de treino enviada pelo coach no patch de treino planejado")
public record EtapaInputDto(

        @Schema(description = "Tipo da etapa: AQUECIMENTO, PRINCIPAL, INTERVALADO, RECUPERACAO, DESAQUECIMENTO")
        @Size(max = 30)
        String tipoEtapa,

        @Schema(description = "Descrição da etapa")
        @Size(max = 300)
        String descricaoEtapa,

        @Schema(description = "Duração da etapa em minutos", example = "10")
        @Positive
        Integer duracaoMin,

        @Schema(description = "Distância da etapa em quilômetros", example = "2.0")
        @Positive
        Double distanciaKm,

        @Schema(description = "Frequência cardíaca alvo da etapa", example = "70-80% FCmáx")
        @Size(max = 50)
        String fcAlvoEtapa,

        @Schema(description = "Número de repetições — somente para tipo INTERVALADO", example = "4")
        @Positive
        Integer repeticoes
) {}
