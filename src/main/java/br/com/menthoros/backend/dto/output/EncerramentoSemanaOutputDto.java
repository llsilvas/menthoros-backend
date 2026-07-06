package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.OrigemEncerramento;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.services.EncerramentoSemanaResultado;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Resumo do encerramento da semana de um plano")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EncerramentoSemanaOutputDto(

        @Schema(description = "ID do plano encerrado")
        UUID planoId,

        @Schema(description = "Status do plano após o encerramento", example = "CONCLUIDO")
        PlanoStatus novoStatus,

        @Schema(description = "Quantidade de treinos marcados como PERDIDO", example = "2")
        int treinosFinalizados,

        @Schema(description = "IDs dos treinos marcados como PERDIDO")
        List<UUID> treinosPerdidos,

        @Schema(description = "true quando o plano ficou CONCLUIDO (pronto para gerar a próxima semana)", example = "true")
        boolean prontoParaProximaSemana,

        @Schema(description = "Origem do encerramento", example = "ON_DEMAND")
        OrigemEncerramento origem,

        @Schema(description = "Aviso quando a semana ainda não terminou; ausente caso contrário")
        String aviso
) {

    public static EncerramentoSemanaOutputDto from(EncerramentoSemanaResultado r) {
        if (r == null) {
            throw new IllegalArgumentException("EncerramentoSemanaResultado não pode ser null");
        }
        return new EncerramentoSemanaOutputDto(
                r.planoId(), r.novoStatus(), r.treinosFinalizados(), r.treinosPerdidos(),
                r.prontoParaProximaSemana(), r.origem(), r.aviso());
    }
}
