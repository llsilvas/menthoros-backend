package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.RecommendationType;

import java.math.BigDecimal;

/**
 * Comparação da revisão com a semana (revisão) anterior do atleta — computada na leitura,
 * não persistida (CA9). Sem revisão anterior ⇒ {@link #semAnterior()} ({@code primeiraSemana=true}).
 */
public record WeekOverWeekDelta(
        boolean primeiraSemana,
        BigDecimal deltaPercentualRealizacao,
        BigDecimal deltaTsbFim,
        RecommendationType recommendationAnterior
) {

    public static WeekOverWeekDelta semAnterior() {
        return new WeekOverWeekDelta(true, null, null, null);
    }
}
