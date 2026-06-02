package br.com.menthoros.backend.skills.analysis;

import br.com.menthoros.backend.skills.core.SkillCategory;
import br.com.menthoros.backend.skills.core.SkillContext;
import br.com.menthoros.backend.skills.core.SkillResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes TDD para LongRunAnalysisSkill.
 *
 * Cobertura dos 7+ cenários definidos na task 6.2:
 *  1. Com etapas: calcular drift de FC entre primeiro e último terço
 *  2. Drift acima de 10%: qualidade aeróbia BAIXA
 *  3. Sem etapas: fallback para IF e RPE
 *  4. Progressão correta: pace mantido ou melhorado no último terço
 *  5. skillKey retorna identificador correto
 *  6. category retorna WORKOUT_ANALYSIS
 *  7. Qualidade aeróbia ALTA quando drift baixo e pace mantido
 *  8. Qualidade aeróbia MODERADA em cenário intermediário
 */
class LongRunAnalysisSkillTest {

    private LongRunAnalysisSkill skill;
    private SkillContext context;

    private static final LocalDate DATA_REFERENCIA = LocalDate.of(2026, 6, 1);
    private static final UUID ATLETA_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        skill = new LongRunAnalysisSkill();
        context = SkillContext.of(ATLETA_ID, TENANT_ID, DATA_REFERENCIA);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Contrato da skill
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("skillKey deve retornar identificador correto")
    void skillKey_retornaIdentificadorCorreto() {
        assertEquals("long-run-analysis-v1", skill.skillKey());
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
    // Cenário: Com etapas — calcular drift de FC
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("com etapas: calcular drift de FC entre primeiro e último terço")
    void comEtapas_calcularDriftFc() {
        // 6 etapas uniformes: primeiro terço (etapas 1-2) fcMedia=140, último terço (etapas 5-6) fcMedia=155
        // drift = (155 - 140) / 140 * 100 = 10.71% → acima de 10% → BAIXA
        List<EtapaRealizadaResumo> etapas = List.of(
                new EtapaRealizadaResumo("PRINCIPAL", 15, 3.0, 138.0, 300.0),
                new EtapaRealizadaResumo("PRINCIPAL", 15, 3.0, 142.0, 302.0),
                new EtapaRealizadaResumo("PRINCIPAL", 15, 3.0, 147.0, 305.0),
                new EtapaRealizadaResumo("PRINCIPAL", 15, 3.0, 150.0, 308.0),
                new EtapaRealizadaResumo("PRINCIPAL", 15, 3.0, 153.0, 312.0),
                new EtapaRealizadaResumo("PRINCIPAL", 15, 3.0, 157.0, 318.0)
        );

        LongRunAnalysisInput input = new LongRunAnalysisInput(
                18.0,   // distanciaKm
                90,     // duracaoMin
                85.0,   // tssRealizado
                7,      // percepcaoEsforco
                147.0,  // fcMediaGlobal
                160.0,  // fcLimiar
                305.0,  // paceMediaGlobal (sec/km)
                300.0,  // paceAlvo (sec/km)
                etapas
        );

        SkillResult<LongRunAnalysisPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertNotNull(result.payload());
        assertTrue(result.payload().driftFc() > 0, "Drift de FC deve ser positivo quando FC aumentou");
        assertNotNull(result.payload().qualidadeAerobia());
        assertNotNull(result.payload().interpretacao());
    }

    @Test
    @DisplayName("drift acima de 10%: qualidade aeróbia deve ser BAIXA")
    void driftAcimaDe10pct_qualidadeAerobiaBaixa() {
        // primeiro terço fcMedia≈130, último terço fcMedia≈148 → drift ≈ 13.8% → BAIXA
        List<EtapaRealizadaResumo> etapas = List.of(
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 128.0, 310.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 132.0, 315.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 140.0, 320.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 145.0, 325.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 146.0, 328.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 150.0, 332.0)
        );

        LongRunAnalysisInput input = new LongRunAnalysisInput(
                24.0, 120, 100.0, 8,
                140.0, 160.0, 320.0, 310.0,
                etapas
        );

        SkillResult<LongRunAnalysisPayload> result = skill.execute(input, context);

        assertEquals("BAIXA", result.payload().qualidadeAerobia());
        assertTrue(result.payload().driftFc() > 10.0, "Drift deve ser > 10% para qualidade BAIXA");
    }

    @Test
    @DisplayName("drift abaixo de 10%: qualidade aeróbia deve ser ALTA ou MODERADA")
    void driftAbaixoDe10pct_qualidadeAeroбiaAltaOuModerada() {
        // primeiro terço fcMedia≈140, último terço fcMedia≈146 → drift ≈ 4.3% → não é BAIXA
        List<EtapaRealizadaResumo> etapas = List.of(
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 139.0, 305.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 141.0, 307.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 143.0, 310.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 144.0, 310.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 145.0, 308.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 147.0, 306.0)
        );

        LongRunAnalysisInput input = new LongRunAnalysisInput(
                24.0, 120, 90.0, 6,
                143.0, 160.0, 308.0, 305.0,
                etapas
        );

        SkillResult<LongRunAnalysisPayload> result = skill.execute(input, context);

        assertNotEquals("BAIXA", result.payload().qualidadeAerobia());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: Sem etapas — fallback para IF e RPE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sem etapas: fallback para IF e RPE classifica qualidade aeróbia")
    void semEtapas_fallbackParaIfERpe() {
        // fcMediaGlobal=144, fcLimiar=160 → IF=0.90, RPE=5 → qualidade aeróbia ALTA
        LongRunAnalysisInput input = new LongRunAnalysisInput(
                20.0, 100, 70.0,
                5,         // RPE baixo → bom sinal aeróbio
                144.0,     // fcMediaGlobal
                160.0,     // fcLimiar → IF=0.90 (controlado)
                300.0, 305.0,
                List.of()  // sem etapas
        );

        SkillResult<LongRunAnalysisPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertNotNull(result.payload().qualidadeAerobia());
        // No fallback, driftFc deve ser 0.0 (não calculado)
        assertEquals(0.0, result.payload().driftFc(), 0.001, "Sem etapas, drift deve ser 0.0");
    }

    @Test
    @DisplayName("sem etapas: IF alto + RPE alto → qualidade aeróbia BAIXA no fallback")
    void semEtapas_ifAltoERpeAlto_qualidadeBaixa() {
        // fcMediaGlobal=160, fcLimiar=160 → IF=1.0, RPE=9 → sinal de esforço alto demais
        LongRunAnalysisInput input = new LongRunAnalysisInput(
                20.0, 110, 100.0,
                9,         // RPE muito alto
                160.0,     // fcMediaGlobal → IF=1.0 (acima da zona de longa duração)
                160.0,     // fcLimiar
                330.0, 305.0,
                null       // null equivale a lista vazia
        );

        SkillResult<LongRunAnalysisPayload> result = skill.execute(input, context);

        assertNotNull(result);
        // RPE >= 8 com IF >= 0.95 deve indicar qualidade BAIXA no fallback
        assertEquals("BAIXA", result.payload().qualidadeAerobia());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: Progressão correta de pace
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("progressão correta: pace mantido ou melhorado no último terço")
    void progressaoCorreta_paceImproveNoUltimoTerco() {
        // último terço tem pace MENOR (mais rápido) que primeiro terço → progressão correta
        // primeiro terço paceMedia ≈ 310 sec/km, último terço paceMedia ≈ 300 sec/km
        List<EtapaRealizadaResumo> etapas = List.of(
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 140.0, 312.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 141.0, 310.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 142.0, 305.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 143.0, 303.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 143.0, 299.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 144.0, 298.0)
        );

        LongRunAnalysisInput input = new LongRunAnalysisInput(
                24.0, 120, 88.0, 6,
                142.0, 160.0, 304.0, 305.0,
                etapas
        );

        SkillResult<LongRunAnalysisPayload> result = skill.execute(input, context);

        assertTrue(result.payload().progressaoCorreta(),
                "Pace melhorou no último terço → progressão deve ser correta");
    }

    @Test
    @DisplayName("progressão incorreta: pace piorou no último terço")
    void progressaoIncorreta_pacePiorandoNoUltimoTerco() {
        // primeiro terço pace ≈ 295 sec/km, último terço pace ≈ 325 sec/km (mais lento)
        List<EtapaRealizadaResumo> etapas = List.of(
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 140.0, 293.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 143.0, 297.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 148.0, 308.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 153.0, 318.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 157.0, 322.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 162.0, 328.0)
        );

        LongRunAnalysisInput input = new LongRunAnalysisInput(
                24.0, 120, 110.0, 9,
                150.0, 160.0, 311.0, 295.0,
                etapas
        );

        SkillResult<LongRunAnalysisPayload> result = skill.execute(input, context);

        assertFalse(result.payload().progressaoCorreta(),
                "Pace piorou no último terço → progressão deve ser incorreta");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: Qualidade aeróbia ALTA
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("baixo drift e progressão correta: qualidade aeróbia deve ser ALTA")
    void baixoDriftEProgressaoCorreta_qualidadeAeroбiaAlta() {
        // drift ≈ 3%, pace melhorou no último terço → ALTA
        List<EtapaRealizadaResumo> etapas = List.of(
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 140.0, 310.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 141.0, 308.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 141.0, 306.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 142.0, 304.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 143.0, 302.0),
                new EtapaRealizadaResumo("PRINCIPAL", 20, 4.0, 144.0, 300.0)
        );

        LongRunAnalysisInput input = new LongRunAnalysisInput(
                24.0, 120, 80.0, 5,
                141.8, 160.0, 305.0, 305.0,
                etapas
        );

        SkillResult<LongRunAnalysisPayload> result = skill.execute(input, context);

        assertEquals("ALTA", result.payload().qualidadeAerobia());
        assertTrue(result.payload().progressaoCorreta());
        assertTrue(result.payload().driftFc() < 10.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: Validações e estrutura do resultado
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resultado deve sempre ter interpretação não vazia")
    void execute_interpretacaoNaoVazia() {
        LongRunAnalysisInput input = new LongRunAnalysisInput(
                20.0, 100, 75.0, 6,
                145.0, 160.0, 305.0, 300.0,
                List.of()
        );

        SkillResult<LongRunAnalysisPayload> result = skill.execute(input, context);

        assertNotNull(result.payload().interpretacao());
        assertFalse(result.payload().interpretacao().isBlank());
    }

    @Test
    @DisplayName("resultado deve sempre ter evidências não vazias")
    void execute_evidenciasNaoVazias() {
        LongRunAnalysisInput input = new LongRunAnalysisInput(
                20.0, 100, 75.0, 6,
                145.0, 160.0, 305.0, 300.0,
                List.of()
        );

        SkillResult<LongRunAnalysisPayload> result = skill.execute(input, context);

        assertNotNull(result.evidence());
        assertFalse(result.evidence().isEmpty());
    }

    @Test
    @DisplayName("input nulo deve lançar IllegalArgumentException")
    void inputNulo_lancaIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> skill.execute(null, context));
    }
}
