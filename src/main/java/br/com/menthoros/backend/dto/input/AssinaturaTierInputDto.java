package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.PlanoAssessoria;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Dados de entrada para troca de tier da assinatura (PATCH). A troca é sempre administrativa
 * (design.md Decisão 6 / CA9) — o novo valor é aplicado no Asaas e o tier no entitlement local.
 */
@Schema(description = "Dados para troca de tier de uma assinatura")
public record AssinaturaTierInputDto(

        @Schema(description = "Novo tier (entitlement da assessoria)", example = "ENTERPRISE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Plano (tier) é obrigatório")
        PlanoAssessoria plano,

        @Schema(description = "Novo valor mensal da assinatura", example = "499.90",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Valor é obrigatório")
        @Positive(message = "Valor deve ser positivo")
        BigDecimal valor
) {}
