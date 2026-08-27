package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.MotivoPulo;
import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de "Não vou conseguir hoje". Tudo opcional: pular sem dizer por quê é válido. */
@Schema(description = "Motivo opcional do pulo do treino de hoje")
public record PularTreinoInputDto(

        @Schema(description = "Motivo declarado; fora da lista é 400", example = "SEM_TEMPO")
        MotivoPulo motivo
) {}
