package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.services.onboarding.ConfidenceTier;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Resultado da conclusão do onboarding (task 6.0.3): perfil migrado, prova "
        + "alvo criada/atualizada e baseline/score inicial calculados.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OnboardingConclusaoOutputDto(
        @Schema(description = "Status do perfil de onboarding após a conclusão", example = "COMPLETO")
        String status,

        @Schema(description = "Prova alvo criada/atualizada a partir do dataProva (CA13)")
        ProvaOutputDto provaAlvo,

        @Schema(description = "CTL estimado do baseline inicial")
        Double ctlEstimado,

        @Schema(description = "Data de referência da estimativa de baseline")
        LocalDate dataEstimativaBaseline,

        @Schema(description = "Score de confiança normalizado (0.0-1.0)")
        Double confidenceScore,

        @Schema(description = "Tier de confiança (A/B/C)")
        ConfidenceTier confidenceTier
) {
}
