package br.com.menthoros.backend.enums;

/**
 * Constantes centralizadas para thresholds de métricas de treino.
 *
 * <p>Todos os valores de corte usados para classificar TSB, Ramp Rate,
 * dias consecutivos e semanas de progressão estão aqui.
 * Qualquer ajuste de threshold deve ser feito APENAS nesta classe.
 */
public final class MetricasThresholds {

    private MetricasThresholds() {}

    // ===== TSB (Training Stress Balance) =====

    /** TSB abaixo deste valor = fadiga crítica (risco de overtraining) */
    public static final double TSB_CRITICO = -35.0;

    /** TSB abaixo deste valor = sobrecarga / fadiga alta */
    public static final double TSB_SOBRECARGA = -30.0;

    /** TSB abaixo deste valor = fadiga moderada */
    public static final double TSB_FADIGA_MODERADA = -20.0;

    /**
     * TSB abaixo deste valor = recomendar semana de recuperação.
     *
     * <p>Nível próprio, entre {@link #TSB_FADIGA_MODERADA} e {@link #TSB_SOBRECARGA}: recomendar
     * recuperação acontece <b>antes</b> de o alarme de sobrecarga disparar. Não é cópia errada de
     * nenhum dos dois — o valor já era usado, idêntico, pelo {@code RecoveryCargaSkill} e pelo
     * {@code RevisaoSemanalCalculator}, cada um com sua própria constante. Nomeá-lo aqui foi o que
     * permitiu apagar as duas cópias.
     */
    public static final double TSB_PISO_RECOVERY = -25.0;

    /** TSB abaixo deste valor = acumulando fadiga */
    public static final double TSB_ACUMULANDO_FADIGA = -10.0;

    /** TSB abaixo deste valor = fatigado */
    public static final double TSB_FATIGADO = 0.0;

    /** TSB acima deste valor = início da forma ideal */
    public static final double TSB_FORMA_IDEAL_MIN = 5.0;

    /** TSB até este valor = forma ideal */
    public static final double TSB_FORMA_IDEAL_MAX = 15.0;

    /** TSB até este valor = descansado */
    public static final double TSB_DESCANSADO_MAX = 25.0;

    // ===== RAMP RATE =====

    /** Ramp rate acima deste valor = progressão crítica (risco de lesão) */
    public static final double RAMP_RATE_CRITICO = 10.0;

    /** Ramp rate acima deste valor = progressão rápida */
    public static final double RAMP_RATE_ALTO = 8.0;

    // ===== RAMP RATE (RELATIVO) =====

    /** Ramp rate relativo acima deste valor = progressão crítica (risco de lesão) */
    public static final double RAMP_RATE_RELATIVO_CRITICO = 15.0; // % do CTL/semana

    /** Ramp rate relativo acima deste valor = progressão rápida */
    public static final double RAMP_RATE_RELATIVO_ALTO = 10.0; // % do CTL/semana

    /**
     * CTL mínimo para estabilizar o cálculo de ramp rate relativo.
     *
     * <p>Quando CTL é muito baixo, percentuais podem explodir (ex: CTL=2 e ramp=3 => 150%).
     * Para manter a interpretação estável e ainda sensível, usa-se o maior valor entre
     * CTL anterior e este mínimo como denominador.
     */
    public static final double CTL_MINIMO_RAMP_RELATIVO = 10.0;

    // ===== DIAS CONSECUTIVOS =====

    /** Dias consecutivos a partir deste valor = alerta crítico */
    public static final int DIAS_CONSECUTIVOS_CRITICO = 6;

    /**
     * Dias consecutivos a partir deste valor = descanso obrigatório na classificação de prontidão.
     *
     * <p>Vem <b>depois</b> de {@link #DIAS_CONSECUTIVOS_CRITICO}: o alerta ao treinador dispara
     * primeiro (6), o bloqueio de carga só no dia seguinte (7). Valor extraído da constante privada
     * do {@code RecoveryCargaSkill}, que era a única a conhecê-lo.
     */
    public static final int DIAS_CONSECUTIVOS_RECOVERY = 7;

    /** Dias consecutivos a partir deste valor = alerta alto */
    public static final int DIAS_CONSECUTIVOS_ALTO = 5;

    // ===== SEMANAS PROGRESSÃO =====

    /** Semanas contínuas de progressão a partir deste valor = alerta */
    public static final int SEMANAS_PROGRESSAO_ALERTA = 4;
}
