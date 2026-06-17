package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resumo "hoje" do atleta: próximo treino planejado + métricas-chave do dia.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resumo do dia para o shell do atleta")
public record AtletaHomeDto(

        @Schema(description = "Próximo treino planejado; omitido quando não há")
        ProximoTreino proximoTreino,

        @Schema(description = "Métricas-chave atuais do atleta")
        MetricasChave metricasChave
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Próximo treino planejado")
    public record ProximoTreino(

            @Schema(description = "Data do treino", example = "2026-06-18")
            LocalDate data,

            @Schema(description = "Tipo do treino", example = "INTERVALADO")
            String tipoTreino,

            @Schema(description = "Descrição/objetivo do treino")
            String descricao
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Métricas-chave do dia (último ponto PMC)")
    public record MetricasChave(

            @Schema(description = "CTL atual (fitness)", example = "52.3")
            Double ctl,

            @Schema(description = "ATL atual (fadiga)", example = "44.0")
            Double atl,

            @Schema(description = "TSB atual (forma)", example = "8.3")
            Double tsb,

            @Schema(description = "TSS do dia", example = "0")
            Integer tss,

            @Schema(description = "Volume do dia (km)", example = "12.5")
            BigDecimal volumeKm
    ) {}
}
