package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Status da integração e sincronização com Strava")
public record StravaSyncStatusDto(
    @Schema(description = "Indica se a integração está ativa") boolean connected,
    @Schema(description = "Indica se uma sincronização está em progresso") boolean syncing,
    @Schema(description = "Total de atividades importadas na última sync") int imported,
    @Schema(description = "Erro da última sincronização, se houver") String lastError,
    @Schema(description = "Timestamp da última sincronização") Instant lastSync,
    @Schema(description = "ID do atleta na plataforma Strava") String externalAthleteId
) {}
