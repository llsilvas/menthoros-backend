package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.MotivoOmissaoVolta;
import br.com.menthoros.backend.enums.OrigemCalculo;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Série de eficiência (EF) por volta — o "decoupling volta a volta": mostra ONDE o treino
 * degradou, não só que degradou. Carrega os metadados de omissão para a UI reconciliar buracos
 * no gráfico com o decoupling escalar.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Série de eficiência (velocidade/FC e potência/FC) por volta do treino")
public record LapEfficiencySeriesDto(
        @Schema(description = "Granularidade do cálculo (POR_VOLTA nesta versão)", example = "POR_VOLTA")
        OrigemCalculo origem,

        @Schema(description = "Total de voltas do treino, incluídas ou não na série", example = "16")
        int totalVoltas,

        @Schema(description = "Voltas fora da série, com o motivo — reconcilia buracos no gráfico")
        List<VoltaOmitidaDto> voltasOmitidas,

        @Schema(description = "Pontos elegíveis da série, ordenados por volta")
        List<LapEfficiencyPointDto> pontos
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Volta omitida da série de eficiência")
    public record VoltaOmitidaDto(
            @Schema(description = "Ordem da volta no treino", example = "16")
            int ordem,

            @Schema(description = "Motivo da omissão", example = "SEM_FC")
            MotivoOmissaoVolta motivo
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Ponto da série de eficiência de uma volta")
    public record LapEfficiencyPointDto(
            @Schema(description = "Ordem da volta no treino", example = "3")
            int ordem,

            @Schema(description = "Velocidade da volta em km/h", example = "9.55")
            Double velocidadeKmh,

            @Schema(description = "FC média da volta (bpm)", example = "145")
            Integer fcMedia,

            @Schema(description = "Fator de eficiência velocidade/FC", example = "0.0659")
            Double efPace,

            @Schema(description = "Fator de eficiência potência/FC; ausente sem potência", example = "2.51")
            Double efPotencia,

            @Schema(description = "Potência relativa W/kg; ausente sem potência ou sem peso do atleta", example = "4.28")
            Double wPorKg,

            @Schema(description = "Pace ajustado por gradiente (GAP interno) — presente apenas quando o "
                    + "GAP estiver calibrado e habilitado", example = "6:05")
            String paceGap
    ) {}
}
