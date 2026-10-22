package br.com.menthoros.backend.dto.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de {@code PUT .../sugestoes/{id}} (add-coach-suggestion-edit-delta). */
public record SugestaoCoachEditInputDto(
        @NotBlank @Size(max = 500, message = "summary deve ter no máximo 500 caracteres") String summary,
        @NotNull Long versaoEsperada
) {}
