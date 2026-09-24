package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.NivelExperiencia;

import java.util.Map;

/**
 * Limiares de fadiga por nível de experiência — régua única de
 * {@link IntervaladoElegibilidadeService} (que degrada a intensidade) e de
 * {@link FatigueSignalsService} (que lista os sinais para a regra de cobertura da semana).
 *
 * <p>Um sinal, uma régua: os dois precisam concordar sobre "o atleta está cansado", senão o prompt
 * pede uma coisa e a validação cobra outra (add-descanso-explicito-por-fadiga, Decisão 3).</p>
 */
public final class FatigueThresholds {

    private FatigueThresholds() {
    }

    /** TSB de prontidão abaixo disto indica fadiga acumulada. */
    private static final Map<NivelExperiencia, Double> TSB = Map.of(
            NivelExperiencia.INICIANTE, -10.0,
            NivelExperiencia.INTERMEDIARIO, -15.0,
            NivelExperiencia.AVANCADO, -20.0,
            NivelExperiencia.ELITE, -25.0
    );

    /** Horas mínimas de recuperação desde o último treino intensivo. */
    private static final Map<NivelExperiencia, Long> HORAS_RECUPERACAO = Map.of(
            NivelExperiencia.INICIANTE, 72L,
            NivelExperiencia.INTERMEDIARIO, 60L,
            NivelExperiencia.AVANCADO, 48L,
            NivelExperiencia.ELITE, 48L
    );

    /** CTL abaixo disto indica base aeróbica insuficiente. */
    private static final Map<NivelExperiencia, Double> CTL_MINIMO = Map.of(
            NivelExperiencia.INICIANTE, 15.0,
            NivelExperiencia.INTERMEDIARIO, 25.0,
            NivelExperiencia.AVANCADO, 40.0,
            NivelExperiencia.ELITE, 55.0
    );

    /** Média de RPE dos últimos 7 dias a partir da qual a carga percebida é alta. */
    public static final double RPE_MEDIO_7D = 7.5;

    /** TSB abaixo disto bloqueia intervalado de forma absoluta. */
    public static final double TSB_BLOQUEIO_ABSOLUTO = -30.0;

    public static double tsb(NivelExperiencia nivel) {
        return TSB.getOrDefault(nivel, -15.0);
    }

    public static long horasRecuperacao(NivelExperiencia nivel) {
        return HORAS_RECUPERACAO.getOrDefault(nivel, 60L);
    }

    public static double ctlMinimo(NivelExperiencia nivel) {
        return CTL_MINIMO.getOrDefault(nivel, 25.0);
    }
}
