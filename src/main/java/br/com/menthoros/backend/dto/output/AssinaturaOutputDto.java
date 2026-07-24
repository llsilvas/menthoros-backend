package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.StatusAssinatura;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Dados de saída de uma assinatura. Não expõe token de cartão nem ids do Asaas
 * (dado interno/sensível — PCI, design.md Decisão 3).
 */
@Schema(description = "Dados de saída de uma assinatura de cobrança")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssinaturaOutputDto(

        @Schema(description = "Identificador único da assinatura")
        UUID id,

        @Schema(description = "Assessoria dona da assinatura")
        UUID assessoriaId,

        @Schema(description = "Estado da cobrança", example = "ATIVA")
        StatusAssinatura status,

        @Schema(description = "Tier (entitlement) atual da assessoria", example = "PRO")
        PlanoAssessoria plano,

        @Schema(description = "Valor mensal", example = "199.90")
        BigDecimal valor,

        @Schema(description = "Data da próxima cobrança")
        Instant dataProximaCobranca,

        @Schema(description = "Criação")
        Instant criadoEm,

        @Schema(description = "Última atualização")
        Instant atualizadoEm
) {}
