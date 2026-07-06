package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Filtro opcional do encerramento em lote da assessoria")
public record EncerrarLoteInputDto(

        @Schema(description = "IDs dos atletas a encerrar; vazio ou ausente encerra todos os atletas da assessoria")
        List<UUID> atletaIds
) {
}
