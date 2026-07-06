package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Requisição de geração de planos em lote para múltiplos atletas")
public record BatchGeracaoPlanoInputDto(

        @Schema(description = "IDs dos atletas para os quais gerar plano (1 a 20)")
        @NotEmpty(message = "Informe ao menos um atleta")
        @Size(max = 20, message = "O lote suporta no máximo 20 atletas")
        List<UUID> atletaIds,

        @Schema(description = "Modo de geração; ausente assume PROXIMA_SEMANA", example = "PROXIMA_SEMANA")
        ModoGeracaoPlano modo
) {
    public BatchGeracaoPlanoInputDto {
        if (modo == null) {
            modo = ModoGeracaoPlano.PROXIMA_SEMANA;
        }
    }
}
