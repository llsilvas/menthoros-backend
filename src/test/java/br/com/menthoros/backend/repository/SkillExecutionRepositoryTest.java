package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.SkillExecution;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.skills.core.SkillConfidence;
import br.com.menthoros.backend.skills.core.SkillSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — Section 4.3: testa os métodos do SkillExecutionRepository.
 *
 * <p>Testes de integração que requerem Docker + PostgreSQL via Testcontainers.</p>
 * <p>Rodados somente quando o Docker está disponível (profile: integration).</p>
 */
@DisplayName("SkillExecutionRepository: queries por atleta e skill")
@Transactional
class SkillExecutionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private SkillExecutionRepository skillExecutionRepository;

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    private UUID tenantId;
    private Atleta atleta;

    @BeforeEach
    void setup() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria SkillExec Test");
        assessoria.setDominio("skill-exec-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        tenantId = assessoria.getId();

        atleta = new Atleta();
        atleta.setNome("Atleta Skill Test");
        atleta.setEmail("skill-test-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Testar skills");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);
    }

    @Test
    @DisplayName("findByAtletaIdAndTenantId retorna execuções ordenadas por executedAt DESC")
    void findByAtletaId_retornaExecucoesOrdenadas() {
        Instant agora = Instant.now();
        Instant ontem = agora.minus(1, ChronoUnit.DAYS);
        Instant anteontem = agora.minus(2, ChronoUnit.DAYS);

        SkillExecution exec1 = criarExecution("recovery-v1", anteontem);
        SkillExecution exec2 = criarExecution("recovery-v1", ontem);
        SkillExecution exec3 = criarExecution("recovery-v1", agora);

        skillExecutionRepository.save(exec1);
        skillExecutionRepository.save(exec2);
        skillExecutionRepository.save(exec3);

        List<SkillExecution> resultado = skillExecutionRepository
                .findByAtletaIdAndTenantIdOrderByExecutedAtDesc(atleta.getId(), tenantId);

        assertThat(resultado).hasSize(3);
        // Deve retornar da mais recente para a mais antiga
        assertThat(resultado.get(0).getExecutedAt()).isAfterOrEqualTo(resultado.get(1).getExecutedAt());
        assertThat(resultado.get(1).getExecutedAt()).isAfterOrEqualTo(resultado.get(2).getExecutedAt());
    }

    @Test
    @DisplayName("findTopByAtletaIdAndSkillKey retorna a execução mais recente da skill")
    void findTopByAtletaIdAndSkillKey_retornaMaisRecente() {
        Instant agora = Instant.now();
        Instant ontem = agora.minus(1, ChronoUnit.DAYS);

        SkillExecution execAntiga = criarExecution("eligibility-v1", ontem);
        SkillExecution execRecente = criarExecution("eligibility-v1", agora);
        SkillExecution execOutraSkill = criarExecution("recovery-v1", agora);

        skillExecutionRepository.save(execAntiga);
        skillExecutionRepository.save(execRecente);
        skillExecutionRepository.save(execOutraSkill);

        Optional<SkillExecution> resultado = skillExecutionRepository
                .findTopByAtletaIdAndSkillKeyOrderByExecutedAtDesc(atleta.getId(), "eligibility-v1");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getExecutedAt())
                .isAfterOrEqualTo(execAntiga.getExecutedAt());
        assertThat(resultado.get().getSkillKey()).isEqualTo("eligibility-v1");
    }

    @Test
    @DisplayName("findBySkillKeyAndTenantId retorna todas as execuções da skill no tenant")
    void findBySkillKeyAndTenantId_retornaExecucoesDaSkill() {
        SkillExecution exec1 = criarExecution("prescription-guard-v1", Instant.now().minus(2, ChronoUnit.HOURS));
        SkillExecution exec2 = criarExecution("prescription-guard-v1", Instant.now().minus(1, ChronoUnit.HOURS));
        SkillExecution execOutra = criarExecution("recovery-v1", Instant.now());

        skillExecutionRepository.save(exec1);
        skillExecutionRepository.save(exec2);
        skillExecutionRepository.save(execOutra);

        List<SkillExecution> resultado = skillExecutionRepository
                .findBySkillKeyAndTenantIdOrderByExecutedAtDesc("prescription-guard-v1", tenantId);

        assertThat(resultado).hasSize(2);
        assertThat(resultado).allMatch(e -> "prescription-guard-v1".equals(e.getSkillKey()));
    }

    @Test
    @DisplayName("findByAtletaId não retorna execuções de outro tenant")
    void findByAtletaId_isolamentoTenant() {
        UUID outroTenantId = UUID.randomUUID();

        SkillExecution execTenantCorreto = criarExecution("recovery-v1", Instant.now());
        execTenantCorreto.setTenantId(tenantId);

        SkillExecution execOutroTenant = criarExecution("recovery-v1", Instant.now());
        execOutroTenant.setAtleta(atleta);
        execOutroTenant.setTenantId(outroTenantId);

        skillExecutionRepository.save(execTenantCorreto);
        skillExecutionRepository.save(execOutroTenant);

        List<SkillExecution> resultado = skillExecutionRepository
                .findByAtletaIdAndTenantIdOrderByExecutedAtDesc(atleta.getId(), tenantId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getTenantId()).isEqualTo(tenantId);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private SkillExecution criarExecution(String skillKey, Instant executedAt) {
        SkillExecution exec = new SkillExecution();
        exec.setSkillKey(skillKey);
        exec.setSkillVersion("1.0");
        exec.setSeverity(SkillSeverity.INFO.name());
        exec.setConfidence(SkillConfidence.MEDIUM.name());
        exec.setTenantId(tenantId);
        exec.setAtleta(atleta);
        exec.setExecutedAt(executedAt);
        return exec;
    }
}
