package br.com.menthoros.backend.service;

import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MatchingScoreCalculator Tests")
class MatchingScoreCalculatorTest {

    private MatchingScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new MatchingScoreCalculator();
    }

    @Test
    @DisplayName("deve normalizar por timezone do atleta em boundary UTC->SP")
    void testTimezoneBoundaryUtcToSP() {
        // Scenario: atividade em data UTC vs planejado em data SP (1 dia de diferença)
        // Ambas as datas são normalizadas para o timezone do atleta (SP) antes da comparação.
        // Este teste verifica que o timezone é corretamente aplicado.

        TreinoRealizado atividade = new TreinoRealizado();
        atividade.setDataTreino(LocalDate.of(2026, 5, 2));
        atividade.setDuracaoMin(Duration.ofMinutes(60));
        atividade.setDistanciaKm(new BigDecimal("10.0"));
        atividade.setTipoTreino(TipoTreino.FACIL);
        atividade.setTenantId(UUID.randomUUID());

        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setDataTreino(LocalDate.of(2026, 5, 1));
        planejado.setDuracaoMin(Duration.ofMinutes(60));
        planejado.setDistanciaKm(new BigDecimal("10.0"));
        planejado.setTipoTreino(TipoTreino.FACIL);
        planejado.setTenantId(UUID.randomUUID());

        Atleta atletaSP = new Atleta();
        atletaSP.setTimezone("America/Sao_Paulo");

        // Com timezone normalizado: datas estão 1 dia separadas → score = 0.75
        MatchingScoreResult result = calculator.calculate(atividade, planejado, atletaSP);

        assertThat(result.getTemporalScore().setScale(2, RoundingMode.HALF_UP))
                .as("Temporal score deve ser 0.75 (1 dia de diferença em SP timezone)")
                .isEqualByComparingTo(new BigDecimal("0.75"));
    }

    @Test
    @DisplayName("deve usar fallback timezone quando atleta é null")
    void testTimezoneFallbackWhenAtletaNull() {
        TreinoRealizado atividade = new TreinoRealizado();
        atividade.setDataTreino(LocalDate.of(2026, 5, 1));
        atividade.setDuracaoMin(Duration.ofMinutes(60));
        atividade.setDistanciaKm(new BigDecimal("10.0"));
        atividade.setTenantId(UUID.randomUUID());

        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setDataTreino(LocalDate.of(2026, 5, 1));
        planejado.setDuracaoMin(Duration.ofMinutes(60));
        planejado.setDistanciaKm(new BigDecimal("10.0"));
        planejado.setTenantId(UUID.randomUUID());

        // Passing null athlete should use fallback timezone
        MatchingScoreResult result = calculator.calculate(atividade, planejado, null);

        assertThat(result.getTemporalScore()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("deve usar fallback timezone quando timezone do atleta é null")
    void testTimezoneFallbackWhenAtletaTimezoneNull() {
        TreinoRealizado atividade = new TreinoRealizado();
        atividade.setDataTreino(LocalDate.of(2026, 5, 1));
        atividade.setDuracaoMin(Duration.ofMinutes(60));
        atividade.setDistanciaKm(new BigDecimal("10.0"));
        atividade.setTenantId(UUID.randomUUID());

        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setDataTreino(LocalDate.of(2026, 5, 1));
        planejado.setDuracaoMin(Duration.ofMinutes(60));
        planejado.setDistanciaKm(new BigDecimal("10.0"));
        planejado.setTenantId(UUID.randomUUID());

        Atleta atleta = new Atleta();
        atleta.setTimezone(null);

        // Should use fallback America/Sao_Paulo
        MatchingScoreResult result = calculator.calculate(atividade, planejado, atleta);

        assertThat(result.getTemporalScore()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("deve respeitar timezone customizado do atleta")
    void testCustomAthleteTimezone() {
        // Create activity and planned workout with 1 day difference
        TreinoRealizado atividade = new TreinoRealizado();
        atividade.setDataTreino(LocalDate.of(2026, 5, 2));
        atividade.setDuracaoMin(Duration.ofMinutes(60));
        atividade.setDistanciaKm(new BigDecimal("10.0"));
        atividade.setTenantId(UUID.randomUUID());

        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setDataTreino(LocalDate.of(2026, 5, 1));
        planejado.setDuracaoMin(Duration.ofMinutes(60));
        planejado.setDistanciaKm(new BigDecimal("10.0"));
        planejado.setTenantId(UUID.randomUUID());

        // Use New York timezone (UTC-4 in May)
        Atleta atletaNY = new Atleta();
        atletaNY.setTimezone("America/New_York");

        MatchingScoreResult result = calculator.calculate(atividade, planejado, atletaNY);

        // With different timezone, dates should still be 1 day apart
        assertThat(result.getTemporalScore()).isEqualByComparingTo(new BigDecimal("0.75"));
    }

    @Test
    @DisplayName("deve retornar score zero para null inputs")
    void testNullInputs() {
        Atleta atleta = new Atleta();
        atleta.setTimezone("America/Sao_Paulo");

        MatchingScoreResult result = calculator.calculate(null, null, atleta);

        assertThat(result.getOverallScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTemporalScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDurationScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDistanceScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("deve calcular score perfeito com datas e métricas idênticas")
    void testPerfectMatchWithTimezone() {
        TreinoRealizado atividade = new TreinoRealizado();
        atividade.setDataTreino(LocalDate.of(2026, 5, 1));
        atividade.setDuracaoMin(Duration.ofMinutes(60));
        atividade.setDistanciaKm(new BigDecimal("10.0"));
        atividade.setTenantId(UUID.randomUUID());

        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setDataTreino(LocalDate.of(2026, 5, 1));
        planejado.setDuracaoMin(Duration.ofMinutes(60));
        planejado.setDistanciaKm(new BigDecimal("10.0"));
        planejado.setTenantId(UUID.randomUUID());

        Atleta atleta = new Atleta();
        atleta.setTimezone("America/Sao_Paulo");

        MatchingScoreResult result = calculator.calculate(atividade, planejado, atleta);

        assertThat(result.getOverallScore()).isGreaterThan(new BigDecimal("0.95"));
        assertThat(result.getTemporalScore()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.getDurationScore()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.getDistanceScore()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("deve penalizar diferença de 1 dia com timezone normalizado")
    void testOneDayPenaltyWithTimezone() {
        TreinoRealizado atividade = new TreinoRealizado();
        atividade.setDataTreino(LocalDate.of(2026, 5, 1));
        atividade.setTenantId(UUID.randomUUID());

        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setDataTreino(LocalDate.of(2026, 5, 2));
        planejado.setTenantId(UUID.randomUUID());

        Atleta atleta = new Atleta();
        atleta.setTimezone("America/Sao_Paulo");

        MatchingScoreResult result = calculator.calculate(atividade, planejado, atleta);

        assertThat(result.getTemporalScore()).isEqualByComparingTo(new BigDecimal("0.75"));
    }

    @Test
    @DisplayName("deve rejeitar diferença > 2 dias com timezone normalizado")
    void testRejectLargeDateDifferenceWithTimezone() {
        TreinoRealizado atividade = new TreinoRealizado();
        atividade.setDataTreino(LocalDate.of(2026, 5, 1));
        atividade.setTenantId(UUID.randomUUID());

        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setDataTreino(LocalDate.of(2026, 5, 5));
        planejado.setTenantId(UUID.randomUUID());

        Atleta atleta = new Atleta();
        atleta.setTimezone("America/Sao_Paulo");

        MatchingScoreResult result = calculator.calculate(atividade, planejado, atleta);

        assertThat(result.getTemporalScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
