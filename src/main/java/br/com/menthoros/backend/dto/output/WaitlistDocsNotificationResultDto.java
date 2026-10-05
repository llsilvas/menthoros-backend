package br.com.menthoros.backend.dto.output;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado do disparo em massa do aviso da central de ajuda aos treinadores da waitlist. */
@Schema(description = "Contagem do disparo de aviso da central de ajuda aos treinadores da waitlist")
public record WaitlistDocsNotificationResultDto(
        @Schema(description = "Inscritos TREINADOR ainda não avisados no início desta chamada") int elegiveis,
        @Schema(description = "E-mails enviados com sucesso nesta chamada") int enviados,
        @Schema(description = "Falhas de envio nesta chamada (inscrito segue elegível no próximo disparo)") int falhas
) {
}
