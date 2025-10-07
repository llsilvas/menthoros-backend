package com.menthoros.dto;

import com.menthoros.enums.NivelAlerta;

/**
 * DTO para alertas de métricas de treino
 * Usado para estruturar alertas gerados a partir de PlanoMetaDados
 */
public record AlertaMetricas(
        NivelAlerta nivel,
        String codigo,
        String mensagem,
        String recomendacao
) {

    /**
     * Verifica se o alerta requer ação imediata
     */
    public boolean requerAcaoImediata() {
        return nivel.requerAcaoImediata();
    }

    /**
     * Retorna mensagem formatada com emoji do nível
     */
    public String getMensagemFormatada() {
        return String.format("%s %s", nivel.getEmoji(), mensagem);
    }

    /**
     * Retorna mensagem completa com recomendação
     */
    public String getMensagemCompleta() {
        return String.format("%s %s\n  %s",
            nivel.getEmoji(),
            mensagem,
            recomendacao);
    }
}