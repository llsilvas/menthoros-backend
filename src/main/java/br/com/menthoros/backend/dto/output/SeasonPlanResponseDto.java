package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.domain.planner.SeasonPlanValidator.Rule;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.enums.WeekValueSource;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Plano de temporada do atleta: semanas de segunda a domingo até a prova-alvo")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeasonPlanResponseDto(
        @Schema(description = "ID da temporada (PlanoTreino)") UUID id,
        @Schema(description = "Status da temporada", example = "PROPOSED") SeasonPlanStatus seasonStatus,
        @Schema(description = "Quando o treinador aprovou; ausente enquanto PROPOSED", example = "2026-11-20T13:00:00Z")
        Instant approvedAt,
        @Schema(description = "ID da prova-alvo") UUID provaId,
        @Schema(description = "Nome da prova-alvo", example = "Maratona de São Paulo") String provaNome,
        @Schema(description = "Data da prova-alvo", example = "2027-04-04") LocalDate provaData,
        @Schema(description = "Menos semanas que o mínimo da distância") boolean shortPreparation,
        @Schema(description = "Total de semanas", example = "20") Integer totalWeeks,
        @Schema(description = "CTL de partida usado na geração", example = "40.0") Double initialCtl,
        @Schema(description = "Maior volume semanal da temporada, em km", example = "52.0") BigDecimal peakVolumeKm,
        @Schema(description = "Semanas em ordem") List<Week> weeks,
        @Schema(description = "Provas preparatórias ancoradas como marcos, por semana; vazia se não há")
        List<Milestone> milestones,
        @Schema(description = "Alertas das validações da temporada, recalculados a cada leitura; vazia se nenhum")
        List<Validation> validations
) {

    @Schema(description = "Semana da temporada")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Week(
            @Schema(description = "Posição da semana, a partir de 1", example = "1") int weekIndex,
            @Schema(description = "Segunda-feira da semana", example = "2026-11-16") LocalDate weekStart,
            @Schema(description = "Fase", example = "BASE") TrainingPhase phase,
            @Schema(description = "Volume-alvo em km", example = "40.7") BigDecimal targetVolumeKm,
            @Schema(description = "TSS-alvo", example = "285") Double targetTss,
            @Schema(description = "Semana de descarga") boolean deload,
            @Schema(description = "Longão-alvo em km") BigDecimal longRunKm,
            @Schema(description = "Quem definiu os valores", example = "ENGINE") WeekValueSource valueSource
    ) {
    }

    @Schema(description = "Prova preparatória ancorada numa semana da temporada")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Milestone(
            @Schema(description = "Semana do marco", example = "6") int weekIndex,
            @Schema(description = "ID da prova") UUID provaId,
            @Schema(description = "Nome da prova", example = "Meia de São Paulo") String name,
            @Schema(description = "Data da prova", example = "2026-12-27") LocalDate date,
            @Schema(description = "Distância da prova", example = "KM_21") DistanciaProva distance
    ) {
    }

    @Schema(description = "Alerta brando de uma validação da temporada; não bloqueia a aprovação")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Validation(
            @Schema(description = "Regra", example = "RAMP") Rule rule,
            @Schema(description = "Severidade", example = "WARN") String severity,
            @Schema(description = "Semana do alerta", example = "2") int weekIndex,
            @Schema(description = "Valor medido, na unidade da regra (%, semanas ou p.p.)", example = "12.5") double measured,
            @Schema(description = "Limite da regra, na mesma unidade", example = "10") double limit,
            @Schema(description = "Mensagem para o treinador") String message
    ) {
    }
}
