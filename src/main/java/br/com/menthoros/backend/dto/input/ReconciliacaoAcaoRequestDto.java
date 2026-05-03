package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.ReconciliationActionType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReconciliacaoAcaoRequestDto(
    @NotNull(message = "Action não pode ser nulo") ReconciliationActionType action,
    UUID treinoPlanejadoId,
    String reasonText
) {}
