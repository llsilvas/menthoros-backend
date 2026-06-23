package br.com.menthoros.backend.services.prompt.constraint;

/**
 * Identifica o tipo de uma {@link Constraint} — dirige tanto a renderização no prompt
 * quanto o dispatch da verificação no {@code PlanQualityChecker}.
 *
 * <p>Schema de {@code params} por key (ver factories em {@link Constraint}):
 * <ul>
 *   <li>{@link #INTERVALADO_PROIBIDO} — sem params (regra de presença: plano não pode conter INTERVALADO).</li>
 *   <li>{@link #INTERVALADO_MAX_CATEGORIA} — {@code categoriaSegura: String}. Declarada/renderizada;
 *       verificação adiada (precisa do mapa de categorias).</li>
 *   <li>{@link #PACE_TETO} — {@code teto: Map<String(TipoTreino), BigDecimal>} (pace em minutos decimais).</li>
 *   <li>{@link #DIAS_PERMITIDOS} — {@code dias: List<String(DiaSemana)>}.</li>
 *   <li>{@link #MAX_CONSECUTIVOS} — {@code n: Integer} (máx. de dias de treino consecutivos).</li>
 * </ul>
 */
public enum ConstraintKey {
    INTERVALADO_PROIBIDO,
    INTERVALADO_MAX_CATEGORIA,
    PACE_TETO,
    DIAS_PERMITIDOS,
    MAX_CONSECUTIVOS,
    /** FC limiar inferido por mediana do quintil superior dos últimos 30 dias. */
    LIMIAR_FC_ESTIMADO,
    /** Pace limiar inferido por mediana do quintil mais rápido nos últimos 30 dias. */
    LIMIAR_PACE_ESTIMADO
}
