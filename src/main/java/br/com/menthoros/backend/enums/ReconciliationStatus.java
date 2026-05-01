package br.com.menthoros.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReconciliationStatus {

    VINCULADO_AUTOMATICO("Linked automatically via scoring"),
    AMBIGUO("Multiple candidates with similar scores"),
    NAO_PLANEJADO("No matching planned workout found"),
    VINCULADO_MANUAL("Manually linked by coach"),
    PENDENTE("Awaiting reconciliation decision");

    private final String description;

    public String getDescription() {
        return description;
    }
}
