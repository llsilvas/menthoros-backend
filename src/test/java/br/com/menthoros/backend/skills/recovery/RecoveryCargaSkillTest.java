package br.com.menthoros.backend.skills.recovery;

import br.com.menthoros.backend.skills.core.SkillCategory;
import br.com.menthoros.backend.skills.core.SkillConfidence;
import br.com.menthoros.backend.skills.core.SkillContext;
import br.com.menthoros.backend.skills.core.SkillResult;
import br.com.menthoros.backend.skills.core.SkillSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes TDD para RecoveryCargaSkill.
 *
 * Cobertura dos 7 cenários definidos na task 3.1:
 *  1. Atleta fresco: TSB >= 5 e rampRate <= 5 → FRESCO (INFO, HIGH)
 *  2. Atleta neutro: TSB >= -10 e < 5 → NEUTRO (INFO, MEDIUM)
 *  3. Atleta fatigado: TSB < -10 e >= -25 → FATIGADO (WARNING, HIGH)
 *  4. Atleta sobrecarregado: TSB < -25 → SOBRECARREGADO (CRITICAL, HIGH)
 *  5. Atleta com lesão ativa → recomendação de redução de intensidade
 *  6. 7 dias consecutivos → SOBRECARREGADO
 *  7. skillKey e category
 */
class RecoveryCargaSkillTest {

    private RecoveryCargaSkill skill;
    private SkillContext context;

    @BeforeEach
    void setUp() {
        skill = new RecoveryCargaSkill();
        context = SkillContext.of(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());
    }

    // =====================================================================
    // Cenário 1: Atleta FRESCO
    // tsb >= 5 e rampRate <= 5 → FRESCO (INFO, HIGH, sem bloqueio)
    // =====================================================================

    @Test
    void atletaFresco_tsbPositivo_retornaInfoSemBloqueio() {
        RecoveryCargaInput input = new RecoveryCargaInput(
                60.0,   // ctlAtual
                55.0,   // atlAtual
                8.0,    // tsbProntidaoAtual >= 5 → FRESCO
                3.0,    // rampRateAtual <= 5
                3,      // diasConsecutivosTreino
                false   // temLesaoAtiva
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertEquals(SkillSeverity.INFO, result.severity());
        assertEquals(SkillConfidence.HIGH, result.confidence());
        assertNotNull(result.payload());
        assertEquals("FRESCO", result.payload().classificacao());
        assertFalse(result.payload().requerDescanso());
        assertFalse(result.payload().bloqueiaAltaIntensidade());
        assertFalse(result.evidence().isEmpty());
    }

    @Test
    void atletaFresco_tsbExatamente5_retornaFresco() {
        RecoveryCargaInput input = new RecoveryCargaInput(
                50.0,
                45.0,
                5.0,   // limite exato
                2.0,
                2,
                false
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        assertEquals("FRESCO", result.payload().classificacao());
        assertEquals(SkillSeverity.INFO, result.severity());
    }

    // =====================================================================
    // Cenário 2: Atleta NEUTRO
    // tsb >= -10 e < 5 → NEUTRO (INFO, MEDIUM)
    // =====================================================================

    @Test
    void atletaNeutro_tsbEntreMenos10e5_retornaInfoMedium() {
        RecoveryCargaInput input = new RecoveryCargaInput(
                45.0,
                48.0,
                -3.0,   // -10 <= tsb < 5 → NEUTRO
                4.0,
                2,
                false
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertEquals(SkillSeverity.INFO, result.severity());
        assertEquals(SkillConfidence.MEDIUM, result.confidence());
        assertEquals("NEUTRO", result.payload().classificacao());
        assertFalse(result.payload().requerDescanso());
    }

    // =====================================================================
    // Cenário 3: Atleta FATIGADO
    // tsb < -10 e >= -25 → FATIGADO (WARNING, HIGH)
    // =====================================================================

    @Test
    void atletaFatigado_tsbMenos15_retornaWarning() {
        RecoveryCargaInput input = new RecoveryCargaInput(
                55.0,
                70.0,
                -15.0,  // -25 <= tsb < -10 → FATIGADO
                6.0,
                4,
                false
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertEquals(SkillSeverity.WARNING, result.severity());
        assertEquals(SkillConfidence.HIGH, result.confidence());
        assertEquals("FATIGADO", result.payload().classificacao());
        assertFalse(result.payload().requerDescanso(), "FATIGADO não bloqueia — apenas alerta");
    }

    @Test
    void atletaFatigado_tsbMenos24_limiteInferiorFatigado() {
        RecoveryCargaInput input = new RecoveryCargaInput(
                50.0,
                75.0,
                -24.0,  // ainda FATIGADO (justo acima de -25)
                5.0,
                5,
                false
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        assertEquals("FATIGADO", result.payload().classificacao());
        assertEquals(SkillSeverity.WARNING, result.severity());
    }

    // =====================================================================
    // Cenário 4: Atleta SOBRECARREGADO por TSB < -25
    // → SOBRECARREGADO (CRITICAL, HIGH)
    // =====================================================================

    @Test
    void atletaSobrecarregado_tsbMenos30_retornaCritical() {
        RecoveryCargaInput input = new RecoveryCargaInput(
                60.0,
                90.0,
                -30.0,  // tsb < -25 → SOBRECARREGADO
                8.0,
                6,
                false
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertEquals(SkillSeverity.CRITICAL, result.severity());
        assertEquals(SkillConfidence.HIGH, result.confidence());
        assertEquals("SOBRECARREGADO", result.payload().classificacao());
        assertTrue(result.payload().requerDescanso());
        assertTrue(result.payload().bloqueiaAltaIntensidade());
    }

    @Test
    void atletaSobrecarregado_tsbMenos25virgula1_retornaCritical() {
        // TSB = -25.1: estritamente menor que -25 → SOBRECARREGADO
        // TSB = -25.0 (exato) ainda é FATIGADO, pois a regra é tsb < -25 (strictamente menor)
        RecoveryCargaInput input = new RecoveryCargaInput(
                55.0,
                80.0,
                -25.1,  // estritamente menor que -25 → SOBRECARREGADO
                7.0,
                5,
                false
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        assertEquals("SOBRECARREGADO", result.payload().classificacao());
        assertEquals(SkillSeverity.CRITICAL, result.severity());
    }

    // =====================================================================
    // Cenário 5: Atleta com lesão ativa
    // → sempre adiciona recomendação de redução de intensidade
    // =====================================================================

    @Test
    void atletaComLesao_sempreAdicionaRecomendacaoReducao() {
        RecoveryCargaInput input = new RecoveryCargaInput(
                50.0,
                45.0,
                10.0,   // FRESCO normalmente
                2.0,
                2,
                true    // temLesaoAtiva = true
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        assertNotNull(result);
        // Mesmo sendo FRESCO, lesão ativa deve adicionar recomendação de redução
        assertTrue(
                result.recommendations().stream().anyMatch(r ->
                        r.toLowerCase().contains("intensidade") || r.toLowerCase().contains("lesão") || r.toLowerCase().contains("lesao")),
                "Deve conter recomendação relacionada a lesão/intensidade. Recommendations: " + result.recommendations()
        );
    }

    @Test
    void atletaFatigadoComLesao_adicionaRecomendacaoDeLesaoEFadiga() {
        RecoveryCargaInput input = new RecoveryCargaInput(
                55.0,
                70.0,
                -15.0,  // FATIGADO
                6.0,
                4,
                true    // temLesaoAtiva = true
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        assertEquals("FATIGADO", result.payload().classificacao());
        assertFalse(result.recommendations().isEmpty());
        assertTrue(
                result.recommendations().stream().anyMatch(r ->
                        r.toLowerCase().contains("intensidade") || r.toLowerCase().contains("lesão") || r.toLowerCase().contains("lesao")),
                "Deve incluir recomendação de redução de intensidade por lesão"
        );
    }

    // =====================================================================
    // Cenário 6: Atleta com 7 dias consecutivos → SOBRECARREGADO
    // =====================================================================

    @Test
    void atletaSeteDiasConsecutivos_retornaSobrecarregado() {
        RecoveryCargaInput input = new RecoveryCargaInput(
                50.0,
                52.0,
                0.0,    // tsb neutro — mas dias consecutivos dispara SOBRECARREGADO
                3.0,
                7,      // >= 7 dias → SOBRECARREGADO
                false
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertEquals("SOBRECARREGADO", result.payload().classificacao());
        assertEquals(SkillSeverity.CRITICAL, result.severity());
        assertTrue(result.payload().requerDescanso());
    }

    @Test
    void atletaSeisDiasConsecutivos_naoRetornaSobrecarregadoSoPorDias() {
        // 6 dias NÃO é o threshold de 7 dias — não deve ser SOBRECARREGADO por isso
        RecoveryCargaInput input = new RecoveryCargaInput(
                50.0,
                52.0,
                0.0,    // NEUTRO por TSB
                3.0,
                6,      // < 7 dias consecutivos
                false
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        // TSB=0 está entre -10 e 5, então seria NEUTRO sem o gate de dias
        assertNotEquals("SOBRECARREGADO", result.payload().classificacao());
    }

    // =====================================================================
    // Cenário 7: skillKey e category
    // =====================================================================

    @Test
    void skillKey_retornaIdentificadorCorreto() {
        assertEquals("recovery-carga-v1", skill.skillKey());
    }

    @Test
    void category_retornaRECOVERY() {
        assertEquals(SkillCategory.RECOVERY, skill.category());
    }

    @Test
    void skillVersion_retornaVersaoValida() {
        assertNotNull(skill.skillVersion());
        assertFalse(skill.skillVersion().isBlank());
    }

    // =====================================================================
    // Cenário: evidências não estão vazias para todos os casos
    // =====================================================================

    @Test
    void execute_sempreRetornaEvidenciasNaoVazias() {
        RecoveryCargaInput input = new RecoveryCargaInput(
                50.0, 55.0, -5.0, 4.0, 3, false
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        assertNotNull(result.evidence());
        assertFalse(result.evidence().isEmpty(), "Evidence list should not be empty");
    }

    // =====================================================================
    // Cenário: FRESCO com rampRate alto não retorna FRESCO
    // tsb >= 5 mas rampRate > 5 → não é FRESCO (condição AND)
    // =====================================================================

    @Test
    void atletaTsbPositivoMasRampRateAlto_naoRetornaFresco() {
        RecoveryCargaInput input = new RecoveryCargaInput(
                60.0,
                52.0,
                8.0,    // tsb >= 5
                6.0,    // rampRate > 5 → não satisfaz a condição AND para FRESCO
                3,
                false
        );

        SkillResult<RecoveryCargaPayload> result = skill.execute(input, context);

        // Com rampRate > 5, mesmo com tsb >= 5, não deve classificar como FRESCO
        assertNotEquals("FRESCO", result.payload().classificacao(),
                "TSB >= 5 mas rampRate > 5 não deve resultar em FRESCO");
    }
}
