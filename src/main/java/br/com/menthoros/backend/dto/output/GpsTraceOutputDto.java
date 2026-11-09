package br.com.menthoros.backend.dto.output;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Trajeto GPS simplificado de um treino realizado (add-activity-gps-trace, D4). Arrays paralelos:
 * o i-ésimo ponto da polyline tem {@code tSeg[i]} e {@code fc[i]}.
 */
@Schema(description = "Trajeto GPS simplificado do treino realizado; polyline codificada no formato do Google Maps (precisão 5)")
public record GpsTraceOutputDto(
        @Schema(description = "Versão do formato", example = "1")
        int v,

        @Schema(description = "Número de pontos", example = "312")
        int n,

        @Schema(description = "Encoded polyline (Google, precisão 5) do trajeto simplificado", example = "_p~iF~ps|U_ulLnnqC")
        String polyline,

        @Schema(description = "Segundos desde o primeiro ponto, um por ponto", example = "[0, 4, 9]")
        List<Integer> tSeg,

        @Schema(description = "FC por ponto em bpm; null onde o sensor não registrou", example = "[112, 118, null]")
        List<@Nullable Integer> fc,

        @Schema(description = "Modelo do dispositivo, para a atribuição 'Garmin [modelo]'; ausente quando desconhecido", example = "Forerunner 265")
        @Nullable String deviceName
) {}
