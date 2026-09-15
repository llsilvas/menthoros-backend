package br.com.menthoros.backend.dto.llm.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Bloco qualitativo de um treino no schema v2 — a LLM decide estrutura (papel, repetições, zona),
 * o Java resolve os números absolutos ({@link br.com.menthoros.backend.domain.planner.SessionResolver}).
 * Sem pace, FC, distância ou duração — isso é {@code quantidadePorRepeticao}/{@code unidade},
 * neutros até a resolução.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlocoDto(
        @Schema(description = "Papel do bloco no treino")
        Papel papel,

        @Schema(description = "Número de repetições — 1 para blocos que não repetem", example = "6")
        Integer repeticoes,

        @Schema(description = "Quantidade de cada repetição (não o total)", example = "400")
        BigDecimal quantidadePorRepeticao,

        @Schema(description = "Unidade de quantidadePorRepeticao")
        UnidadeQuantidade unidade,

        @Schema(description = "Zona de intensidade alvo do bloco")
        Zona zona,

        @Schema(description = "Recuperação entre repetições — omitida quando o bloco não repete ou não tem recuperação")
        RecuperacaoDto recuperacao
) {
}
