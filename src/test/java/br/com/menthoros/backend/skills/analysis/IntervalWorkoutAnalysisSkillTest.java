package br.com.menthoros.backend.skills.analysis;

import br.com.menthoros.backend.skills.core.SkillCategory;
import br.com.menthoros.backend.skills.core.SkillContext;
import br.com.menthoros.backend.skills.core.SkillResult;
import br.com.menthoros.backend.skills.core.SkillSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes TDD para IntervalWorkoutAnalysisSkill.
 *
 * Cobertura dos 8+ cenários definidos na task 6.1:
 *  1. Com etapas: calcular IF pelas etapas principais
 *  2. Sem etapas: usar FC global como fallback
 *  3. IF >= 1.05 → qualidade EXCELENTE
 *  4. IF 0.95-1.05 → qualidade BOA
 *  5. IF 0.85-0.95 → qualidade REGULAR
 *  6. IF < 0.85 → qualidade FRACA
 *  7. skillKey retorna identificador correto
 *  8. category retorna WORKOUT_ANALYSIS
 *  9. etapasAnalisadas = true quando etapas disponíveis
 * 10. etapasAnalisadas = false quando sem etapas (fallback)
 */
class IntervalWorkoutAnalysisSkillTest {

    private IntervalWorkoutAnalysisSkill skill;
    private SkillContext context;

    private static final LocalDate DATA_REFERENCIA = LocalDate.of(2026, 6, 1);
    private static final UUID ATLETA_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        skill = new IntervalWorkoutAnalysisSkill();
        context = SkillContext.of(ATLETA_ID, TENANT_ID, DATA_REFERENCIA);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Contrato da skill
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("skillKey deve retornar identificador correto")
    void skillKey_retornaIdentificadorCorreto() {
        assertEquals("interval-workout-analysis-v1", skill.skillKey());
    }

    @Test
    @DisplayName("category deve retornar WORKOUT_ANALYSIS")
    void category_retornaWORKOUT_ANALYSIS() {
        assertEquals(SkillCategory.WORKOUT_ANALYSIS, skill.category());
    }

    @Test
    @DisplayName("skillVersion deve retornar versão não nula e não vazia")
    void skillVersion_retornaVersaoValida() {
        assertNotNull(skill.skillVersion());
        assertFalse(skill.skillVersion().isBlank());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: Com etapas — calcular IF pelas etapas principais
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("com etapas: deve usar etapas principais para calcular IF e marcar etapasAnalisadas=true")
    void comEtapas_calcularIfPorEtapasPrincipais() {
        // fcLimiar = 160, etapa principal com fcMedia = 168 → IF = 168/160 = 1.05
        List<EtapaRealizadaResumo> etapas = List.of(
                new EtapaRealizadaResumo("AQUECIMENTO", 10, 2.0, 130.0, 360.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 5.0, 168.0, 240.0),
                new EtapaRealizadaResumo("RECUPERACAO", 5, 1.0, 120.0, 480.0)
        );

        IntervalWorkoutAnalysisInput input = new IntervalWorkoutAnalysisInput(
                "INTERVALADO",
                8.0,    // distanciaKm
                35,     // duracaoMin
                75.0,   // tssRealizado
                7,      // percepcaoEsforco
                150.0,  // fcMediaGlobal
                160.0,  // fcLimiar
                etapas
        );

        SkillResult<IntervalWorkoutAnalysisPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertNotNull(result.payload());
        assertTrue(result.payload().etapasAnalisadas(), "Deve marcar etapasAnalisadas=true quando etapas disponíveis");
        // IF = 168/160 = 1.05 → BOA (limite exato 1.05 é BOA, não EXCELENTE — vide spec)
        assertNotNull(result.payload().qualidadeExecucao());
        assertNotNull(result.payload().interpretacao());
    }

    @Test
    @DisplayName("com etapas: múltiplas etapas principais — calcular IF médio das principais")
    void comEtapas_multiplas_principaisCalcularIfMedio() {
        // fcLimiar = 160
        // etapa PRINCIPAL 1: fcMedia=176 → IF=1.10
        // etapa PRINCIPAL 2: fcMedia=164 → IF=1.025
        // IF médio = (1.10 + 1.025) / 2 = 1.0625 → acima de 1.05 → EXCELENTE
        List<EtapaRealizadaResumo> etapas = List.of(
                new EtapaRealizadaResumo("AQUECIMENTO", 10, 2.0, 130.0, 360.0),
                new EtapaRealizadaResumo("PRINCIPAL", 12, 3.0, 176.0, 230.0),
                new EtapaRealizadaResumo("RECUPERACAO", 3, 0.5, 120.0, 500.0),
                new EtapaRealizadaResumo("PRINCIPAL", 12, 3.0, 164.0, 255.0),
                new EtapaRealizadaResumo("ENCERRAMENTO", 8, 1.5, 125.0, 400.0)
        );

        IntervalWorkoutAnalysisInput input = new IntervalWorkoutAnalysisInput(
                "INTERVALADO",
                10.0,
                45,
                90.0,
                8,
                155.0,
                160.0,
                etapas
        );

        SkillResult<IntervalWorkoutAnalysisPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertTrue(result.payload().etapasAnalisadas());
        assertEquals("EXCELENTE", result.payload().qualidadeExecucao());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: Sem etapas — fallback para FC global
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sem etapas: deve usar fcMediaGlobal/fcLimiar como IF e marcar etapasAnalisadas=false")
    void semEtapas_usarFcGlobalComoFallback() {
        // fcMediaGlobal=144, fcLimiar=160 → IF=0.90 → REGULAR (0.85 <= IF < 0.95)
        IntervalWorkoutAnalysisInput input = new IntervalWorkoutAnalysisInput(
                "INTERVALADO",
                8.0,
                35,
                65.0,
                6,
                144.0,  // fcMediaGlobal → IF=0.90 (estritamente abaixo de 0.95)
                160.0,  // fcLimiar
                List.of()  // sem etapas
        );

        SkillResult<IntervalWorkoutAnalysisPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertFalse(result.payload().etapasAnalisadas(), "Deve marcar etapasAnalisadas=false no fallback");
        assertEquals("REGULAR", result.payload().qualidadeExecucao());
    }

    @Test
    @DisplayName("sem etapas: input null na lista deve funcionar como fallback")
    void semEtapas_listaNull_fallback() {
        // fcMediaGlobal=170, fcLimiar=160 → IF=1.0625 → EXCELENTE
        IntervalWorkoutAnalysisInput input = new IntervalWorkoutAnalysisInput(
                "INTERVALADO",
                9.0,
                40,
                80.0,
                8,
                170.0,
                160.0,
                null  // null equivale a lista vazia
        );

        SkillResult<IntervalWorkoutAnalysisPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertFalse(result.payload().etapasAnalisadas());
        assertEquals("EXCELENTE", result.payload().qualidadeExecucao());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: Classificação de qualidade por IF
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IF acima de 1.05: qualidade deve ser EXCELENTE")
    void ifAcimaDe105_qualidadeExcelente() {
        // IF = 170/160 = 1.0625 → EXCELENTE
        IntervalWorkoutAnalysisInput input = new IntervalWorkoutAnalysisInput(
                "INTERVALADO", 8.0, 35, 75.0, 8,
                170.0, 160.0, List.of()
        );

        SkillResult<IntervalWorkoutAnalysisPayload> result = skill.execute(input, context);

        assertEquals("EXCELENTE", result.payload().qualidadeExecucao());
        assertTrue(result.payload().ifMedio() > 1.05);
    }

    @Test
    @DisplayName("IF entre 0.95 e 1.05 (inclusive): qualidade deve ser BOA")
    void ifEntre095E105_qualidadeBoa() {
        // IF = 160/160 = 1.00 → BOA
        IntervalWorkoutAnalysisInput input = new IntervalWorkoutAnalysisInput(
                "INTERVALADO", 8.0, 35, 70.0, 7,
                160.0, 160.0, List.of()
        );

        SkillResult<IntervalWorkoutAnalysisPayload> result = skill.execute(input, context);

        assertEquals("BOA", result.payload().qualidadeExecucao());
    }

    @Test
    @DisplayName("IF entre 0.85 e 0.95 (exclusive): qualidade deve ser REGULAR")
    void ifEntre085E095_qualidadeRegular() {
        // IF = 144/160 = 0.90 → REGULAR
        IntervalWorkoutAnalysisInput input = new IntervalWorkoutAnalysisInput(
                "INTERVALADO", 7.0, 30, 58.0, 5,
                144.0, 160.0, List.of()
        );

        SkillResult<IntervalWorkoutAnalysisPayload> result = skill.execute(input, context);

        assertEquals("REGULAR", result.payload().qualidadeExecucao());
    }

    @Test
    @DisplayName("IF abaixo de 0.85: qualidade deve ser FRACA")
    void ifAbaixoDe085_qualidadeFraca() {
        // IF = 128/160 = 0.80 → FRACA
        IntervalWorkoutAnalysisInput input = new IntervalWorkoutAnalysisInput(
                "INTERVALADO", 5.0, 30, 40.0, 4,
                128.0, 160.0, List.of()
        );

        SkillResult<IntervalWorkoutAnalysisPayload> result = skill.execute(input, context);

        assertEquals("FRACA", result.payload().qualidadeExecucao());
        assertTrue(result.payload().ifMedio() < 0.85);
    }

    @Test
    @DisplayName("IF exatamente 0.95: qualidade deve ser BOA (limite inferior de BOA)")
    void ifExato095_qualidadeBoa() {
        // IF = 152/160 = 0.95 → BOA (0.95 <= IF < 1.05 é BOA)
        IntervalWorkoutAnalysisInput input = new IntervalWorkoutAnalysisInput(
                "INTERVALADO", 7.5, 33, 62.0, 6,
                152.0, 160.0, List.of()
        );

        SkillResult<IntervalWorkoutAnalysisPayload> result = skill.execute(input, context);

        assertEquals("BOA", result.payload().qualidadeExecucao());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: evidências e interpretação
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resultado deve sempre ter interpretação não vazia")
    void execute_interpretacaoNaoVazia() {
        IntervalWorkoutAnalysisInput input = new IntervalWorkoutAnalysisInput(
                "INTERVALADO", 8.0, 35, 70.0, 7,
                160.0, 160.0, List.of()
        );

        SkillResult<IntervalWorkoutAnalysisPayload> result = skill.execute(input, context);

        assertNotNull(result.payload().interpretacao());
        assertFalse(result.payload().interpretacao().isBlank());
    }

    @Test
    @DisplayName("resultado deve sempre ter evidências não vazias")
    void execute_evidenciasNaoVazias() {
        IntervalWorkoutAnalysisInput input = new IntervalWorkoutAnalysisInput(
                "INTERVALADO", 8.0, 35, 70.0, 7,
                160.0, 160.0, List.of()
        );

        SkillResult<IntervalWorkoutAnalysisPayload> result = skill.execute(input, context);

        assertNotNull(result.evidence());
        assertFalse(result.evidence().isEmpty());
    }

    @Test
    @DisplayName("input nulo deve lançar IllegalArgumentException")
    void inputNulo_lancaIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> skill.execute(null, context));
    }
}
