package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.RecommendationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Saída da revisão semanal do coach (Fatia 1 — determinística).
 *
 * <p>Campos congelados vêm da {@code RevisaoSemanal}; janela e {@code tsbFim} vêm do
 * {@code PlanoSemanal} associado. {@code weekOverWeekDelta} (computado na leitura) é adicionado
 * pela leitura (Fatia 1, bloco 4); {@code nextWeekFocus} entra na Fatia 2.
 */
public record RevisaoSemanalOutputDto(
        UUID planoSemanalId,
        LocalDate semanaInicio,
        LocalDate semanaFim,
        RecommendationType recommendationType,
        NivelAderencia adherenceStatus,
        BigDecimal percentualRealizacao,
        BigDecimal tsbFim,
        boolean dadosSuficientes,
        Instant geradaEm
) {}
