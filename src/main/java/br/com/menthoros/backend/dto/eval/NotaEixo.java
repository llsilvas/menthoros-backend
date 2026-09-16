package br.com.menthoros.backend.dto.eval;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Nota de um eixo da rubrica do juiz-LLM (plan-generation-eval-set, fatia 2) — escala 1-5, com
 * justificativa curta obrigatória (evita nota sem razão auditável).
 */
public record NotaEixo(
        @Schema(description = "Nota do eixo, escala 1 (péssimo) a 5 (excelente)") int nota,
        @Schema(description = "1-2 frases justificando a nota") String justificativa
) {
}
