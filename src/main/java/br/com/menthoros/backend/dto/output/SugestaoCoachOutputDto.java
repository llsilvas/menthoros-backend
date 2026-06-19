package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Sugestão de IA para revisão do treinador")
public record SugestaoCoachOutputDto(

        @Schema(description = "ID da sugestão")
        UUID id,

        @Schema(description = "ID do atleta")
        UUID atletaId,

        @Schema(description = "Nome do atleta", example = "Ana Silva")
        String athleteName,

        @Schema(description = "Tipo da sugestão", example = "RECOVERY")
        TipoSugestao tipo,

        @Schema(description = "Status no ciclo de revisão", example = "PENDING")
        StatusSugestao status,

        @Schema(description = "Confiança: HIGH (sinal CRITICA), MEDIUM (ALTA)", example = "HIGH")
        String confidence,

        @Schema(description = "Ação sugerida ao treinador")
        String summary,

        @Schema(description = "Explicabilidade estruturada do sinal (nullable se ausente no sinal de origem)")
        RecommendationExplanation reasoning,

        @Schema(description = "Momento de criação da sugestão")
        Instant createdAt,

        @Schema(description = "Momento da revisão (null enquanto PENDING)")
        Instant reviewedAt,

        @Schema(description = "Expiração da sugestão (null = sem expiração)")
        Instant expiresAt
) {}
