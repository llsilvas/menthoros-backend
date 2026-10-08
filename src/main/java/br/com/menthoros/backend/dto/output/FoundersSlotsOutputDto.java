package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Vagas da turma fundadora — fonte única de verdade para o número que hoje é texto fixo
 * ("10 vagas") na home e em {@code /waitlist}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Vagas da turma fundadora")
public record FoundersSlotsOutputDto(
        @Schema(description = "Total de vagas configurado", example = "10") int total,
        @Schema(description = "Vagas ocupadas (convites não invalidados)", example = "6") int taken,
        @Schema(description = "Vagas restantes, nunca negativo", example = "4") int remaining,
        @Schema(description = "Falso quando as vagas acabaram", example = "true") boolean open
) {
}
