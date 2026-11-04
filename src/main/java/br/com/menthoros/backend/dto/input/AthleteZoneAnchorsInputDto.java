package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.FonteLimiar;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Limites = CHECKs de V6 ({@code ck_atleta_fc_limiar}, {@code ck_atleta_fc_maxima},
 * {@code ck_atleta_pace_limiar}). Afrouxar só aqui faria o PUT falhar no commit.
 */
@Schema(description = "Âncoras das zonas do atleta. Grupo ausente = não alterar aquele eixo; ao menos um é obrigatório.")
public record AthleteZoneAnchorsInputDto(
        @Valid Fc fc,
        @Valid Pace pace
) {

    @Schema(description = "Âncoras de FC")
    public record Fc(
            @Schema(description = "FC de limiar (LTHR) em bpm", example = "168", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "FC de limiar é obrigatória")
            @Min(value = 100, message = "FC de limiar deve estar entre 100 e 200")
            @Max(value = 200, message = "FC de limiar deve estar entre 100 e 200")
            Integer limiar,

            @Schema(description = "FC máxima em bpm. Vazia = manter a vigente (a estimativa nunca vira valor informado)", example = "186")
            @Min(value = 120, message = "FC máxima deve estar entre 120 e 220")
            @Max(value = 220, message = "FC máxima deve estar entre 120 e 220")
            Integer maxima,

            @Schema(description = "Data do teste", example = "2026-10-12", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "Data do teste é obrigatória")
            LocalDate dataTeste,

            @Schema(description = "Origem do limiar", example = "TESTE_CAMPO")
            FonteLimiar origem
    ) {}

    @Schema(description = "Âncoras de pace")
    public record Pace(
            @Schema(description = "Pace de limiar em segundos por km (2:30 a 12:00/km)", example = "290", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "Pace de limiar é obrigatório")
            @Min(value = 150, message = "Pace de limiar deve estar entre 150 e 720 s/km")
            @Max(value = 720, message = "Pace de limiar deve estar entre 150 e 720 s/km")
            Integer limiarSegKm,

            @Schema(description = "Data do teste", example = "2026-10-12", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "Data do teste é obrigatória")
            LocalDate dataTeste,

            @Schema(description = "Origem do limiar", example = "TESTE_CAMPO")
            FonteLimiar origem
    ) {}
}
