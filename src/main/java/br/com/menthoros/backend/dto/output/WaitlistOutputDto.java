package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.WaitlistSegment;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resultado da inscrição na waitlist")
public record WaitlistOutputDto(

        @Schema(description = "Situação da inscrição", example = "CRIADO")
        String status,

        @Schema(description = "Mensagem amigável para exibição", example = "Você está na lista.")
        String mensagem,

        @Schema(description = "Segmento derivado do perfil e da marca de relógio", example = "QUALIFIED")
        WaitlistSegment segment
) {}
