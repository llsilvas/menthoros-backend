package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.services.FalhaAtleta;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Atleta cujo encerramento de semana falhou durante o lote")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EncerramentoFalhaAtletaOutputDto(

        @Schema(description = "ID do atleta cujo encerramento falhou")
        UUID atletaId,

        @Schema(description = "Motivo (mensagem segura, sem detalhes internos)")
        String motivo
) {

    public static EncerramentoFalhaAtletaOutputDto from(FalhaAtleta falha) {
        if (falha == null) {
            throw new IllegalArgumentException("FalhaAtleta não pode ser null");
        }
        return new EncerramentoFalhaAtletaOutputDto(falha.atletaId(), falha.motivo());
    }
}
