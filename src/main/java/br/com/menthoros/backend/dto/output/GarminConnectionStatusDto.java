package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Status da conexão Garmin do atleta — nunca contém token nem o userId completo")
public record GarminConnectionStatusDto(
        @Schema(description = "Conexão ativa") boolean ativo,
        @Schema(description = "userId Garmin mascarado", example = "d331…4df8") String userId,
        @Schema(description = "Atleta autorizou o envio de treinos ao relógio (WORKOUT_IMPORT)") boolean workoutImport,
        @Schema(description = "Atleta autorizou a leitura das atividades (ACTIVITY_EXPORT)") boolean activityExport,
        @Schema(description = "Última sincronização bem-sucedida") Instant ultimaSincronizacao,
        @Schema(description = "Último erro de autenticação/sincronização, legível") String lastSyncError
) {}
