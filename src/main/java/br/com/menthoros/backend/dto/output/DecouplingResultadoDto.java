package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.MotivoNullDecoupling;
import br.com.menthoros.backend.enums.OrigemCalculo;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Envelope do decoupling com proveniência e motivo de null por métrica — a UI explica um valor
 * ausente em vez de parecer inconsistência, e detecta quando a granularidade de cálculo mudar.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Decoupling aeróbico com proveniência: Pa:HR e Pw:HR, cada um com o motivo quando não calculado")
public record DecouplingResultadoDto(
        @Schema(description = "Decoupling Pa:HR (%) — mesmo valor do campo legado decouplingPercentual", example = "4.2")
        Double percentual,

        @Schema(description = "Motivo do Pa:HR não calculado; ausente quando calculado", example = "VARIABILIDADE_ALTA")
        MotivoNullDecoupling motivoNull,

        @Schema(description = "Decoupling Pw:HR (%) — potência/FC, mais estável que Pa:HR em terreno variável", example = "3.1")
        Double potenciaPercentual,

        @Schema(description = "Motivo do Pw:HR não calculado; ausente quando calculado", example = "COBERTURA_POTENCIA_INSUFICIENTE")
        MotivoNullDecoupling motivoNullPotencia,

        @Schema(description = "Granularidade do cálculo (POR_VOLTA nesta versão)", example = "POR_VOLTA")
        OrigemCalculo origem
) {}
