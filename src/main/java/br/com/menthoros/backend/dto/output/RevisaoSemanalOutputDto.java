package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.RecommendationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Saída da revisão semanal do coach (Fatia 1 — determinística).
 *
 * <p>Campos congelados vêm da {@code RevisaoSemanal}; janela e {@code tsbFim} vêm do
 * {@code PlanoSemanal} associado. {@code weekOverWeekDelta} é computado na leitura (CA9);
 * {@code nextWeekFocus} entra na Fatia 2.
 */
@Schema(description = "Revisão semanal congelada de um atleta, com comparação vs. a semana anterior")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RevisaoSemanalOutputDto(
        @Schema(description = "ID do PlanoSemanal a que a revisão está ancorada (1:1)")
        UUID planoSemanalId,

        @Schema(description = "Início da semana revisada (segunda-feira)")
        LocalDate semanaInicio,

        @Schema(description = "Fim da semana revisada (domingo)")
        LocalDate semanaFim,

        @Schema(description = "Recomendação de foco para a próxima semana", example = "MAINTAIN")
        RecommendationType recommendationType,

        @Schema(description = "Nível de aderência por contagem na janela do plano", example = "MEDIA")
        NivelAderencia adherenceStatus,

        @Schema(description = "% de treinos realizados/planejados na janela (0–100)", example = "75.00")
        BigDecimal percentualRealizacao,

        @Schema(description = "TSB ao fim da semana (do PlanoSemanal)", example = "-5.00")
        BigDecimal tsbFim,

        @Schema(description = "Se a semana teve dado suficiente para conclusão forte")
        boolean dadosSuficientes,

        @Schema(description = "Comparação com a revisão anterior (computada na leitura)")
        WeekOverWeekDelta weekOverWeekDelta,

        @Schema(description = "Instante em que a revisão foi congelada no encerramento")
        Instant geradaEm
) {}
