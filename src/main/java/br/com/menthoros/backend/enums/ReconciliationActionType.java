package br.com.menthoros.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
