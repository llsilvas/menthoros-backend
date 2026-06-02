package br.com.menthoros.backend.skills.prescription;

import br.com.menthoros.backend.skills.core.SkillCategory;
import br.com.menthoros.backend.skills.core.SkillContext;
import br.com.menthoros.backend.skills.core.SkillResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes TDD para WeeklyDistributionSkill.
 *
 * Cobertura dos cenários definidos na task B:
 *  1. Longão vai para dia preferido quando disponível
 *  2. INTERVALADO não segue LONGO — mínimo 1 dia fácil entre sessões-chave
 *  3. Duas sessões de qualidade consecutivas são separadas
 *  4. Sem conflito → não reordena
 *  5. skillKey retorna identificador correto
 *  6. category retorna DISTRIBUTION
 */
class WeeklyDistributionSkillTest {

    private WeeklyDistributionSkill skill;
    private SkillContext context;

    @BeforeEach
    void setUp() {
        skill = new WeeklyDistributionSkill();
        context = SkillContext.of(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private TreinoPlanejadoResumo sessao(String tipo, String dia, double km) {
        return new TreinoPlanejadoResumo(tipo, dia, km, 60, 80.0);
    }

    // =====================================================================
    // Cenário 1: Longão vai para dia preferido quando disponível
    // =====================================================================

    @Test
    void longoVaiParaDiaPreferido_quandoDisponivel() {
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("FACIL", "SEGUNDA", 10.0),
                sessao("LONGO", "QUARTA",  25.0)  // LONGO proposto para QUARTA
        );

        WeeklyDistributionInput input = new WeeklyDistributionInput(
                sessoes,
                List.of("SEGUNDA", "QUARTA", "SEXTA", "SABADO"),
                "SABADO",   // diaPreferidoLongo = SABADO
                "INTERMEDIARIO"
        );

        SkillResult<WeeklyDistributionPayload> result = skill.execute(input, context);

        assertNotNull(result);
        // LONGO deve estar no SABADO (dia preferido), não na QUARTA
        assertEquals("LONGO", result.payload().distribuicao().get("SABADO"),
                "LONGO deve ser movido para o dia preferido SABADO");
    }

    @Test
    void longoVaiParaUltimoDiaDisponivel_quandoPreferidoNaoDisponivel() {
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("FACIL", "SEGUNDA", 10.0),
                sessao("LONGO", "SEGUNDA", 25.0)
        );

        WeeklyDistributionInput input = new WeeklyDistributionInput(
                sessoes,
                List.of("SEGUNDA", "QUARTA", "SEXTA"),
                "SABADO",   // SABADO não está disponível
                "INTERMEDIARIO"
        );

        SkillResult<WeeklyDistributionPayload> result = skill.execute(input, context);

        assertNotNull(result);
        // LONGO deve ir para o último dia disponível (SEXTA) quando preferido não está disponível
        assertEquals("LONGO", result.payload().distribuicao().get("SEXTA"),
                "LONGO deve ir para o último dia disponível (SEXTA) quando preferido não disponível");
    }

    // =====================================================================
    // Cenário 2: INTERVALADO não segue LONGO — mínimo 1 dia fácil entre sessões-chave
    // =====================================================================

    @Test
    void intervaladoNaoSegeLongo_minimoUmDiaFacil() {
        // LONGO na QUINTA, INTERVALADO na SEXTA (consecutivos) → deve ser separado
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("LONGO",      "QUINTA", 25.0),
                sessao("INTERVALADO","SEXTA",  12.0)
        );

        WeeklyDistributionInput input = new WeeklyDistributionInput(
                sessoes,
                List.of("TERCA", "QUINTA", "SEXTA", "SABADO"),
                null,
                "INTERMEDIARIO"
        );

        SkillResult<WeeklyDistributionPayload> result = skill.execute(input, context);

        assertNotNull(result);
        // INTERVALADO não pode ficar no dia imediatamente após LONGO
        String diaLongo = result.payload().distribuicao().entrySet().stream()
                .filter(e -> "LONGO".equals(e.getValue()))
                .map(java.util.Map.Entry::getKey)
                .findFirst().orElse(null);
        String diaIntervalado = result.payload().distribuicao().entrySet().stream()
                .filter(e -> "INTERVALADO".equals(e.getValue()))
                .map(java.util.Map.Entry::getKey)
                .findFirst().orElse(null);

        assertNotNull(diaLongo, "LONGO deve estar na distribuição");
        assertNotNull(diaIntervalado, "INTERVALADO deve estar na distribuição");

        // Os dias não podem ser consecutivos
        assertFalse(saoConsecutivos(diaLongo, diaIntervalado),
                "LONGO e INTERVALADO não devem estar em dias consecutivos. " +
                "LONGO=" + diaLongo + ", INTERVALADO=" + diaIntervalado);
    }

    // =====================================================================
    // Cenário 3: Duas sessões de qualidade consecutivas são separadas
    // =====================================================================

    @Test
    void duasQualidadesConsecutivas_saoSeparadas() {
        // INTERVALADO na TERCA, TEMPO_RUN na QUARTA (consecutivos) → deve ser separado
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("INTERVALADO", "TERCA",  12.0),
                sessao("TEMPO_RUN",   "QUARTA", 15.0),
                sessao("FACIL",       "SABADO", 10.0)
        );

        WeeklyDistributionInput input = new WeeklyDistributionInput(
                sessoes,
                List.of("SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SABADO"),
                "SABADO",
                "INTERMEDIARIO"
        );

        SkillResult<WeeklyDistributionPayload> result = skill.execute(input, context);

        assertNotNull(result);
        // Sessões de alta qualidade não devem estar em dias consecutivos
        String diaIntervalado = result.payload().distribuicao().entrySet().stream()
                .filter(e -> "INTERVALADO".equals(e.getValue()))
                .map(java.util.Map.Entry::getKey)
                .findFirst().orElse(null);
        String diaTempoRun = result.payload().distribuicao().entrySet().stream()
                .filter(e -> "TEMPO_RUN".equals(e.getValue()))
                .map(java.util.Map.Entry::getKey)
                .findFirst().orElse(null);

        assertNotNull(diaIntervalado, "INTERVALADO deve estar na distribuição");
        assertNotNull(diaTempoRun, "TEMPO_RUN deve estar na distribuição");

        assertFalse(saoConsecutivos(diaIntervalado, diaTempoRun),
                "INTERVALADO e TEMPO_RUN não devem estar em dias consecutivos. " +
                "INTERVALADO=" + diaIntervalado + ", TEMPO_RUN=" + diaTempoRun);
    }

    // =====================================================================
    // Cenário 4: Sem conflito → não reordena
    // =====================================================================

    @Test
    void semConflito_naoReordena() {
        // FACIL na SEGUNDA, CONTINUO na QUARTA, LONGO no SABADO — sem conflitos
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("FACIL",    "SEGUNDA", 10.0),
                sessao("CONTINUO", "QUARTA",  15.0),
                sessao("LONGO",    "SABADO",  25.0)
        );

        WeeklyDistributionInput input = new WeeklyDistributionInput(
                sessoes,
                List.of("SEGUNDA", "QUARTA", "SABADO"),
                "SABADO",   // LONGO já está no dia preferido
                "INTERMEDIARIO"
        );

        SkillResult<WeeklyDistributionPayload> result = skill.execute(input, context);

        assertNotNull(result);
        assertFalse(result.payload().reordenado(), "Sem conflitos, não deve reordenar");
        assertTrue(result.payload().movimentos().isEmpty(), "Sem reordenação, movimentos devem ser vazios");
        assertEquals("LONGO",    result.payload().distribuicao().get("SABADO"));
        assertEquals("FACIL",    result.payload().distribuicao().get("SEGUNDA"));
        assertEquals("CONTINUO", result.payload().distribuicao().get("QUARTA"));
    }

    // =====================================================================
    // Cenário 5: skillKey retorna identificador correto
    // =====================================================================

    @Test
    void skillKey_retornaIdentificadorCorreto() {
        assertEquals("weekly-distribution-v1", skill.skillKey());
    }

    // =====================================================================
    // Cenário 6: category retorna DISTRIBUTION
    // =====================================================================

    @Test
    void category_retornaDISTRIBUTION() {
        assertEquals(SkillCategory.DISTRIBUTION, skill.category());
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
    // Cenário: reordenado = true quando movimento foi feito
    // =====================================================================

    @Test
    void quandoLongoMovido_reordenadoDeveSerTrue() {
        List<TreinoPlanejadoResumo> sessoes = List.of(
                sessao("FACIL", "SEGUNDA", 10.0),
                sessao("LONGO", "QUARTA",  25.0)  // proposto para QUARTA, deve ir para SABADO
        );

        WeeklyDistributionInput input = new WeeklyDistributionInput(
                sessoes,
                List.of("SEGUNDA", "QUARTA", "SABADO"),
                "SABADO",
                "INTERMEDIARIO"
        );

        SkillResult<WeeklyDistributionPayload> result = skill.execute(input, context);

        assertTrue(result.payload().reordenado(), "Deve marcar reordenado=true quando LONGO é movido");
        assertFalse(result.payload().movimentos().isEmpty(), "Deve registrar o movimento realizado");
    }

    // =====================================================================
    // Utilitário: verificar se dois dias da semana são consecutivos
    // =====================================================================

    private static final List<String> ORDEM_SEMANA = List.of(
            "DOMINGO", "SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO"
    );

    private boolean saoConsecutivos(String dia1, String dia2) {
        if (dia1 == null || dia2 == null) return false;
        int idx1 = ORDEM_SEMANA.indexOf(dia1.toUpperCase());
        int idx2 = ORDEM_SEMANA.indexOf(dia2.toUpperCase());
        if (idx1 < 0 || idx2 < 0) return false;
        return Math.abs(idx1 - idx2) == 1;
    }
}
