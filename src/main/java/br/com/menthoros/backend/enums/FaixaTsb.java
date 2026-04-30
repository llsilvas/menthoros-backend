package br.com.menthoros.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * Classificação de TSB (Training Stress Balance) por faixas.
 *
 * <p>Cada faixa encapsula threshold, interpretação, status e recomendação,
 * eliminando a necessidade de duplicar essa lógica em múltiplos métodos.
 *
 * <p>Ordem dos valores importa: a classificação percorre do mais crítico
 * ao mais leve, retornando a primeira faixa correspondente.
 */
@Getter
@RequiredArgsConstructor
public enum FaixaTsb {

    FADIGA_EXCESSIVA(
            Double.NEGATIVE_INFINITY, MetricasThresholds.TSB_CRITICO,
            NivelAlerta.CRITICO,
            "Fadiga excessiva",
            "FADIGA CRÍTICA",
            "Dia de descanso completo OBRIGATÓRIO ou apenas atividade regenerativa leve (30min)."
    ),
    FADIGA_ALTA(
            MetricasThresholds.TSB_CRITICO, MetricasThresholds.TSB_SOBRECARGA,
            NivelAlerta.ALTO,
            "Fadiga alta",
            "FADIGA ALTA",
            "Reduzir volume em 30-40%. Priorizar treinos regenerativos e descanso."
    ),
    FADIGA_MODERADA(
            MetricasThresholds.TSB_SOBRECARGA, MetricasThresholds.TSB_FADIGA_MODERADA,
            NivelAlerta.ATENCAO,
            "Alta fadiga",
            "FADIGA MODERADA",
            "Fadiga moderada acumulada. Reduzir intensidade e priorizar recuperação entre sessões."
    ),
    ACUMULANDO_FADIGA(
            MetricasThresholds.TSB_FADIGA_MODERADA, MetricasThresholds.TSB_ACUMULANDO_FADIGA,
            NivelAlerta.ATENCAO,
            "Acumulando fadiga",
            "ACUMULANDO FADIGA",
            "Fadiga em acumulação. Evitar sessões intensas consecutivas e reforçar recuperação."
    ),
    FATIGADO(
            MetricasThresholds.TSB_ACUMULANDO_FADIGA, MetricasThresholds.TSB_FATIGADO,
            NivelAlerta.INFO,
            "Fatigado",
            "FATIGADO",
            "Fadiga normal de treinamento. Respeitar intervalos de recuperação entre sessões intensas."
    ),
    RECUPERANDO(
            MetricasThresholds.TSB_FATIGADO, MetricasThresholds.TSB_FORMA_IDEAL_MIN,
            NivelAlerta.INFO,
            "Recuperando",
            "RECUPERANDO",
            "Em fase de recuperação. Bom momento para treinos de base e consolidação."
    ),
    FORMA_IDEAL(
            MetricasThresholds.TSB_FORMA_IDEAL_MIN, MetricasThresholds.TSB_FORMA_IDEAL_MAX,
            NivelAlerta.INFO,
            "Forma ideal",
            "FORMA IDEAL",
            "Condições ideais para treinos intensos ou provas importantes."
    ),
    DESCANSADO(
            MetricasThresholds.TSB_FORMA_IDEAL_MAX, MetricasThresholds.TSB_DESCANSADO_MAX,
            NivelAlerta.INFO,
            "Descansado",
            "DESCANSADO",
            "Descansado. Considerar incluir sessão de qualidade para manter adaptações."
    ),
    MUITO_DESCANSADO(
            MetricasThresholds.TSB_DESCANSADO_MAX, Double.POSITIVE_INFINITY,
            NivelAlerta.ATENCAO,
            "Muito descansado",
            "MUITO DESCANSADO",
            "Muito descansado. Risco de perda de adaptações. Retomar volume gradualmente."
    );

    /** Limite inferior da faixa (exclusivo) */
    private final double min;

    /** Limite superior da faixa (inclusivo) */
    private final double max;

    private final NivelAlerta nivelAlerta;

    /** Texto curto para exibição (ex: "Fadiga excessiva") */
    private final String interpretacao;

    /** Status geral do atleta (ex: "FADIGA CRÍTICA") */
    private final String status;

    /** Recomendação de treino baseada nesta faixa */
    private final String recomendacao;

    /**
     * Classifica um valor de TSB na faixa correspondente.
     *
     * <p>Lógica: min (exclusivo) < tsb <= max (inclusivo).
     * Exceção: FADIGA_EXCESSIVA aceita qualquer valor abaixo de TSB_CRITICO.
     *
     * @param tsb valor de TSB (pode ser null)
     * @return faixa correspondente, ou null se tsb for null
     */
    public static FaixaTsb classificar(Double tsb) {
        if (tsb == null) return null;
        return Arrays.stream(values())
                .filter(f -> tsb > f.min && tsb <= f.max)
                .findFirst()
                .orElse(FORMA_IDEAL); // fallback seguro
    }

    public boolean isFormaIdeal() {
        return this == FORMA_IDEAL;
    }

    public boolean isFadigaCritica() {
        return this == FADIGA_EXCESSIVA || this == FADIGA_ALTA;
    }
}
