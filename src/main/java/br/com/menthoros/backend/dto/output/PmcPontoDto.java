package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Ponto diário da curva PMC (Performance Management Chart) do atleta.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Ponto diário da curva PMC (CTL/ATL/TSB/TSS)")
public record PmcPontoDto(

        @Schema(description = "Data do ponto", example = "2026-06-17")
        LocalDate data,

        @Schema(description = "Chronic Training Load (fitness)", example = "52.3")
        Double ctl,

        @Schema(description = "Acute Training Load (fadiga)", example = "60.1")
        Double atl,

        @Schema(description = "Training Stress Balance (forma) = CTL - ATL", example = "-7.8")
        Double tsb,

        @Schema(description = "Training Stress Score do dia", example = "85")
        Integer tss,

        @Schema(description = "Faixa de forma (FaixaTsb) resolvida pelo backend a partir do TSB; null quando tsb ausente", example = "FATIGADO")
        String statusForma
) {}
