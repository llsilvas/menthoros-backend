package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.AnaliseStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Análise pós-treino na visão do ATLETA (analise-ia-treino-atleta, D4). Deliberadamente NÃO
 * expõe os campos do coach ({@code technicalInterpretation}, {@code primaryCause},
 * {@code executionScore}, {@code tags}, {@code rationale}) — o atleta recebe só o bloco escrito
 * para ele e os números executado vs. planejado.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Análise pós-treino em linguagem de atleta")
public record AthleteWorkoutAnalysisOutputDto(
        @Schema(description = "PENDING enquanto a análise processa (inclui a janela antes da linha existir); COMPLETED com os textos")
        AnaliseStatus status,

        @Schema(description = "Quando a análise foi concluída", nullable = true)
        Instant analyzedAt,

        @Schema(description = "Algo concreto que o atleta fez bem", nullable = true)
        String reconhecimento,

        @Schema(description = "Como foi, executado vs. planejado, sem jargão", nullable = true)
        String comoFoi,

        @Schema(description = "O que o RPE informado diz, comparado ao esperado", nullable = true)
        String esforco,

        @Schema(description = "Dica prática para o próximo treino — nunca altera o plano", nullable = true)
        String proximoTreino,

        @Schema(description = "Números do treino realizado")
        Executado executado,

        @Schema(description = "Números do treino planejado vinculado; ausente quando o realizado não tem planejado", nullable = true)
        Planejado planejado
) {

    @Schema(description = "Números do treino realizado")
    public record Executado(
            @Schema(description = "Duração em minutos inteiros", nullable = true) Long duracaoMin,
            @Schema(nullable = true) BigDecimal distanciaKm,
            @Schema(description = "RPE informado (1–10)", nullable = true) Integer rpe
    ) {}

    @Schema(description = "Números do treino planejado")
    public record Planejado(
            @Schema(description = "Duração planejada em minutos inteiros", nullable = true) Long duracaoMin,
            @Schema(nullable = true) BigDecimal distanciaKm,
            @Schema(description = "RPE esperado (1–10)", nullable = true) Integer rpeEsperado
    ) {}
}
