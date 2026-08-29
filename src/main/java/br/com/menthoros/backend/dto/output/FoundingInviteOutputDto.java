package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resultado do convite emitido pelo ADMIN. <strong>Nunca</strong> carrega o token: o único lugar
 * onde ele existe em claro é o e-mail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Convite de assessoria fundadora emitido a partir da waitlist")
public record FoundingInviteOutputDto(
        @Schema(description = "Id do convite") UUID id,
        @Schema(description = "Id do inscrito na waitlist") UUID waitlistId,
        @Schema(description = "Validade do link") OffsetDateTime expiresAt
) {
}
