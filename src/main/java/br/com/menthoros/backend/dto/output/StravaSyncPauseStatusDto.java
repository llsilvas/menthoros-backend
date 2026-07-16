package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Estado da pausa de sincronização automática do Strava para um atleta")
public record StravaSyncPauseStatusDto(
    @Schema(description = "true quando o sync automático do Strava (scheduler + webhook) está pausado para este atleta") boolean autoSyncPausado,
    @Schema(description = "Timestamp da última atualização da conexão Strava") LocalDateTime atualizadoEm
) {}
