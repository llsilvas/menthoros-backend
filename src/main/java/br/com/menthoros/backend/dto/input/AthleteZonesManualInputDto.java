package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.ModoZonas;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Validado por {@code AthleteZoneManualValidator}, não por Bean Validation (ver o controller). */
@Schema(description = "Modo das zonas por eixo. Grupo ausente = não alterar aquele eixo; ao menos um é obrigatório.")
public record AthleteZonesManualInputDto(Eixo fc, Eixo pace) {

    @Schema(description = "Modo de um eixo e, em MANUAL, as faixas")
    public record Eixo(
            @Schema(description = "LIMIAR apaga as faixas manuais do eixo", example = "MANUAL",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            ModoZonas modo,

            @ArraySchema(arraySchema = @Schema(description = "Obrigatório em MANUAL, ordenado por zona: "
                    + "FC Z1–Z5 em bpm, pace Z1–Z6 em s/km. Ignorado em LIMIAR."))
            List<Faixa> faixas
    ) {}

    @Schema(description = "Faixa de uma zona. Pace: min = limite mais rápido, max = o mais lento (s/km).")
    public record Faixa(
            @Schema(example = "2") Integer zona,
            @Schema(example = "140") Integer min,
            @Schema(example = "150") Integer max
    ) {}
}
