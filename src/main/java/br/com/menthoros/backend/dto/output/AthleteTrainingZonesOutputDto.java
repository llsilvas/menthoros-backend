package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Zonas de treino do atleta (pace e FC) para o treinador, com as âncoras que as originam")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AthleteTrainingZonesOutputDto(

        @Schema(description = "Modelo de zonas de FC", example = "LTHR_FRIEL")
        String modelo,

        @Schema(description = "Limiares que originam as zonas")
        Ancoras ancoras,

        @Schema(description = "Ajuste de pace por fadiga de hoje — informativo, não está somado às faixas")
        AjusteFadiga ajusteFadiga,

        @Schema(description = "FC máxima usada pelo push (o literal acima dela é descartado)")
        FcMaxima fcMaxima,

        @Schema(description = "Quais eixos têm faixa calculável")
        Disponibilidade disponibilidade,

        @Schema(description = "Z1 a Z6; faixas de pace são as base (as mesmas do prompt da IA)")
        List<ZonaDto> zonas
) {

    public record Ancoras(
            @Schema(description = "Pace de limiar em minutos decimais por km", example = "5.00")
            BigDecimal paceLimiarMinKm,
            LocalDate dataUltimoTestePace,
            @Schema(description = "FC de limiar em bpm (derivada da FC máxima quando não informada)", example = "168")
            Integer fcLimiar,
            LocalDate dataUltimoTesteFc,
            @Schema(description = "true quando o limiar de FC veio do fallback etário, não de teste")
            boolean fcLimiarEstimada
    ) {}

    public record AjusteFadiga(
            @Schema(description = "Segundos por km a somar ao pace hoje (0 sem fadiga)", example = "7")
            int segundosPorKm,
            @Schema(description = "TSB de prontidão pré-treino usado no cálculo")
            Double tsbProntidao
    ) {}

    public record FcMaxima(
            int bpm,
            @Schema(description = "true quando a FC máxima veio do fallback, não de teste")
            boolean estimada
    ) {}

    public record Disponibilidade(
            boolean pace,
            boolean fc,
            @Schema(description = "SEM_PACE_LIMIAR quando o atleta não tem pace de limiar", example = "SEM_PACE_LIMIAR")
            String motivoPace
    ) {}

    public record ZonaDto(
            int numero,
            String nome,
            @Schema(description = "Faixa de pace; ausente sem pace de limiar")
            PaceFaixa pace,
            @Schema(description = "Faixa de FC; ausente em Z6")
            FcFaixa fc,
            @Schema(description = "FC_NAO_ESTABILIZA em Z6", example = "FC_NAO_ESTABILIZA")
            String motivoFc
    ) {}

    public record PaceFaixa(
            @Schema(description = "Segundos por km, limite mais rápido", example = "315")
            int minSegKm,
            @Schema(description = "Segundos por km, limite mais lento", example = "345")
            int maxSegKm
    ) {}

    public record FcFaixa(int min, int max) {}
}
