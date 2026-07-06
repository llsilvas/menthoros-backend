package br.com.menthoros.backend.services;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.BatchPlanJob;
import br.com.menthoros.backend.enums.BatchJobStatus;
import br.com.menthoros.backend.repository.BatchPlanJobRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * TDD — Section 1.8: recovery de jobs órfãos. Testa o filtro da query
 * (idade + status não-terminal) e o fechamento por contagem. Requer Docker.
 */
@DisplayName("BatchPlanRecoveryService")
@Transactional
class BatchPlanRecoveryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private BatchPlanRecoveryService recoveryService;
    @Autowired
    private BatchPlanJobRepository jobRepository;
    @Autowired
    private EntityManager entityManager;

    @Nested
    @DisplayName("recuperarJobsOrfaos")
    class RecuperarJobsOrfaos {

    @Test
    @DisplayName("fecha job órfão anterior ao limiar como CONCLUIDO_COM_ERROS com concluidoEm e observação")
    void fechaJobOrfao() {
        BatchPlanJob orfao = salvarJob(BatchJobStatus.EM_PROGRESSO, minutosAtras(40), 5, 2, 0);
        entityManager.flush();

        recoveryService.recuperarJobsOrfaos();
        entityManager.flush();
        entityManager.clear();

        BatchPlanJob recarregado = jobRepository.findById(orfao.getId()).orElseThrow();
        assertThat(recarregado.getStatus()).isEqualTo(BatchJobStatus.CONCLUIDO_COM_ERROS);
        assertThat(recarregado.getConcluidoEm()).isNotNull();
        // naoProcessados = 5 - 2 - 0 = 3
        assertThat(recarregado.getResultado()).contains("3 atleta(s)").contains("interrompido");
    }

    @Test
    @DisplayName("ignora job recente dentro do limiar")
    void ignoraJobRecente() {
        BatchPlanJob recente = salvarJob(BatchJobStatus.EM_PROGRESSO, minutosAtras(5), 3, 0, 0);
        entityManager.flush();

        recoveryService.recuperarJobsOrfaos();
        entityManager.flush();
        entityManager.clear();

        BatchPlanJob recarregado = jobRepository.findById(recente.getId()).orElseThrow();
        assertThat(recarregado.getStatus()).isEqualTo(BatchJobStatus.EM_PROGRESSO);
        assertThat(recarregado.getConcluidoEm()).isNull();
    }

    @Test
    @DisplayName("ignora job já em estado terminal (idempotência)")
    void ignoraJobTerminal() {
        Instant concluidoOriginal = minutosAtras(35);
        BatchPlanJob terminal = salvarJob(BatchJobStatus.CONCLUIDO, minutosAtras(40), 4, 4, 0);
        terminal.setConcluidoEm(concluidoOriginal);
        jobRepository.save(terminal);
        entityManager.flush();

        recoveryService.recuperarJobsOrfaos();
        entityManager.flush();
        entityManager.clear();

        BatchPlanJob recarregado = jobRepository.findById(terminal.getId()).orElseThrow();
        // Status inalterado prova que o recovery não tocou o job terminal (filtro da query).
        assertThat(recarregado.getStatus()).isEqualTo(BatchJobStatus.CONCLUIDO);
        assertThat(recarregado.getConcluidoEm()).isCloseTo(concluidoOriginal, within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("rodar o recovery duas vezes é idempotente — o job fechado não é reprocessado")
    void idempotenteEmDuasChamadas() {
        BatchPlanJob orfao = salvarJob(BatchJobStatus.EM_PROGRESSO, minutosAtras(40), 5, 1, 1);
        entityManager.flush();

        recoveryService.recuperarJobsOrfaos();
        entityManager.flush();
        entityManager.clear();
        Instant concluidoPrimeira = jobRepository.findById(orfao.getId()).orElseThrow().getConcluidoEm();

        // 2ª chamada: o job já é terminal, não deve mais aparecer como órfão.
        assertThat(jobRepository.findByStatusInAndCriadoEmBefore(
                List.of(BatchJobStatus.PENDENTE, BatchJobStatus.EM_PROGRESSO), Instant.now()))
                .extracting(BatchPlanJob::getId)
                .doesNotContain(orfao.getId());
        recoveryService.recuperarJobsOrfaos();
        entityManager.flush();
        entityManager.clear();

        BatchPlanJob recarregado = jobRepository.findById(orfao.getId()).orElseThrow();
        assertThat(recarregado.getStatus()).isEqualTo(BatchJobStatus.CONCLUIDO_COM_ERROS);
        assertThat(recarregado.getConcluidoEm()).isEqualTo(concluidoPrimeira);
    }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private BatchPlanJob salvarJob(BatchJobStatus status, Instant criadoEm, int total, int gerados, int erros) {
        BatchPlanJob job = new BatchPlanJob();
        job.setTenantId(UUID.randomUUID());
        job.setStatus(status);
        job.setTotalAtletas(total);
        job.setGerados(gerados);
        job.setErros(erros);
        job.setCriadoEm(criadoEm);
        return jobRepository.save(job);
    }

    private static Instant minutosAtras(long min) {
        return Instant.now().minus(min, ChronoUnit.MINUTES);
    }
}
