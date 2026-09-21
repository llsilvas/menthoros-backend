package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.InvoiceStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Só sai pelo endpoint do proprietário: carrega valor (design D5). */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Mensalidade do contrato do atleta")
public record AthleteInvoiceOutputDto(
        @Schema(description = "ID da mensalidade")
        UUID id,

        @Schema(description = "ID do contrato")
        UUID contractId,

        @Schema(description = "Vencimento", example = "2026-10-10")
        LocalDate dueDate,

        @Schema(description = "Valor no momento da geração; ausente no contrato migrado sem valor", example = "250.00")
        BigDecimal amount,

        @Schema(description = "OPEN, PAID ou CANCELLED. Vencida = OPEN com vencimento no passado", example = "OPEN")
        InvoiceStatus status,

        @Schema(description = "Data do pagamento; presente só em PAID", example = "2026-10-08")
        LocalDate paidAt,

        @Schema(description = "Valor pago; presente só em PAID", example = "250.00")
        BigDecimal paidAmount,

        @Schema(description = "Derivado: OPEN e vencimento anterior a hoje", example = "false")
        boolean overdue
) {
}
