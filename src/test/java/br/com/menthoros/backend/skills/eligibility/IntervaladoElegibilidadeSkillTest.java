package br.com.menthoros.backend.skills.eligibility;

import br.com.menthoros.backend.skills.core.SkillCategory;
import br.com.menthoros.backend.skills.core.SkillContext;
import br.com.menthoros.backend.skills.core.SkillResult;
import br.com.menthoros.backend.skills.core.SkillSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IntervaladoElegibilidadeSkillTest {

    private IntervaladoElegibilidadeSkill skill;

    private static final LocalDate DATA_REFERENCIA = LocalDate.of(2026, 6, 1);
    private static final UUID ATLETA_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        skill = new IntervaladoElegibilidadeSkill();
    }

    private SkillContext contexto() {
        return SkillContext.of(ATLETA_ID, TENANT_ID, DATA_REFERENCIA);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Contrato da skill
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("skillKey deve retornar identificador correto")
    void skillKey_retornaIdentificadorCorreto() {
        assertEquals("intervalado-elegibilidade-v1", skill.skillKey());
    }

    @Test
    @DisplayName("category deve retornar ELIGIBILITY")
    void category_retornaELIGIBILITY() {
        assertEquals(SkillCategory.ELIGIBILITY, skill.category());
    }

    @Test
    @DisplayName("skillVersion deve retornar versão não nula e não vazia")
    void skillVersion_retornaVersaoValida() {
        assertNotNull(skill.skillVersion());
        assertFalse(skill.skillVersion().isBlank());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: atleta elegível
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Atleta elegível com TSB bom, fase ideal e sem lesão retorna elegivel=true")
    void atletaElegivel_tsbBom_faseIdeal_retornaElegivel() {
        IntervaladoElegibilidadeInput input = new IntervaladoElegibilidadeInput(
                5.0,    // tsbProntidaoAtual — positivo, boa prontidão
                40.0,   // ctlAtual — bom para fase BUILD
                6.0,    // rampRateAtual — dentro do limite
                false,  // temLesaoAtiva
                5,      // diasDesdeUltimoIntervalado — >= 3 dias mínimo
                "BUILD" // fasePeriodizacao
        );

        SkillResult<IntervaladoElegibilidadePayload> result = skill.execute(input, contexto());

        assertNotNull(result);
        assertNotNull(result.payload());
        assertTrue(result.payload().elegivel(), "Atleta deve ser elegível");
        assertNotNull(result.payload().motivo());
        assertNotNull(result.payload().condicoes());
        assertFalse(result.payload().condicoes().isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: lesão ativa — BLOCKER
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Atleta com lesão ativa retorna BLOCKER independente do TSB")
    void atletaComLesao_retornaBLOCKER_independenteDeTsb() {
        IntervaladoElegibilidadeInput input = new IntervaladoElegibilidadeInput(
                10.0,   // tsbProntidaoAtual — excelente (não importa)
                50.0,   // ctlAtual — excelente (não importa)
                4.0,    // rampRateAtual — seguro (não importa)
                true,   // temLesaoAtiva — BLOQUEADOR ABSOLUTO
                7,      // diasDesdeUltimoIntervalado
                "BUILD"
        );

        SkillResult<IntervaladoElegibilidadePayload> result = skill.execute(input, contexto());

        assertNotNull(result);
        assertEquals(SkillSeverity.BLOCKER, result.severity(),
                "Lesão ativa deve resultar em BLOCKER");
        assertFalse(result.payload().elegivel(),
                "Atleta com lesão ativa não deve ser elegível");
        assertNotNull(result.payload().motivo());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: fase TAPER — bloqueia intervalado
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fase TAPER bloqueia intervalado independente do TSB")
    void faseTAPER_bloqueiaIntervalado() {
        IntervaladoElegibilidadeInput input = new IntervaladoElegibilidadeInput(
                8.0,    // tsbProntidaoAtual — ótimo (não importa para TAPER)
                60.0,   // ctlAtual — elite
                3.0,    // rampRateAtual — seguro
                false,  // sem lesão
                7,      // dias suficientes
                "TAPER" // fase que bloqueia intervalados
        );

        SkillResult<IntervaladoElegibilidadePayload> result = skill.execute(input, contexto());

        assertNotNull(result);
        assertFalse(result.payload().elegivel(),
                "Fase TAPER deve bloquear intervalado");
        assertNotEquals(SkillSeverity.INFO, result.severity(),
                "TAPER não deve resultar em INFO quando há bloqueio");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: fase RECOVERY — bloqueia intervalado
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fase RECOVERY bloqueia intervalado independente das métricas")
    void faseRECOVERY_bloqueiaIntervalado() {
        IntervaladoElegibilidadeInput input = new IntervaladoElegibilidadeInput(
                5.0,        // tsbProntidaoAtual — bom
                45.0,       // ctlAtual — bom
                5.0,        // rampRateAtual — seguro
                false,      // sem lesão
                10,         // dias suficientes
                "RECOVERY"  // fase de recuperação — bloqueia alta intensidade
        );

        SkillResult<IntervaladoElegibilidadePayload> result = skill.execute(input, contexto());

        assertNotNull(result);
        assertFalse(result.payload().elegivel(),
                "Fase RECOVERY deve bloquear intervalado");
        assertNotNull(result.payload().motivo());
        assertTrue(result.payload().motivo().toLowerCase().contains("recovery")
                || result.payload().motivo().toLowerCase().contains("recupera"),
                "Motivo deve mencionar a fase de recuperação");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: TSB insuficiente — WARNING
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TSB insuficiente retorna WARNING e não elegível")
    void tsbInsuficiente_retornaWarning_naoElegivel() {
        IntervaladoElegibilidadeInput input = new IntervaladoElegibilidadeInput(
                -25.0,  // tsbProntidaoAtual — muito negativo (fadiga alta)
                35.0,   // ctlAtual
                8.0,    // rampRateAtual — elevado
                false,  // sem lesão
                5,      // dias suficientes
                "BUILD"
        );

        SkillResult<IntervaladoElegibilidadePayload> result = skill.execute(input, contexto());

        assertNotNull(result);
        assertFalse(result.payload().elegivel(),
                "TSB muito negativo deve tornar atleta inelegível");
        assertEquals(SkillSeverity.WARNING, result.severity(),
                "TSB insuficiente deve gerar WARNING");
        assertTrue(result.payload().motivo().toLowerCase().contains("tsb")
                || result.payload().motivo().toLowerCase().contains("prontidão")
                || result.payload().motivo().toLowerCase().contains("prontidao"),
                "Motivo deve mencionar TSB ou prontidão");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: dias insuficientes desde último intervalado — WARNING
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Dias insuficientes desde último intervalado retorna WARNING e não elegível")
    void diasInsuficientesDesdeUltimo_retornaWarning() {
        IntervaladoElegibilidadeInput input = new IntervaladoElegibilidadeInput(
                5.0,    // tsbProntidaoAtual — bom
                40.0,   // ctlAtual — bom
                5.0,    // rampRateAtual — seguro
                false,  // sem lesão
                1,      // diasDesdeUltimoIntervalado — insuficiente (mínimo é 2)
                "BUILD"
        );

        SkillResult<IntervaladoElegibilidadePayload> result = skill.execute(input, contexto());

        assertNotNull(result);
        assertFalse(result.payload().elegivel(),
                "Recuperação insuficiente deve tornar atleta inelegível");
        assertEquals(SkillSeverity.WARNING, result.severity(),
                "Dias insuficientes deve gerar WARNING");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: ramp rate excessivo — WARNING
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ramp rate excessivo retorna WARNING e não elegível")
    void rampRateExcessivo_retornaWarning_naoElegivel() {
        IntervaladoElegibilidadeInput input = new IntervaladoElegibilidadeInput(
                3.0,    // tsbProntidaoAtual — aceitável
                35.0,   // ctlAtual — aceitável
                12.0,   // rampRateAtual — acima do limite seguro (> 8.0)
                false,  // sem lesão
                4,      // dias suficientes
                "BUILD"
        );

        SkillResult<IntervaladoElegibilidadePayload> result = skill.execute(input, contexto());

        assertNotNull(result);
        assertFalse(result.payload().elegivel(),
                "Ramp rate excessivo deve tornar atleta inelegível");
        assertEquals(SkillSeverity.WARNING, result.severity(),
                "Ramp rate excessivo deve gerar WARNING");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: input nulo — exceção
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Input nulo deve lançar IllegalArgumentException")
    void inputNulo_lancaIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> skill.execute(null, contexto()),
                "Input nulo deve lançar IllegalArgumentException");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: resultado contém evidence e recommendations
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Resultado elegível deve conter evidências e recomendações")
    void resultadoElegivel_contemEvidenciasERecomendacoes() {
        IntervaladoElegibilidadeInput input = new IntervaladoElegibilidadeInput(
                5.0,
                40.0,
                6.0,
                false,
                5,
                "BUILD"
        );

        SkillResult<IntervaladoElegibilidadePayload> result = skill.execute(input, contexto());

        assertNotNull(result.evidence());
        assertNotNull(result.recommendations());
        assertFalse(result.evidence().isEmpty(), "Evidence não deve estar vazia");
        assertFalse(result.recommendations().isEmpty(), "Recommendations não deve estar vazia");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário: resultado contém skillKey e skillVersion corretos
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Resultado deve conter skillKey e skillVersion da skill")
    void resultado_contemSkillKeyESkillVersion() {
        IntervaladoElegibilidadeInput input = new IntervaladoElegibilidadeInput(
                5.0, 40.0, 6.0, false, 5, "BUILD"
        );

        SkillResult<IntervaladoElegibilidadePayload> result = skill.execute(input, contexto());

        assertEquals(skill.skillKey(), result.skillKey());
        assertEquals(skill.skillVersion(), result.skillVersion());
    }
}
