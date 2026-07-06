package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Corpo do 202 Accepted do disparo do lote — identifica o job criado para
 * o polling subsequente em {@code GET /coach/planos/lote/{jobId}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Confirmação do disparo do lote de geração de planos")
public record BatchLoteAceitoOutputDto(

        @Schema(description = "Identificador do job criado")
        UUID jobId,

        @Schema(description = "Total de atletas do lote (após deduplicação)", example = "5")
        int totalAtletas
) {}
