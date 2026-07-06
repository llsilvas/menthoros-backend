package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.services.EncerramentoLoteResultado;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Resumo consolidado do encerramento em lote da semana da assessoria")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EncerramentoLoteOutputDto(

        @Schema(description = "Atletas com semana corrente que foram encerrados", example = "8")
        int atletasProcessados,

        @Schema(description = "Atletas sem plano na semana corrente (ignorados)", example = "2")
        int atletasSemPlano,

        @Schema(description = "Quantos planos ficaram CONCLUIDO", example = "8")
        int planosConcluidos,

        @Schema(description = "Total de treinos marcados como PERDIDO", example = "23")
        int treinosPerdidosTotal,

        @Schema(description = "Resumo por atleta encerrado")
        List<EncerramentoSemanaOutputDto> resultados,

        @Schema(description = "Atletas cujo encerramento falhou (lote não abortado)")
        List<EncerramentoFalhaAtletaOutputDto> falhas
) {

    public static EncerramentoLoteOutputDto from(EncerramentoLoteResultado r) {
        return new EncerramentoLoteOutputDto(
                r.atletasProcessados(),
                r.atletasSemPlano(),
                r.planosConcluidos(),
                r.treinosPerdidosTotal(),
                r.resultados().stream().map(EncerramentoSemanaOutputDto::from).toList(),
                r.falhas().stream().map(EncerramentoFalhaAtletaOutputDto::from).toList());
    }
}
