package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.ConfiancaInferencia;
import br.com.menthoros.backend.enums.FonteLimiarInferencia;

import java.math.BigDecimal;

/**
 * Resultado puro da decisão de qual fonte de pace limiar vence (prova > quintil) — sem mutação,
 * sem `PlanoMetaDados` (refactor-threshold-call-outside-transaction, design.md D1).
 */
public record PaceLimiarResolvido(FonteLimiarInferencia fonte, BigDecimal valor, ConfiancaInferencia confianca) {
}
