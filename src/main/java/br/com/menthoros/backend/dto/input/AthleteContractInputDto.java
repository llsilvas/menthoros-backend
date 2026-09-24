package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.ContractPeriodicity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Contrato do atleta com a assessoria — cria ou edita (upsert). Edição vale só para mensalidades futuras.")
public record AthleteContractInputDto(
        @Schema(description = "Periodicidade da cobrança", example = "MONTHLY", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Periodicidade é obrigatória")
        ContractPeriodicity periodicity,

        @Schema(description = "Valor por período. Opcional no contrato migrado; a UI exige.", example = "250.00")
        @PositiveOrZero(message = "Valor não pode ser negativo")
        BigDecimal amount,

        @Schema(description = "Dia do vencimento (1–31; meses curtos usam o último dia)", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Dia de vencimento é obrigatório")
        @Min(value = 1, message = "Dia de vencimento deve estar entre 1 e 31")
        @Max(value = 31, message = "Dia de vencimento deve estar entre 1 e 31")
        Integer dueDay,

        @Schema(description = "Início do contrato", example = "2026-09-21", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Data de início é obrigatória")
        LocalDate startDate,

        @Schema(description = "Avisar o atleta por e-mail antes do vencimento (consumido pela change de aviso). Default true.", example = "true")
        Boolean athleteNoticeEnabled
) {
}
