package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Baixa de mensalidade. Ambos os campos são opcionais: data default hoje, valor default o da mensalidade.")
public record InvoicePaymentInputDto(
        @Schema(description = "Data do pagamento (default: hoje); não pode ser futura", example = "2026-10-08")
        @PastOrPresent(message = "Data do pagamento não pode ser futura")
        LocalDate paidAt,

        @Schema(description = "Valor pago (default: valor da mensalidade)", example = "250.00")
        @PositiveOrZero(message = "Valor pago não pode ser negativo")
        BigDecimal paidAmount
) {
}
