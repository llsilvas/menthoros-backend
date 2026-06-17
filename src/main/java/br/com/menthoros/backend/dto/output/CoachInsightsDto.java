package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Insights agregados do coach: KPIs do tenant, tendência de carga semanal e top atletas.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Insights agregados do coach")
public record CoachInsightsDto(

        @Schema(description = "KPIs consolidados do tenant")
        Kpis kpis,

        @Schema(description = "Tendência de carga (volume/TSS) por semana no período")
        List<PontoCargaSemanal> tendenciaCargaSemanal,

        @Schema(description = "Atletas com maior volume no período")
        List<TopAtleta> topAtletas
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "KPIs do tenant")
    public record Kpis(

            @Schema(description = "Total de atletas do tenant", example = "24")
            int totalAtletas,

            @Schema(description = "Atletas com status active", example = "18")
            int ativos,

            @Schema(description = "Atletas em atenção (warning + danger)", example = "5")
            int emAtencao,

            @Schema(description = "Atletas pausados (inativos)", example = "1")
            int pausados,

            @Schema(description = "Treinos planejados na semana atual", example = "96")
            int treinosPlanejadosSemana
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Carga agregada de uma semana")
    public record PontoCargaSemanal(

            @Schema(description = "Semana ISO (ex.: 2026-W25)", example = "2026-W25")
            String semana,

            @Schema(description = "Volume total realizado (km) no tenant", example = "412.0")
            double volumeTotalKm,

            @Schema(description = "TSS total realizado no tenant", example = "3120")
            int tssTotal
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Atleta no ranking de volume do período")
    public record TopAtleta(

            @Schema(description = "ID do atleta")
            UUID atletaId,

            @Schema(description = "Nome do atleta", example = "Ana Silva")
            String nome,

            @Schema(description = "Volume realizado no período (km)", example = "180.5")
            double volumeKm
    ) {}
}
