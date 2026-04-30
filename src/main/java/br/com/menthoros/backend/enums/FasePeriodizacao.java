package br.com.menthoros.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Fases de periodização do treino baseadas na proximidade da prova alvo
 */
@Getter
@RequiredArgsConstructor
public enum FasePeriodizacao {

    BASE(
        "Fase Base",
        "Construir volume aeróbico. 80% treinos fáceis, 20% moderados.",
        "Construir base aeróbica com volume progressivo. Foco em Z2."
    ),

    BUILD(
        "Fase Build",
        "Adicionar qualidade. 70% fáceis, 20% específicos, 10% intensos.",
        "Introduzir treinos de qualidade (tempo run, intervalados Z3-Z4)."
    ),

    ESPECIFICO(
        "Fase Específico",
        "Treinos no pace de prova. 60% fáceis, 30% específicos, 10% regenerativos.",
        "Treinos no pace de prova. Simulações parciais da distância."
    ),

    TAPER(
        "Taper",
        "Reduzir volume 40-60%, manter intensidade. Foco em recuperação.",
        "Reduzir volume progressivamente. Manter intensidade em treinos curtos."
    ),

    SEMANA_PROVA(
        "Semana da Prova",
        "Apenas treinos leves curtíssimos. TSB deve estar entre +5 e +10.",
        "Apenas treinos leves curtíssimos. TSB deve estar +5 a +10."
    ),

    POS_PROVA(
        "Pós-Prova",
        "Recuperação ativa. Treinos regenerativos por 1-2 semanas.",
        "Recuperação ativa. Treinos regenerativos por 1-2 semanas."
    ),

    DESENVOLVIMENTO_GERAL(
        "Desenvolvimento Geral",
        "Sem prova alvo definida. Manter consistência e progressão gradual.",
        "Desenvolvimento geral. Manter consistência."
    );

    private final String label;
    private final String descricao;
    private final String foco;

    /**
     * Determina a fase baseada em semanas faltando para a prova
     */
    public static FasePeriodizacao determinarPorSemanasFaltando(int semanasFaltando) {
        if (semanasFaltando > 12) {
            return BASE;
        } else if (semanasFaltando > 8) {
            return BUILD;
        } else if (semanasFaltando > 3) {
            return ESPECIFICO;
        } else if (semanasFaltando > 1) {
            return TAPER;
        } else if (semanasFaltando == 1) {
            return SEMANA_PROVA;
        } else if (semanasFaltando == 0) {
            return SEMANA_PROVA;
        } else {
            return POS_PROVA;
        }
    }

    /**
     * Determina a fase baseada em dias faltando para a prova
     */
    public static FasePeriodizacao determinarPorDiasFaltando(int diasFaltando) {
        return determinarPorSemanasFaltando(diasFaltando / 7);
    }

    /**
     * Retorna recomendação de distribuição de treinos para a fase
     */
    public String getDistribuicaoTreinos() {
        return switch (this) {
            case BASE -> "80% fáceis (Z1-Z2), 20% moderados (Z3)";
            case BUILD -> "70% fáceis, 20% específicos, 10% intensos";
            case ESPECIFICO -> "60% fáceis, 30% específicos (pace prova), 10% regenerativos";
            case TAPER -> "Reduzir volume 40-60%, manter intensidade";
            case SEMANA_PROVA -> "Apenas regenerativos curtos (< 30min)";
            case POS_PROVA -> "100% regenerativo por 1-2 semanas";
            case DESENVOLVIMENTO_GERAL -> "80% aeróbico, 15% qualidade, 5% recuperação";
        };
    }

    /**
     * Verifica se é fase de alto volume
     */
    public boolean isFaseAltoVolume() {
        return this == BASE || this == BUILD;
    }

    /**
     * Verifica se é fase de recuperação
     */
    public boolean isFaseRecuperacao() {
        return this == TAPER || this == SEMANA_PROVA || this == POS_PROVA;
    }

    /**
     * Verifica se é fase de treinos específicos
     */
    public boolean isFaseEspecifica() {
        return this == ESPECIFICO;
    }
}