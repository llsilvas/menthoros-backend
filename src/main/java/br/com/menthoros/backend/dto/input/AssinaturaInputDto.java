package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.PlanoAssessoria;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados de entrada para criar a assinatura de cobrança de uma assessoria (POST).
 *
 * <p><strong>PCI (design.md Decisão 3):</strong> {@code creditCardToken} é sempre um token
 * pré-tokenizado do Asaas — nunca PAN/CVV bruto. O {@link #toString()} é sobrescrito para mascarar
 * o token (evita vazamento em log de request/erro).
 */
@Schema(description = "Dados de entrada para criar a assinatura de cobrança de uma assessoria")
public record AssinaturaInputDto(

        @Schema(description = "Token de cartão pré-tokenizado do Asaas (creditCardToken). Nunca enviar PAN/CVV bruto.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Token de cartão é obrigatório")
        String creditCardToken,

        @Schema(description = "Data da primeira cobrança (futuro suporta trial)", example = "2026-09-01",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Data da próxima cobrança é obrigatória")
        LocalDate nextDueDate,

        @Schema(description = "Valor mensal da assinatura", example = "199.90",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Valor é obrigatório")
        @Positive(message = "Valor deve ser positivo")
        BigDecimal valor,

        @Schema(description = "Tier vendido (entitlement da assessoria)", example = "PRO",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Plano (tier) é obrigatório")
        PlanoAssessoria plano
) {

    /** Mascara o token de cartão — nunca deve aparecer em log/toString (PCI, Decisão 3). */
    @Override
    public String toString() {
        return "AssinaturaInputDto[creditCardToken=***, nextDueDate=" + nextDueDate
                + ", valor=" + valor + ", plano=" + plano + "]";
    }
}
