package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.skills.race.enums.CtlTrend;
import br.com.menthoros.backend.skills.race.enums.GapAssessment;
import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Snapshot de projeção de tempo de prova (visão do coach)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RaceProjectionSnapshotOutputDto(

    @Schema(description = "ID do snapshot") UUID id,
    @Schema(description = "ID do atleta") UUID atletaId,
    @Schema(description = "ID da prova associada (nullable)") UUID provaId,
    @Schema(description = "Data e hora de geração") Instant generatedAt,
    @Schema(description = "Semanas até a prova no momento da geração") Integer weeksToRaceAtGeneration,
    @Schema(description = "Confiança global da projeção") ProjectionConfidence confidence,
    @Schema(description = "Se é a projeção oficial para a prova") boolean isOfficial,
    @Schema(description = "Data e hora de revisão pelo coach") Instant coachReviewedAt,

    @Schema(description = "Projeções por distância (chave: metros)") Map<Integer, ProjectionDto> projections,
    @Schema(description = "Narrativa de progressão gerada pelo LLM") String progressionNarrative,
    @Schema(description = "Premissas-chave consideradas na projeção") List<String> keyAssumptions,
    @Schema(description = "Nota do coach gerada pelo LLM") String coachNote,
    @Schema(description = "Previsão de CTL para o dia da prova") CtlForecastDto ctlForecast,
    @Schema(description = "Análise de gap entre projeção e meta do coach (nullable)") GoalGapDto goalGapAnalysis,

    @Schema(description = "R² da regressão de pace (auditoria)") BigDecimal regressionRSquared,
    @Schema(description = "Expoente de Riegel utilizado (auditoria)") BigDecimal riegelExponentUsed,
    @Schema(description = "Se Riegel foi calibrado com histórico de provas") boolean riegelCalibrated,
    @Schema(description = "Semanas de treino utilizadas na análise") Integer trainingWeeksUsed

) {

    @Schema(description = "Projeção para uma distância específica")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProjectionDto(
        @Schema(description = "Distância em metros") int distanceM,
        @Schema(description = "Tempo projetado em segundos") long projectedTimeSeconds,
        @Schema(description = "Pace projetado em seg/km") int projectedPaceSecPerKm,
        @Schema(description = "Tempo otimista em segundos (P97)") long timeRangeOptimisticSec,
        @Schema(description = "Tempo conservador em segundos (P103)") long timeRangeConservativeSec,
        @Schema(description = "Confiança para esta distância") ProjectionConfidence confidence,
        @Schema(description = "Se há potencial de PR") boolean prPotential
    ) {}

    @Schema(description = "Previsão de CTL para o dia da prova")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CtlForecastDto(
        @Schema(description = "CTL atual") double currentCtl,
        @Schema(description = "CTL projetado no dia da prova") double projectedCtlRaceDay,
        @Schema(description = "Tendência do CTL") CtlTrend ctlTrend,
        @Schema(description = "Semanas até o pico (nullable)") Integer weeksToPeak
    ) {}

    @Schema(description = "Análise de gap entre projeção e meta definida pelo coach")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GoalGapDto(
        @Schema(description = "Tempo-meta em segundos") long goalTimeSeconds,
        @Schema(description = "Tempo projetado em segundos") long projectedTimeSeconds,
        @Schema(description = "Diferença em segundos (positivo = mais lento que a meta)") long gapSeconds,
        @Schema(description = "Diferença percentual") double gapPct,
        @Schema(description = "Avaliação do gap") GapAssessment gapAssessment,
        @Schema(description = "Nota factual do coach sobre o gap") String coachNoteGap
    ) {}
}
