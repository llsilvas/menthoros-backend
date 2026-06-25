package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Resumo de um atleta no roster do coach.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Atleta no roster do coach, com métricas e status")
public record CoachAtletaResumoDto(

        @Schema(description = "ID do atleta")
        UUID atletaId,

        @Schema(description = "Nome do atleta", example = "Ana Silva")
        String nome,

        @Schema(description = "CTL atual (fitness); null quando sem métricas", example = "52.3")
        Double ctl,

        @Schema(description = "ATL atual (fadiga); null quando sem métricas", example = "44.0")
        Double atl,

        @Schema(description = "TSB atual (forma); null quando sem métricas", example = "8.3")
        Double tsb,

        @Schema(description = "Fase de periodização atual; null quando sem plano", example = "BUILD")
        String fase,

        @Schema(description = "Status de atenção do coach: active, warning, danger, paused", example = "warning")
        String status,

        @Schema(description = "Data do último treino realizado; null se nenhum", example = "2026-06-15")
        LocalDate lastActivity,

        @Schema(description = "Volume realizado na semana atual (km)", example = "32.5")
        BigDecimal weeklyVolume,

        @Schema(description = "Percentual de aderência ao plano nas últimas 4 semanas (0–100); null quando sem plano", example = "78")
        Integer aderenciaPercentual
) {}
