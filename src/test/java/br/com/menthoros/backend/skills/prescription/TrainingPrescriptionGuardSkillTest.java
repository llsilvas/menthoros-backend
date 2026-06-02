package br.com.menthoros.backend.skills.prescription;

import br.com.menthoros.backend.skills.core.SkillCategory;
import br.com.menthoros.backend.skills.core.SkillContext;
import br.com.menthoros.backend.skills.core.SkillResult;
import br.com.menthoros.backend.skills.core.SkillSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes TDD para TrainingPrescriptionGuardSkill.
 *
 * Cobertura dos cenários definidos na task A (Section 5):
 *  1. Plano adequado dentro do volume → aprovado
 *  2. Volume excessivo > 10% da média 4 semanas → BLOCKER
 *  3. TSS excessivo > 15% da meta → BLOCKER
 *  4. Fase RECOVERY com sessão INTERVALADO → BLOCKER
 *  5. Lesão ativa com dias consecutivos de alta intensidade → BLOCKER
 *  6. Ramp rate elevado → WARNING (não bloqueia)
 *  7. Plano vazio → aprovado sem violações
 *  8. skillKey retorna identificador correto
 */
class TrainingPrescriptionGuardSkillTest {

    private TrainingPrescriptionGuardSkill skill;
    private SkillContext context;

    @BeforeEach
    void setUp() {
        skill = new TrainingPrescriptionGuardSkill();
        context = SkillContext.of(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());
    }

    // =====================================================================
    // Helpers para construção de sessões
    // =====================================================================

    private TreinoPlanejadoResumo sessao(String tipo, String dia, double km, int min, double tss) {
        return new TreinoPlanejadoResumo(tipo, dia, km, min, tss);
    }

    private TrainingPrescriptionGuardInput inputPadrao(List<TreinoPlanejadoResumo> sessoes) {
        return new TrainingPrescriptionGuardInput(
                sessoes,
                55.0,   // ctlAtual
                5.0,    // tsbProntidaoAtual
                4.0,    // rampRateAtual normal
                400.0,  // metaTssSemanal
                50.0,   // mediaVolumeSemanas4 (50 km)
                3,      // diasConsecutivos
                false,  // temLesaoAtiva
                "BASE", // fasePeriodizacao
                "INTERMEDIARIO" // nivelExperiencia
        );
    }

    // =====================================================================
    // Cenário 1: Plano adequado dentro do volume — aprovado
    // =====================================================================

    @Test
    void planoAdequado_dentroDoVolume_retornaAprovado() {
        // 50 km médias × 1.10 = 55 km limite → propondo 50 km → OK
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("FACIL",    "SEGUNDA", 10.0, 60, 60.0),
                sessao("CONTINUO", "QUARTA",  15.0, 80, 90.0),
                sessao("LONGO",    "SABADO",  25.0, 130, 150.0)
        );

        TrainingPrescriptionGuardInput input = inputPadrao(sessoes);
        SkillResult<TrainingPrescriptionGuardPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertTrue(result.payload().aprovado(), "Plano dentro do volume deve ser aprovado");
        assertTrue(result.payload().violacoes().isEmpty(), "Não deve haver violações");
        // Severity deve ser INFO quando aprovado sem warnings
        assertNotEquals(SkillSeverity.BLOCKER, result.severity());
    }

    // =====================================================================
    // Cenário 2: Volume excessivo > 10% da média 4 semanas → BLOCKER
    // =====================================================================

    @Test
    void volumeExcessivo_maisQue10pct_retornaBLOCKER() {
        // Média = 50 km, limite = 55 km, propondo 60 km → violação
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("FACIL",    "SEGUNDA", 15.0, 70, 80.0),
                sessao("CONTINUO", "QUARTA",  20.0, 100, 110.0),
                sessao("LONGO",    "SABADO",  25.0, 130, 150.0)  // total = 60 km > 55 km
        );

        TrainingPrescriptionGuardInput input = inputPadrao(sessoes);
        SkillResult<TrainingPrescriptionGuardPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertEquals(SkillSeverity.BLOCKER, result.severity());
        assertFalse(result.payload().aprovado(), "Volume excessivo deve reprovar o plano");
        assertFalse(result.payload().violacoes().isEmpty(), "Deve ter violação de volume");
        assertTrue(
                result.payload().violacoes().stream().anyMatch(v -> v.toLowerCase().contains("volume")),
                "Violação deve mencionar volume"
        );
    }

    @Test
    void volumeExatamenteNoLimite_naoRetornaBLOCKER() {
        // Média = 50 km, limite exato = 55 km → deve passar
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("FACIL",    "SEGUNDA", 15.0, 70, 80.0),
                sessao("CONTINUO", "QUARTA",  15.0, 80, 90.0),
                sessao("LONGO",    "SABADO",  25.0, 130, 150.0)  // total = 55 km = limite
        );

        TrainingPrescriptionGuardInput input = inputPadrao(sessoes);
        SkillResult<TrainingPrescriptionGuardPayload> result = skill.execute(input, context);

        assertNotEquals(SkillSeverity.BLOCKER, result.severity(),
                "Volume no limite exato não deve ser BLOCKER");
    }

    // =====================================================================
    // Cenário 3: TSS excessivo > 15% da meta → BLOCKER
    // =====================================================================

    @Test
    void tssExcessivo_maisQue15pct_retornaBLOCKER() {
        // Meta = 400 TSS, limite = 460 TSS, propondo 500 TSS → violação
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("FACIL",      "SEGUNDA", 10.0, 60, 80.0),
                sessao("INTERVALADO","TERCA",   12.0, 70, 150.0),
                sessao("CONTINUO",   "QUARTA",  15.0, 80, 100.0),
                sessao("LONGO",      "SABADO",  20.0, 110, 170.0)  // total TSS = 500 > 460
        );

        TrainingPrescriptionGuardInput input = inputPadrao(sessoes);
        SkillResult<TrainingPrescriptionGuardPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertEquals(SkillSeverity.BLOCKER, result.severity());
        assertFalse(result.payload().aprovado());
        assertTrue(
                result.payload().violacoes().stream().anyMatch(v -> v.toLowerCase().contains("tss")),
                "Violação deve mencionar TSS"
        );
    }

    // =====================================================================
    // Cenário 4: Fase RECOVERY com sessão INTERVALADO → BLOCKER
    // =====================================================================

    @Test
    void faseRecovery_comIntervalado_retornaBLOCKER() {
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("REGENERATIVO", "SEGUNDA",  8.0, 45, 40.0),
                sessao("INTERVALADO",  "QUARTA",  10.0, 60, 90.0)  // INTERVALADO em RECOVERY → violação
        );

        TrainingPrescriptionGuardInput input = new TrainingPrescriptionGuardInput(
                sessoes,
                55.0,
                5.0,
                4.0,
                400.0,
                50.0,
                3,
                false,
                "RECOVERY", // fase RECOVERY
                "INTERMEDIARIO"
        );

        SkillResult<TrainingPrescriptionGuardPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertEquals(SkillSeverity.BLOCKER, result.severity());
        assertFalse(result.payload().aprovado());
        assertTrue(
                result.payload().violacoes().stream().anyMatch(v ->
                        v.toLowerCase().contains("recovery") || v.toLowerCase().contains("fase")),
                "Violação deve mencionar fase RECOVERY"
        );
    }

    @Test
    void faseTaper_comLongo_retornaBLOCKER() {
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("FACIL", "SEGUNDA",  8.0, 45, 40.0),
                sessao("LONGO", "SABADO",  28.0, 150, 170.0)  // LONGO em TAPER → violação
        );

        TrainingPrescriptionGuardInput input = new TrainingPrescriptionGuardInput(
                sessoes,
                55.0,
                5.0,
                4.0,
                400.0,
                50.0,
                3,
                false,
                "TAPER",
                "INTERMEDIARIO"
        );

        SkillResult<TrainingPrescriptionGuardPayload> result = skill.execute(input, context);

        assertEquals(SkillSeverity.BLOCKER, result.severity());
        assertFalse(result.payload().aprovado());
    }

    // =====================================================================
    // Cenário 5: Lesão ativa com mais de 2 dias consecutivos de alta intensidade → BLOCKER
    // =====================================================================

    @Test
    void lesaoAtiva_diasConsecutivosAltaIntensidade_retornaBLOCKER() {
        // Com lesão ativa: 3 sessões de alta intensidade em dias seguidos
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("INTERVALADO", "SEGUNDA",  10.0, 60, 90.0),
                sessao("TEMPO_RUN",   "TERCA",    12.0, 65, 100.0),
                sessao("LONGO",       "QUARTA",   20.0, 110, 140.0)
                // SEGUNDA→TERCA→QUARTA são 3 dias consecutivos de alta intensidade
        );

        TrainingPrescriptionGuardInput input = new TrainingPrescriptionGuardInput(
                sessoes,
                55.0,
                5.0,
                4.0,
                400.0,
                50.0,  // dentro do limite de volume (42 km < 55 km)
                3,
                true,   // temLesaoAtiva = true → BLOCKER se > 2 dias consecutivos de alta
                "BASE",
                "INTERMEDIARIO"
        );

        SkillResult<TrainingPrescriptionGuardPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertEquals(SkillSeverity.BLOCKER, result.severity());
        assertFalse(result.payload().aprovado());
        assertTrue(
                result.payload().violacoes().stream().anyMatch(v ->
                        v.toLowerCase().contains("lesão") || v.toLowerCase().contains("lesao")
                        || v.toLowerCase().contains("consecutiv")),
                "Violação deve mencionar lesão ou dias consecutivos"
        );
    }

    @Test
    void lesaoAtiva_doisDiasConsecutivosAltaIntensidade_naoRetornaBLOCKER() {
        // Com lesão ativa: exatamente 2 sessões de alta intensidade em dias seguidos → OK (limite é > 2)
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("INTERVALADO", "SEGUNDA",  10.0, 60, 90.0),
                sessao("LONGO",       "TERCA",    15.0, 90, 110.0)
                // Apenas 2 dias consecutivos → abaixo do limite de > 2
        );

        TrainingPrescriptionGuardInput input = new TrainingPrescriptionGuardInput(
                sessoes,
                55.0,
                5.0,
                4.0,
                400.0,
                50.0,
                2,
                true,   // temLesaoAtiva = true mas apenas 2 dias consecutivos
                "BASE",
                "INTERMEDIARIO"
        );

        SkillResult<TrainingPrescriptionGuardPayload> result = skill.execute(input, context);

        assertNotEquals(SkillSeverity.BLOCKER, result.severity(),
                "2 dias consecutivos com lesão não deve ser BLOCKER (regra é > 2)");
    }

    // =====================================================================
    // Cenário 6: Ramp rate elevado > 8.0 → WARNING (não BLOCKER)
    // =====================================================================

    @Test
    void rampRateElevado_retornaWARNING_naoBloqueia() {
        // rampRate > 8.0 + plano de alta carga → WARNING mas não BLOCKER
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("FACIL",    "SEGUNDA", 10.0, 60, 60.0),
                sessao("CONTINUO", "QUARTA",  15.0, 80, 90.0),
                sessao("LONGO",    "SABADO",  20.0, 110, 120.0)
        );

        TrainingPrescriptionGuardInput input = new TrainingPrescriptionGuardInput(
                sessoes,
                55.0,
                5.0,
                9.5,    // rampRate > 8.0 → WARNING
                400.0,
                50.0,
                3,
                false,
                "BASE",
                "INTERMEDIARIO"
        );

        SkillResult<TrainingPrescriptionGuardPayload> result = skill.execute(input, context);

        assertNotNull(result);
        // Ramp rate elevado deve ser WARNING, não BLOCKER — plano ainda pode ser aprovado
        assertNotEquals(SkillSeverity.BLOCKER, result.severity(),
                "Ramp rate elevado isoladamente deve gerar WARNING, não BLOCKER");
        // Se o plano estiver dentro dos outros limites, deve ser aprovado (com WARNING)
        assertTrue(result.payload().aprovado(),
                "Plano deve ser aprovado mesmo com ramp rate elevado (apenas WARNING)");
    }

    // =====================================================================
    // Cenário 7: Plano vazio → aprovado sem violações
    // =====================================================================

    @Test
    void planoVazio_retornaAprovadoSemViolacoes() {
        List<TreinoPlanejadoResumo> sessoes = Collections.emptyList();

        TrainingPrescriptionGuardInput input = inputPadrao(sessoes);
        SkillResult<TrainingPrescriptionGuardPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertTrue(result.payload().aprovado(), "Plano vazio deve ser aprovado");
        assertTrue(result.payload().violacoes().isEmpty(), "Plano vazio não deve ter violações");
        assertEquals(0.0, result.payload().tssTotal(), 0.001);
        assertEquals(0.0, result.payload().volumeTotalKm(), 0.001);
    }

    // =====================================================================
    // Cenário 8: skillKey retorna identificador correto
    // =====================================================================

    @Test
    void skillKey_retornaIdentificadorCorreto() {
        assertEquals("training-prescription-guard-v1", skill.skillKey());
    }

    @Test
    void category_retornaPRESCRIPTION_GUARD() {
        assertEquals(SkillCategory.PRESCRIPTION_GUARD, skill.category());
    }

    @Test
    void skillVersion_retornaVersaoValida() {
        assertNotNull(skill.skillVersion());
        assertFalse(skill.skillVersion().isBlank());
    }

    // =====================================================================
    // Cenário: input null lança exceção
    // =====================================================================

    @Test
    void execute_inputNull_lancaIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> skill.execute(null, context));
    }

    // =====================================================================
    // Cenário: totais calculados corretamente
    // =====================================================================

    @Test
    void execute_calculaTotaisCorretamente() {
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("FACIL",    "SEGUNDA", 10.0, 60, 60.0),
                sessao("CONTINUO", "QUARTA",  15.0, 80, 90.0)
        );

        TrainingPrescriptionGuardInput input = inputPadrao(sessoes);
        SkillResult<TrainingPrescriptionGuardPayload> result = skill.execute(input, context);

        assertEquals(25.0, result.payload().volumeTotalKm(), 0.001, "Volume total deve ser 25 km");
        assertEquals(150.0, result.payload().tssTotal(), 0.001, "TSS total deve ser 150");
    }
}
