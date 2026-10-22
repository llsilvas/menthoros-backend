package br.com.menthoros.backend.dto.input;

import jakarta.validation.constraints.Size;

/**
 * Corpo opcional de {@code POST .../sugestoes/{id}/rejeitar} (add-coach-suggestion-decision-audit).
 * Ausente ou {@code rejectionReason} ausente é válido — rejeitar sem motivo continua permitido.
 */
public record RejeitarSugestaoRequestDto(
        @Size(max = 500, message = "rejectionReason deve ter no máximo 500 caracteres") String rejectionReason
) {}
