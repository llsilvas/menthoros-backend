package br.com.menthoros.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Por que uma prova está pendente de ciência do coach (gravado em {@code tb_prova.motivo_revisao}).
 */
@Getter
@RequiredArgsConstructor
public enum MotivoRevisaoProva {

    NOVA("prova nova"),
    DATA_ALTERADA("data ou distância alterada"),
    ALVO_TROCADA("alvo trocada"),
    CANCELADA("prova cancelada");

    private final String label;
}
