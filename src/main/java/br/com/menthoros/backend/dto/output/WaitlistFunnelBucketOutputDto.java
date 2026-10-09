package br.com.menthoros.backend.dto.output;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Um grupo do funil de waitlist por origem — a dupla {@code (utmSource, utmContent)} identifica um
 * post/anúncio específico, mais granular que só {@code utmSource}. {@code null} nos dois campos é
 * o grupo "sem UTM" (acesso direto, sem rastreamento).
 */
@Schema(description = "Funil de inscrições agrupado por origem (UTM)")
public record WaitlistFunnelBucketOutputDto(
        @Schema(description = "utm_source da inscrição, ou null para acesso direto", example = "instagram") String utmSource,
        @Schema(description = "utm_content da inscrição, ou null quando ausente", example = "bio-link") String utmContent,
        @Schema(description = "Total de inscrições no grupo", example = "12") int total,
        @Schema(description = "Perfil TREINADOR — aproximação de 'qualificado' até BE-01 existir", example = "9") int qualified,
        @Schema(description = "Tem convite de turma fundadora (aberto ou convertido)", example = "3") int invited,
        @Schema(description = "Convite convertido em assessoria", example = "1") int active
) {
}
