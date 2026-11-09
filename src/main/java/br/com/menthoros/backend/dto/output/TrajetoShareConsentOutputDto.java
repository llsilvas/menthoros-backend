package br.com.menthoros.backend.dto.output;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Estado do consentimento do atleta para o trajeto GPS aparecer nos cards de compartilhamento")
public record TrajetoShareConsentOutputDto(
        @Schema(description = "Verdadeiro só se o último evento for concessão na versão vigente da Política", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean granted,
        @Schema(description = "Versão vigente da Política (YYYY-MM-DD) — a que o aviso deve apresentar", requiredMode = Schema.RequiredMode.REQUIRED)
        String policyVersion,
        @Schema(description = "Momento do último evento; ausente se o atleta nunca respondeu")
        Instant registradoEm
) {
}
