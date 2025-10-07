package com.menthoros.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Níveis de criticidade dos alertas de métricas de treino
 */
@Getter
@RequiredArgsConstructor
public enum NivelAlerta {

    CRITICO("Crítico", "🔴", 4),
    ALTO("Alto", "⚠️", 3),
    ATENCAO("Atenção", "🟡", 2),
    INFO("Informativo", "ℹ️", 1);

    private final String label;
    private final String emoji;
    private final int prioridade;

    /**
     * Verifica se o alerta requer ação imediata
     */
    public boolean requerAcaoImediata() {
        return this == CRITICO || this == ALTO;
    }

    /**
     * Retorna descrição formatada com emoji
     */
    public String getLabelComEmoji() {
        return emoji + " " + label;
    }
}