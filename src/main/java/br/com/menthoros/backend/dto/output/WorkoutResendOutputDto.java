package br.com.menthoros.backend.dto.output;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado do reenvio manual dos treinos de um plano ao canal (Garmin ou intervals.icu)")
public record WorkoutResendOutputDto(

        @Schema(description = "Treinos em erro reiniciados e reenviados; 0 quando nada estava em erro", example = "4")
        int resent
) {}
