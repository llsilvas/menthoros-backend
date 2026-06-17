package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Readiness atual do atleta.
 *
 * <p><b>Provisório:</b> enquanto a change `add-daily-readiness-checkin` não entrega o sinal
 * subjetivo (sono/estresse/prontidão), o {@code score} é derivado apenas de sinais objetivos
 * (TSB de prontidão + CTL/ATL + RPE do último treino). {@code score} pode ser {@code null}
 * quando não há sinais suficientes (degrada sem erro).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Readiness atual do atleta (score + fatores objetivos)")
public record ReadinessDto(

        @Schema(description = "Score de prontidão 0–100; null quando sem sinais suficientes", example = "72")
        Integer score,

        @Schema(description = "Classificação textual do score", example = "BOM")
        String classificacao,

        @Schema(description = "Fatores objetivos que compõem o score")
        Fatores fatores,

        @Schema(description = "Nota sobre a origem/limitação do cálculo",
                example = "Provisório: sem check-in subjetivo; baseado em TSB de prontidão e carga.")
        String nota
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Sinais objetivos usados no readiness")
    public record Fatores(

            @Schema(description = "TSB de prontidão (forma antes da carga do dia)", example = "8.5")
            Double tsbProntidao,

            @Schema(description = "CTL atual (fitness)", example = "52.3")
            Double ctl,

            @Schema(description = "ATL atual (fadiga)", example = "44.0")
            Double atl,

            @Schema(description = "RPE do último treino realizado (1–10)", example = "7")
            Integer ultimoRpe
    ) {}
}
