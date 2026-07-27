package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.RecommendationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Lógica determinística da revisão semanal (Fatia 1 — add-weekly-review-consolidation).
 * Regras congeladas: ADR-0006, design D4–D5. Sem estado, sem I/O — puro.
 *
 * <p>Limiares v1 (ajustáveis; o congelamento na {@code tb_revisao_semanal} preserva a história
 * mesmo que estes mudem — ver ADR-0006).
 */
public final class RevisaoSemanalCalculator {

    /** Aderência ≥ 90% → ALTA. */
    static final BigDecimal ADERENCIA_ALTA_MIN = new BigDecimal("90");
    /** Aderência ≥ 60% → MEDIA (senão BAIXA). */
    static final BigDecimal ADERENCIA_MEDIA_MIN = new BigDecimal("60");
    /** Piso de fadiga: TSB ≤ −25 → RECOVERY (ancorado em RecoveryCargaSkill/retention-radar). */
    static final BigDecimal TSB_PISO_RECOVERY = new BigDecimal("-25");
    /** Faixa de fadiga: TSB ≤ −10 com baixa aderência → RECOVERY; TSB ≥ −10 é pré-requisito de PROGRESS. */
    static final BigDecimal TSB_FADIGA = new BigDecimal("-10");
    /** Mínimo de treinos realizados na janela para conclusão forte. */
    static final int MIN_TREINOS_SUFICIENTE = 2;
    /** Treino de "alta criticidade" — {@code TipoTreino.getFatorImpacto() ≥ 1.15} (LONGO em diante). */
    static final double FATOR_IMPACTO_CRITICO = 1.15;
    /** Folga sobre a semana imediatamente anterior, para absorver encerramento atrasado (D11). */
    static final int FOLGA_JANELA_DIAS = 7;

    private RevisaoSemanalCalculator() {
    }

    /** {@code true} se o {@code fatorImpacto} do tipo de treino for de alta criticidade (≥ 1.15). */
    public static boolean treinoCritico(double fatorImpacto) {
        return fatorImpacto >= FATOR_IMPACTO_CRITICO;
    }

    /** % de aderência (0–100, 2 casas) por contagem realizados/planejados. 0 planejados → 0 (degenerado). */
    public static BigDecimal completionRate(int planejados, int realizados) {
        if (planejados <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(realizados)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(planejados), 2, RoundingMode.HALF_UP);
    }

    /** Corte ALTA/MEDIA/BAIXA; ≥1 treino crítico faltando força BAIXA (CA1b). */
    public static NivelAderencia nivelAderencia(BigDecimal percentual, boolean criticoFaltando) {
        if (criticoFaltando) {
            return NivelAderencia.BAIXA;
        }
        if (percentual.compareTo(ADERENCIA_ALTA_MIN) >= 0) {
            return NivelAderencia.ALTA;
        }
        if (percentual.compareTo(ADERENCIA_MEDIA_MIN) >= 0) {
            return NivelAderencia.MEDIA;
        }
        return NivelAderencia.BAIXA;
    }

    /** {@code false} se &lt;2 treinos realizados ou sem ponto de TSB válido (tsbFim nulo) — CA3. */
    public static boolean sufficientData(int realizados, BigDecimal tsbFim) {
        return realizados >= MIN_TREINOS_SUFICIENTE && tsbFim != null;
    }

    /**
     * Árvore determinística (D5), avaliada nesta ordem:
     * <ol>
     *   <li>{@code tsbFim} nulo → MAINTAIN (ramos numéricos não se aplicam — CA3b);</li>
     *   <li>RECOVERY se {@code tsbFim ≤ −25} ou ({@code BAIXA} e {@code tsbFim ≤ −10});</li>
     *   <li>PROGRESS se {@code ALTA} e {@code tsbFim ≥ −10} e {@code sufficientData};</li>
     *   <li>MAINTAIN caso contrário.</li>
     * </ol>
     */
    public static RecommendationType recommendationType(NivelAderencia status, BigDecimal tsbFim,
                                                        boolean sufficientData) {
        if (tsbFim == null) {
            return RecommendationType.MAINTAIN;
        }
        boolean fadigaAltaPiso = tsbFim.compareTo(TSB_PISO_RECOVERY) <= 0;
        boolean baixaComFadiga = status == NivelAderencia.BAIXA && tsbFim.compareTo(TSB_FADIGA) <= 0;
        if (fadigaAltaPiso || baixaComFadiga) {
            return RecommendationType.RECOVERY;
        }
        if (status == NivelAderencia.ALTA && tsbFim.compareTo(TSB_FADIGA) >= 0 && sufficientData) {
            return RecommendationType.PROGRESS;
        }
        return RecommendationType.MAINTAIN;
    }

    /**
     * Se uma revisão ainda é insumo válido para o plano que está sendo gerado (D11).
     *
     * <p>O {@code recommendationType} deriva do {@code tsbFim} daquela semana e apodrece rápido:
     * um atleta que ficou três semanas sem plano (lesão, férias, pausa) teria "a revisão mais
     * recente" mandando reduzir carga logo depois de um período destreinando. Como o coach não vê
     * o prompt, ele não tem como filtrar isso — a janela filtra por ele.
     *
     * @param semanaFimRevisao   fim da semana revisada
     * @param semanaInicioPlano  início da semana do plano sendo gerado
     */
    public static boolean withinConsumptionWindow(LocalDate semanaFimRevisao, LocalDate semanaInicioPlano) {
        if (semanaFimRevisao == null || semanaInicioPlano == null) {
            return false;
        }
        if (semanaFimRevisao.isAfter(semanaInicioPlano)) {
            return false; // revisão do futuro — data inconsistente, não consome
        }
        return !semanaFimRevisao.isBefore(semanaInicioPlano.minusDays(FOLGA_JANELA_DIAS));
    }

    /**
     * Foco da próxima semana em forma determinística, derivado só do {@code recommendationType}.
     *
     * <p>É o fallback da narrativa por LLM: vale com a flag desligada, quando a chamada falha e
     * quando o checker de consistência reprova a narrativa. Como o texto deriva do tipo, ele nunca
     * pode contrariá-lo — que é exatamente a garantia que o checker precisa impor sobre o LLM.
     * Nenhuma revisão nasce sem foco.
     */
    public static String nextWeekFocusTemplate(RecommendationType recommendationType) {
        if (recommendationType == null) {
            throw new IllegalArgumentException("recommendationType não pode ser nulo");
        }
        return switch (recommendationType) {
            case RECOVERY -> "Prioridade: recuperação. Reduza o volume da semana e evite treinos "
                    + "de intensidade até a fadiga ceder.";
            case MAINTAIN -> "Prioridade: manter a carga atual e consolidar a consistência antes "
                    + "de qualquer evolução.";
            case PROGRESS -> "Prioridade: evolução gradual do volume, preservando a qualidade dos "
                    + "treinos-chave.";
        };
    }
}
