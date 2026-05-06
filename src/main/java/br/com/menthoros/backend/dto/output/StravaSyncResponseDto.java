package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resultado de sincronização de atividades Strava")
public record StravaSyncResponseDto(
    @Schema(description = "Número de atividades importadas") int imported,
    @Schema(description = "Mensagem descritiva do resultado") String message
) {}
