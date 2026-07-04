package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.MotivoKudos;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Dados de entrada para o coach reconhecer (dar kudo a) um atleta")
public record KudosInputDto(

        @Schema(description = "Motivo do reconhecimento", example = "CONSISTENCIA")
        @NotNull(message = "Motivo é obrigatório")
        MotivoKudos motivo
) {}
