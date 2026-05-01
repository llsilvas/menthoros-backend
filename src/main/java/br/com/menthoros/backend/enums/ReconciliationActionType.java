package br.com.menthoros.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Ações de reconciliação entre atividade realizada e treino planejado.
 *
 * Define o tipo de operação realizada para reconciliar uma atividade
 * com treino planejado, incluindo ações automáticas e manuais do coach.
 */
@Getter
@RequiredArgsConstructor
public enum ReconciliationActionType {

    RECONCILIACAO_AUTOMATICA("Auto-reconciliation via scoring engine"),
    VINCULAR_MANUALMENTE("Coach manually linked planned to completed"),
    MARCAR_NAO_PLANEJADO("Coach confirmed activity was unplanned"),
    DESFAZER_VINCULO("Coach removed incorrect link"),
    REPROCESSAMENTO("Automatic reprocessing on demand");

    private final String description;

    public String getDescription() {
        return description;
    }
}
