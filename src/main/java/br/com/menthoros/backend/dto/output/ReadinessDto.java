package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Readiness atual do atleta.
 *
 * <p>Quando o atleta já registrou um check-in de prontidão hoje, {@code score}/{@code classificacao}
 * refletem esse check-in (fonte única de verdade). Caso contrário, degradam para uma heurística
 * objetiva (TSB de prontidão + CTL/ATL + RPE do último treino). {@code score} pode ser {@code null}
 * quando não há nenhum sinal disponível (degrada sem erro).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Readiness atual do atleta (score + fatores objetivos)")
public record ReadinessDto(

        @Schema(description = "Score de prontidão 0–100; null quando sem sinais suficientes", example = "72")
        Integer score,

        @Schema(description = "Classificação textual do score — PRONTO/CAUTELOSO/DESCANSAR quando "
                + "vem do check-in de hoje, ou OTIMO/BOM/MODERADO/BAIXO/INDISPONIVEL quando vem da "
                + "heurística objetiva (os dois vocabulários não se sobrepõem)", example = "BOM")
        String classificacao,

        @Schema(description = "Fatores objetivos que compõem o score")
        Fatores fatores,

        @Schema(description = "Nota sobre a origem/limitação do cálculo",
                example = "Baseado em TSB de prontidão e carga — faça seu check-in de hoje para um sinal mais preciso.")
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
