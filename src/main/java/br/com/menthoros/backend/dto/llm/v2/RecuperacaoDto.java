package br.com.menthoros.backend.dto.llm.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Recuperação de um {@link BlocoDto} — sempre resolve na zona {@code Z1} (sem campo {@code zona}
 * próprio), mesma convenção implícita de v1 (design.md, semantic-session-schema, Decisão 2).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecuperacaoDto(
        @Schema(description = "Quantidade de recuperação por repetição", example = "90")
        BigDecimal quantidade,

        @Schema(description = "Unidade da quantidade de recuperação")
        UnidadeQuantidade unidade
) {
}
