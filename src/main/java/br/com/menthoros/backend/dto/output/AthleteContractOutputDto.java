package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.ContractPeriodicity;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Só sai pelo endpoint do proprietário: carrega valor (design D5). */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Contrato do atleta com a assessoria e suas mensalidades (mais recente primeiro)")
public record AthleteContractOutputDto(
        @Schema(description = "ID do contrato")
        UUID id,

        @Schema(description = "ID do atleta")
        UUID athleteId,

        @Schema(description = "Periodicidade", example = "MONTHLY")
        ContractPeriodicity periodicity,

        @Schema(description = "Valor por período; ausente no contrato migrado", example = "250.00")
        BigDecimal amount,

        @Schema(description = "Dia do vencimento (1–31)", example = "10")
        int dueDay,

        @Schema(description = "Início do contrato", example = "2026-09-21")
        LocalDate startDate,

        @Schema(description = "Encerramento; ausente enquanto ativo")
        OffsetDateTime endedAt,

        @Schema(description = "Contrato ativo (sem encerramento)", example = "true")
        boolean active,

        @Schema(description = "Avisar o atleta por e-mail antes do vencimento", example = "true")
        boolean athleteNoticeEnabled,

        @Schema(description = "Mensalidades do contrato, mais recente primeiro")
        List<AthleteInvoiceOutputDto> invoices
) {
}
