package br.com.menthoros.backend.skills.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do SkillOrchestratorService.
 * Usa skills mock inline para verificar comportamento do orquestrador.
 */
@DisplayName("SkillOrchestratorService — orquestração de skills")
class SkillOrchestratorServiceTest {

    private SkillOrchestratorService orchestrator;
    private SkillContext context;

    @BeforeEach
    void setUp() {
        orchestrator = new SkillOrchestratorService();
        context = new SkillContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                Map.of()
        );
    }

    @Test
    @DisplayName("Executa lista de skills e retorna resultados na mesma ordem")
    void execute_duasSkills_retornaResultadosNaOrdem() {
        DomainSkill<String, String> skill1 = mockSkill(
                "recovery-v1", "1.0", SkillCategory.RECOVERY,
                SkillSeverity.WARNING, "Resultado 1"
        );
        DomainSkill<String, String> skill2 = mockSkill(
                "eligibility-v1", "1.0", SkillCategory.ELIGIBILITY,
                SkillSeverity.INFO, "Resultado 2"
        );

        @SuppressWarnings("unchecked")
        List<DomainSkill<?, ?>> skills = List.of(skill1, skill2);
        List<SkillResult<?>> results = orchestrator.execute(skills, context);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).skillKey()).isEqualTo("recovery-v1");
        assertThat(results.get(1).skillKey()).isEqualTo("eligibility-v1");
    }

    @Test
    @DisplayName("Skill que lança exception não propaga erro — resultado omitido, demais executam")
    void execute_skillComException_naoPropagarErro() {
        DomainSkill<String, String> skillOk = mockSkill(
                "recovery-v1", "1.0", SkillCategory.RECOVERY,
                SkillSeverity.INFO, "OK"
        );
        DomainSkill<String, String> skillFalha = new DomainSkill<>() {
            @Override public String skillKey() { return "falha-v1"; }
            @Override public String skillVersion() { return "1.0"; }
            @Override public SkillCategory category() { return SkillCategory.PRESCRIPTION_GUARD; }
            @Override public SkillResult<String> execute(String input, SkillContext ctx) {
                throw new RuntimeException("Erro simulado na skill");
            }
        };
        DomainSkill<String, String> skillOk2 = mockSkill(
                "eligibility-v1", "1.0", SkillCategory.ELIGIBILITY,
                SkillSeverity.INFO, "OK2"
        );

        @SuppressWarnings("unchecked")
        List<DomainSkill<?, ?>> skills = List.of(skillOk, skillFalha, skillOk2);
        List<SkillResult<?>> results = orchestrator.execute(skills, context);

        // A skill que falhou não deve propagar exception; as demais devem executar
        assertThat(results).hasSize(2);
        assertThat(results).extracting(SkillResult::skillKey)
                .containsExactly("recovery-v1", "eligibility-v1");
    }

    @Test
    @DisplayName("Lista vazia de skills retorna lista vazia de resultados")
    void execute_listaVazia_retornaListaVazia() {
        List<SkillResult<?>> results = orchestrator.execute(List.of(), context);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Resultado de skill BLOCKER preservado na lista de resultados")
    void execute_skillComBlocker_preservaResultado() {
        DomainSkill<String, String> skillBlocker = new DomainSkill<>() {
            @Override public String skillKey() { return "overtraining-v1"; }
            @Override public String skillVersion() { return "1.0"; }
            @Override public SkillCategory category() { return SkillCategory.PRESCRIPTION_GUARD; }
            @Override public SkillResult<String> execute(String input, SkillContext ctx) {
                return new SkillResult<>(
                        skillKey(), skillVersion(),
                        SkillSeverity.BLOCKER, SkillConfidence.HIGH,
                        "Overtraining detectado",
                        List.of("TSB=-25"),
                        List.of("Semana de descanso")
                );
            }
        };

        @SuppressWarnings("unchecked")
        List<DomainSkill<?, ?>> skills = List.of(skillBlocker);
        List<SkillResult<?>> results = orchestrator.execute(skills, context);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).severity()).isEqualTo(SkillSeverity.BLOCKER);
    }

    // --- helper para criar skills mock inline ---

    private DomainSkill<String, String> mockSkill(
            String key, String version,
            SkillCategory category,
            SkillSeverity severity,
            String payloadValue) {
        return new DomainSkill<>() {
            @Override public String skillKey() { return key; }
            @Override public String skillVersion() { return version; }
            @Override public SkillCategory category() { return category; }
            @Override public SkillResult<String> execute(String input, SkillContext ctx) {
                return new SkillResult<>(
                        key, version, severity, SkillConfidence.HIGH,
                        payloadValue, List.of(), List.of()
                );
            }
        };
    }
}
