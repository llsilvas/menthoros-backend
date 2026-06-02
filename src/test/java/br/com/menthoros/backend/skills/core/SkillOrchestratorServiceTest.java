package br.com.menthoros.backend.skills.core;

import br.com.menthoros.backend.entity.SkillExecution;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.SkillExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do SkillOrchestratorService.
 * Usa skills mock inline para verificar comportamento do orquestrador.
 */
@DisplayName("SkillOrchestratorService — orquestração de skills")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillOrchestratorServiceTest {

    @Mock
    private SkillExecutionRepository skillExecutionRepository;

    @Mock
    private AtletaRepository atletaRepository;

    private SkillOrchestratorService orchestrator;
    private SkillContext context;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        orchestrator = new SkillOrchestratorService(skillExecutionRepository, atletaRepository, objectMapper);
        context = new SkillContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                Map.of()
        );
        // Por padrão, atleta não encontrado (best-effort)
        when(atletaRepository.findById(any())).thenReturn(Optional.empty());
        // Por padrão, save retorna o argumento recebido
        when(skillExecutionRepository.save(any(SkillExecution.class))).thenAnswer(inv -> inv.getArgument(0));
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

    @Test
    @DisplayName("Persiste SkillExecution após cada skill bem-sucedida")
    void execute_persisteSkillExecution_paraCadaSkillBemSucedida() {
        DomainSkill<String, String> skill1 = mockSkill(
                "recovery-v1", "1.0", SkillCategory.RECOVERY, SkillSeverity.INFO, "payload1");
        DomainSkill<String, String> skill2 = mockSkill(
                "eligibility-v1", "1.0", SkillCategory.ELIGIBILITY, SkillSeverity.WARNING, "payload2");

        @SuppressWarnings("unchecked")
        List<DomainSkill<?, ?>> skills = List.of(skill1, skill2);
        orchestrator.execute(skills, context);

        // Deve ter persistido 2 execuções
        verify(skillExecutionRepository, times(2)).save(any(SkillExecution.class));
    }

    @Test
    @DisplayName("Persistência usa tenantId do contexto")
    void execute_persistencia_usaTenantIdDoContexto() {
        DomainSkill<String, String> skill = mockSkill(
                "recovery-v1", "1.0", SkillCategory.RECOVERY, SkillSeverity.INFO, "payload");

        @SuppressWarnings("unchecked")
        List<DomainSkill<?, ?>> skills = List.of(skill);
        orchestrator.execute(skills, context);

        ArgumentCaptor<SkillExecution> captor = ArgumentCaptor.forClass(SkillExecution.class);
        verify(skillExecutionRepository).save(captor.capture());

        SkillExecution salvo = captor.getValue();
        assertThat(salvo.getTenantId()).isEqualTo(context.tenantId());
        assertThat(salvo.getSkillKey()).isEqualTo("recovery-v1");
        assertThat(salvo.getSeverity()).isEqualTo(SkillSeverity.INFO.name());
        assertThat(salvo.getConfidence()).isEqualTo(SkillConfidence.HIGH.name());
        assertThat(salvo.getExecutedAt()).isNotNull();
        assertThat(salvo.getDataReferencia()).isEqualTo(context.dataReferencia());
    }

    @Test
    @DisplayName("Falha na persistência não bloqueia retorno dos resultados")
    void execute_falhaAoPersistir_naoBloqueia() {
        when(skillExecutionRepository.save(any(SkillExecution.class)))
                .thenThrow(new RuntimeException("Erro simulado de banco"));

        DomainSkill<String, String> skill = mockSkill(
                "recovery-v1", "1.0", SkillCategory.RECOVERY, SkillSeverity.INFO, "payload");

        @SuppressWarnings("unchecked")
        List<DomainSkill<?, ?>> skills = List.of(skill);
        List<SkillResult<?>> results = orchestrator.execute(skills, context);

        // Mesmo com falha na persistência, o resultado é retornado
        assertThat(results).hasSize(1);
        assertThat(results.get(0).skillKey()).isEqualTo("recovery-v1");
    }

    @Test
    @DisplayName("Skill que falha não persiste SkillExecution")
    void execute_skillComException_naoPersiste() {
        DomainSkill<String, String> skillFalha = new DomainSkill<>() {
            @Override public String skillKey() { return "falha-v1"; }
            @Override public String skillVersion() { return "1.0"; }
            @Override public SkillCategory category() { return SkillCategory.PRESCRIPTION_GUARD; }
            @Override public SkillResult<String> execute(String input, SkillContext ctx) {
                throw new RuntimeException("Erro simulado");
            }
        };

        @SuppressWarnings("unchecked")
        List<DomainSkill<?, ?>> skills = List.of(skillFalha);
        orchestrator.execute(skills, context);

        // Não deve persistir execução para skill que falhou
        verify(skillExecutionRepository, never()).save(any(SkillExecution.class));
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
