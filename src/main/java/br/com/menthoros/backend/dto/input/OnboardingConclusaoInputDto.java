package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.TipoProva;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Dados da prova alvo para concluir o onboarding (CA13, design.md Decisão 8) — "
        + "cria/atualiza a Prova do atleta a partir de dataProva.")
public record OnboardingConclusaoInputDto(
        @Schema(description = "Data da prova alvo", example = "2026-10-12", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "dataProva é obrigatória")
        LocalDate dataProva,

        @Schema(description = "Tipo da prova alvo", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "tipoProva é obrigatório")
        TipoProva tipoProva,

        @Schema(description = "Distância da prova alvo", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "distancia é obrigatória")
        DistanciaProva distancia,

        @Schema(description = "Distância em quilômetros (para distâncias customizadas)", example = "42.195")
        BigDecimal distanciaKm,

        @Schema(description = "Nome da prova alvo", example = "Maratona Internacional de São Paulo")
        String nomeProva
) {
}
