package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Melhor tempo contínuo do atleta numa distância de referência, numa janela rolante — diferente de
 * {@link RecordeDto} (PR de treino inteiro): esta marca pode ter sido feita dentro de um treino
 * maior (ex.: o melhor 1km de uma corrida de 10km).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Melhor esforço por distância de referência, numa janela rolante")
public record MelhorEsforcoDto(

        @Schema(description = "Rótulo da distância de referência", example = "5k")
        String distanciaLabel,

        @Schema(description = "Distância real do ponto no curve, em metros", example = "5000.0")
        double distanciaMetros,

        @Schema(description = "Melhor tempo contínuo, em segundos", example = "1796")
        int tempoSegundos,

        @Schema(description = "Pace formatado (mm:ss/km)", example = "5:59/km")
        String paceLabel
) {}
