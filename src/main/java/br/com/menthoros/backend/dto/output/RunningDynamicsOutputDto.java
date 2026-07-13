package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Running dynamics (import .fit) — GCT, equilíbrio, passada, oscilação, proporção vertical,
 * temperatura, tempo em movimento e calorias. Escalares simples, no fluxo comum (não restrito
 * ao detalhe do treino, ao contrário do envelope de decoupling e da série de eficiência).
 * Em {@code EtapaRealizadaOutputDto}, {@code calorias} é sempre ausente — só existe a nível de
 * sessão ({@code TreinoRealizadoOutputDto}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Running dynamics do treino/etapa (GCT, equilíbrio, passada, oscilação, proporção vertical, temperatura, tempo em movimento, calorias)")
public record RunningDynamicsOutputDto(
        @Schema(description = "Tempo em movimento (formato MM:SS ou HH:MM:SS); ausente quando o dispositivo "
                        + "não grava timer time — nesse caso é igual à duração elapsed",
                example = "29:10")
        String tempoMovimento,

        @Schema(description = "Calorias totais (kcal) — só presente a nível de sessão", example = "650")
        Integer calorias,

        @Schema(description = "Tempo médio de contato com o solo (ground contact time), em ms", example = "252")
        Integer gctMedioMs,

        @Schema(description = "Equilíbrio de GCT — % do pé esquerdo (convenção Garmin); ~50 é equilíbrio perfeito",
                example = "49.3")
        Double gctEquilibrioPct,

        @Schema(description = "Comprimento médio da passada em metros", example = "1.05")
        Double passadaMediaM,

        @Schema(description = "Oscilação vertical média em centímetros", example = "8.2")
        Double oscilacaoVerticalCm,

        @Schema(description = "Proporção vertical média (%) — oscilação/passada", example = "6.8")
        Double proporcaoVerticalPct,

        @Schema(description = "Temperatura média, em °C", example = "22.0")
        Double temperaturaMediaC
) {}
