package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Etapa ou bloco repetido de treino enviado pelo coach")
public record EtapaInputDto(

        @Schema(description = "Tipo da etapa: AQUECIMENTO, PRINCIPAL, INTERVALADO, RECUPERACAO, DESAQUECIMENTO, ou BLOCO para bloco repetido")
        @NotBlank(message = "tipoEtapa não pode ser vazio")
        @Pattern(regexp = "(?i)AQUECIMENTO|PRINCIPAL|INTERVALADO|RECUPERACAO|DESAQUECIMENTO|BLOCO",
                 message = "tipoEtapa deve ser: AQUECIMENTO, PRINCIPAL, INTERVALADO, RECUPERACAO, DESAQUECIMENTO ou BLOCO")
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
        Integer repeticoes,

        @Schema(description = "Número de repetições do bloco — somente quando tipoEtapa=BLOCO", example = "4")
        @Positive
        Integer blocoRepeticoes,

        @Schema(description = "Etapas internas do bloco — somente quando tipoEtapa=BLOCO")
        @Valid
        List<EtapaInputDto> subEtapas
) {}
