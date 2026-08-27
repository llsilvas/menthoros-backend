package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
            String descricao,
            @Schema(description = "Duração planejada em minutos inteiros; omitida quando não prescrita", example = "45")
            Integer duracaoMin,
            @Schema(description = "Zona alvo declarada no treino; omitida quando ausente", example = "Z2")
            String zonaAlvo,
            @Schema(description = "TSS planejado; omitido quando ausente", example = "70")
            Integer tssPlanejado,
            @Schema(description = "IF planejado; omitido quando ausente", example = "0.95")
            Double intensidadePlanejada,
            @Schema(description = "Etapas do treino (mesmo DTO do detalhe do coach); omitidas quando o treino não tem etapas")
            List<EtapaTreinoDto> etapas
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
            BigDecimal volumeKm,

            @Schema(description = "Faixa de forma (FaixaTsb) resolvida pelo backend a partir do TSB; null quando tsb ausente", example = "FORMA_IDEAL")
            String statusForma
    ) {}
}
