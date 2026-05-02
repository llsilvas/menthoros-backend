package br.com.menthoros.backend.service;

import br.com.menthoros.backend.dto.MatchingCandidate;
import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Matching Engine Tests")
class MatchingEngineTest {

    private MatchingScoreCalculator scoreCalculator;
    private MatchingDecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        scoreCalculator = new MatchingScoreCalculator();
        decisionEngine = new MatchingDecisionEngine();
    }

    // ===== MatchingScoreCalculator Tests =====

    @Test
    @DisplayName("Should calculate perfect match (same date, same duration, same distance)")
    void testPerfectMatch() {
        TreinoRealizado realizado = createRealizado(
                LocalDate.of(2026, 5, 1),
                Duration.ofMinutes(60),
                new BigDecimal("10.0")
        );

        TreinoPlanejado planejado = createPlanejado(
                LocalDate.of(2026, 5, 1),
                Duration.ofMinutes(60),
                new BigDecimal("10.0")
        );

        MatchingScoreResult result = scoreCalculator.calculate(realizado, planejado, null);

        assertThat(result.getOverallScore()).isGreaterThan(new BigDecimal("0.95"));
        assertThat(result.getTemporalScore()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.getDurationScore()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.getDistanceScore()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("Should penalize different dates (±1 day)")
    void testTemporalPenalty() {
        TreinoRealizado realizado = createRealizado(LocalDate.of(2026, 5, 1), null, null);
        TreinoPlanejado planejado = createPlanejado(LocalDate.of(2026, 5, 2), null, null);

        MatchingScoreResult result = scoreCalculator.calculate(realizado, planejado, null);

        assertThat(result.getTemporalScore()).isEqualByComparingTo(new BigDecimal("0.75"));
    }

    @Test
    @DisplayName("Should reject different dates (>2 days)")
    void testTemporalRejection() {
        TreinoRealizado realizado = createRealizado(LocalDate.of(2026, 5, 1), null, null);
        TreinoPlanejado planejado = createPlanejado(LocalDate.of(2026, 5, 4), null, null);

        MatchingScoreResult result = scoreCalculator.calculate(realizado, planejado, null);

        assertThat(result.getTemporalScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should handle duration within tolerance (±20%)")
    void testDurationTolerance() {
        TreinoRealizado realizado = createRealizado(
                LocalDate.of(2026, 5, 1),
                Duration.ofMinutes(60),
                null
        );

        TreinoPlanejado planejado = createPlanejado(
                LocalDate.of(2026, 5, 1),
                Duration.ofMinutes(70),
                null
        );

        MatchingScoreResult result = scoreCalculator.calculate(realizado, planejado, null);

        assertThat(result.getDurationScore()).isGreaterThanOrEqualTo(new BigDecimal("0.90"));
    }

    @Test
    @DisplayName("Should reject duration outside tolerance (>50%)")
    void testDurationRejection() {
        TreinoRealizado realizado = createRealizado(
                LocalDate.of(2026, 5, 1),
                Duration.ofMinutes(60),
                null
        );

        TreinoPlanejado planejado = createPlanejado(
                LocalDate.of(2026, 5, 1),
                Duration.ofMinutes(150),
                null
        );

        MatchingScoreResult result = scoreCalculator.calculate(realizado, planejado, null);

        assertThat(result.getDurationScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should handle distance within tolerance (±20%)")
    void testDistanceTolerance() {
        TreinoRealizado realizado = createRealizado(
                LocalDate.of(2026, 5, 1),
                null,
                new BigDecimal("10.0")
        );

        TreinoPlanejado planejado = createPlanejado(
                LocalDate.of(2026, 5, 1),
                null,
                new BigDecimal("11.0")
        );

        MatchingScoreResult result = scoreCalculator.calculate(realizado, planejado, null);

        assertThat(result.getDistanceScore()).isGreaterThanOrEqualTo(new BigDecimal("0.90"));
    }

    @Test
    @DisplayName("Should handle null duration gracefully")
    void testNullDuration() {
        TreinoRealizado realizado = createRealizado(LocalDate.of(2026, 5, 1), null, null);
        TreinoPlanejado planejado = createPlanejado(LocalDate.of(2026, 5, 1), null, null);

        MatchingScoreResult result = scoreCalculator.calculate(realizado, planejado, null);

        assertThat(result.getDurationScore()).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    @DisplayName("Should handle null distance gracefully")
    void testNullDistance() {
        TreinoRealizado realizado = createRealizado(LocalDate.of(2026, 5, 1), null, null);
        TreinoPlanejado planejado = createPlanejado(LocalDate.of(2026, 5, 1), null, null);

        MatchingScoreResult result = scoreCalculator.calculate(realizado, planejado, null);

        assertThat(result.getDistanceScore()).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    // ===== MatchingDecisionEngine Tests =====

    @Test
    @DisplayName("Should auto-match when score >= 0.80")
    void testAutoMatch() {
        TreinoRealizado realizado = createRealizado(LocalDate.of(2026, 5, 1), null, null);
        TreinoPlanejado candidate = createPlanejado(LocalDate.of(2026, 5, 1), null, null);

        MatchingScoreResult score = new MatchingScoreResult(
                new BigDecimal("0.85"),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
        );

        List<MatchingCandidate> candidates = List.of(
                new MatchingCandidate(candidate, score, 1)
        );

        MatchingDecision decision = decisionEngine.decide(realizado, candidates);

        assertThat(decision.getStatus()).isEqualTo(ReconciliationStatus.VINCULADO_AUTOMATICO);
        assertThat(decision.getSelectedPlanned()).isEqualTo(candidate);
        assertThat(decision.getReasonCode()).isEqualTo("AUTO_MATCHED");
    }

    @Test
    @DisplayName("Should mark ambiguous when 0.50 <= score < 0.80")
    void testAmbiguous() {
        TreinoRealizado realizado = createRealizado(LocalDate.of(2026, 5, 1), null, null);
        TreinoPlanejado candidate = createPlanejado(LocalDate.of(2026, 5, 1), null, null);

        MatchingScoreResult score = new MatchingScoreResult(
                new BigDecimal("0.65"),
                BigDecimal.ONE,
                new BigDecimal("0.50"),
                new BigDecimal("0.50")
        );

        List<MatchingCandidate> candidates = List.of(
                new MatchingCandidate(candidate, score, 1)
        );

        MatchingDecision decision = decisionEngine.decide(realizado, candidates);

        assertThat(decision.getStatus()).isEqualTo(ReconciliationStatus.AMBIGUO);
        assertThat(decision.getSelectedPlanned()).isNull();
        assertThat(decision.getReasonCode()).isEqualTo("LOW_CONFIDENCE");
    }

    @Test
    @DisplayName("Should mark not planned when score < 0.50")
    void testNotPlanned() {
        TreinoRealizado realizado = createRealizado(LocalDate.of(2026, 5, 1), null, null);
        TreinoPlanejado candidate = createPlanejado(LocalDate.of(2026, 5, 1), null, null);

        MatchingScoreResult score = new MatchingScoreResult(
                new BigDecimal("0.40"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        List<MatchingCandidate> candidates = List.of(
                new MatchingCandidate(candidate, score, 1)
        );

        MatchingDecision decision = decisionEngine.decide(realizado, candidates);

        assertThat(decision.getStatus()).isEqualTo(ReconciliationStatus.NAO_PLANEJADO);
        assertThat(decision.getSelectedPlanned()).isNull();
        assertThat(decision.getReasonCode()).isEqualTo("SCORE_TOO_LOW");
    }

    @Test
    @DisplayName("Should detect tie when delta < 0.10")
    void testTieDetection() {
        TreinoRealizado realizado = createRealizado(LocalDate.of(2026, 5, 1), null, null);
        TreinoPlanejado candidate1 = createPlanejado(LocalDate.of(2026, 5, 1), null, null);
        TreinoPlanejado candidate2 = createPlanejado(LocalDate.of(2026, 5, 1), null, null);

        MatchingScoreResult score1 = new MatchingScoreResult(
                new BigDecimal("0.85"),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
        );

        MatchingScoreResult score2 = new MatchingScoreResult(
                new BigDecimal("0.80"),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
        );

        List<MatchingCandidate> candidates = List.of(
                new MatchingCandidate(candidate1, score1, 1),
                new MatchingCandidate(candidate2, score2, 2)
        );

        MatchingDecision decision = decisionEngine.decide(realizado, candidates);

        assertThat(decision.getStatus()).isEqualTo(ReconciliationStatus.AMBIGUO);
        assertThat(decision.getSelectedPlanned()).isNull();
        assertThat(decision.getReasonCode()).isEqualTo("TIE_DETECTED");
    }

    @Test
    @DisplayName("Should handle empty candidates list")
    void testEmptyCandidates() {
        TreinoRealizado realizado = createRealizado(LocalDate.of(2026, 5, 1), null, null);

        MatchingDecision decision = decisionEngine.decide(realizado, new ArrayList<>());

        assertThat(decision.getStatus()).isEqualTo(ReconciliationStatus.NAO_PLANEJADO);
        assertThat(decision.getSelectedPlanned()).isNull();
        assertThat(decision.getReasonCode()).isEqualTo("NO_CANDIDATES");
    }

    @Test
    @DisplayName("Should select highest score among multiple candidates")
    void testSelectBestCandidate() {
        TreinoRealizado realizado = createRealizado(LocalDate.of(2026, 5, 1), null, null);
        TreinoPlanejado candidate1 = createPlanejado(LocalDate.of(2026, 5, 1), null, null);
        TreinoPlanejado candidate2 = createPlanejado(LocalDate.of(2026, 5, 2), null, null);
        TreinoPlanejado candidate3 = createPlanejado(LocalDate.of(2026, 5, 3), null, null);

        MatchingScoreResult score1 = new MatchingScoreResult(
                new BigDecimal("0.70"), BigDecimal.ONE, new BigDecimal("0.70"), new BigDecimal("0.70")
        );

        MatchingScoreResult score2 = new MatchingScoreResult(
                new BigDecimal("0.85"), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE
        );

        MatchingScoreResult score3 = new MatchingScoreResult(
                new BigDecimal("0.60"), new BigDecimal("0.50"), BigDecimal.ONE, BigDecimal.ONE
        );

        List<MatchingCandidate> candidates = List.of(
                new MatchingCandidate(candidate1, score1, 1),
                new MatchingCandidate(candidate2, score2, 2),
                new MatchingCandidate(candidate3, score3, 3)
        );

        MatchingDecision decision = decisionEngine.decide(realizado, candidates);

        assertThat(decision.getStatus()).isEqualTo(ReconciliationStatus.VINCULADO_AUTOMATICO);
        assertThat(decision.getSelectedPlanned()).isEqualTo(candidate2);
    }

    // ===== Helper Methods =====

    private TreinoRealizado createRealizado(LocalDate data, Duration duracao, BigDecimal distancia) {
        TreinoRealizado realizado = new TreinoRealizado();
        realizado.setDataTreino(data);
        realizado.setDuracaoMin(duracao);
        realizado.setDistanciaKm(distancia);
        realizado.setTenantId(UUID.randomUUID());
        return realizado;
    }

    private TreinoPlanejado createPlanejado(LocalDate data, Duration duracao, BigDecimal distancia) {
        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setDataTreino(data);
        planejado.setDuracaoMin(duracao);
        planejado.setDistanciaKm(distancia);
        planejado.setTenantId(UUID.randomUUID());
        return planejado;
    }
}
