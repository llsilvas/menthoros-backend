package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Concessão do consentimento para o trajeto GPS nos cards")
public record TrajetoShareConsentInputDto(
        @Schema(description = "Versão da Política que o aviso apresentou ao atleta (YYYY-MM-DD)", example = "2026-11-09")
        @NotBlank @Size(max = 20) String policyVersion
) {
}
