package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Status da conexão intervals.icu do atleta — nunca contém a API key")
public record IntervalsIcuConnectionStatusDto(
        @Schema(description = "Conexão ativa") boolean conectado,
        @Schema(description = "Id do atleta no intervals.icu", example = "i641775") String externalAthleteId,
        @Schema(description = "Quando a conexão foi criada") Instant conectadoEm,
        @Schema(description = "Timestamp do último push bem-sucedido") Instant ultimoPush,
        @Schema(description = "Último erro de push/autenticação, legível") String ultimoErro
) {}
