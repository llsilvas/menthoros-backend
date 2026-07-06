package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.BatchPlanJob;
import br.com.menthoros.backend.enums.BatchJobStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — Section 1.2: testa os métodos do {@link BatchPlanJobRepository}.
 *
 * <p>Foca nos incrementos atômicos ({@code @Modifying}) — a garantia contra
 * perda de incremento entre virtual threads concorrentes — e no isolamento de
 * tenant. Requer Docker + PostgreSQL via Testcontainers.
 */
@DisplayName("BatchPlanJobRepository")
@Transactional
class BatchPlanJobRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private BatchPlanJobRepository repository;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("incrementarGerados")
    class IncrementarGerados {

        @Test
        @DisplayName("incrementa o contador atomicamente a cada chamada")
        void incrementaContador() {
            BatchPlanJob job = repository.save(novoJob(5));

            repository.incrementarGerados(job.getId());
            repository.incrementarGerados(job.getId());
            entityManager.clear();

            BatchPlanJob recarregado = repository.findById(job.getId()).orElseThrow();
            assertThat(recarregado.getGerados()).isEqualTo(2);
            assertThat(recarregado.getErros()).isZero();
        }
    }

    @Nested
    @DisplayName("incrementarErros")
    class IncrementarErros {

        @Test
        @DisplayName("incrementa o contador atomicamente a cada chamada")
        void incrementaContador() {
            BatchPlanJob job = repository.save(novoJob(5));

            repository.incrementarErros(job.getId());
            entityManager.clear();

            BatchPlanJob recarregado = repository.findById(job.getId()).orElseThrow();
            assertThat(recarregado.getErros()).isEqualTo(1);
            assertThat(recarregado.getGerados()).isZero();
        }
    }

    @Nested
    @DisplayName("findByIdAndTenantId")
    class FindByIdAndTenantId {

        @Test
        @DisplayName("retorna o job quando pertence ao tenant")
        void retornaJobDoTenant() {
            BatchPlanJob job = repository.save(novoJob(3));

            Optional<BatchPlanJob> encontrado = repository.findByIdAndTenantId(job.getId(), tenantId);

            assertThat(encontrado).isPresent();
            assertThat(encontrado.get().getTotalAtletas()).isEqualTo(3);
        }

        @Test
        @DisplayName("retorna vazio para jobId de outro tenant (isolamento)")
        void isolamentoTenant() {
            BatchPlanJob job = repository.save(novoJob(3));

            Optional<BatchPlanJob> encontrado = repository.findByIdAndTenantId(job.getId(), UUID.randomUUID());

            assertThat(encontrado).isEmpty();
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private BatchPlanJob novoJob(int totalAtletas) {
        BatchPlanJob job = new BatchPlanJob();
        job.setTenantId(tenantId);
        job.setStatus(BatchJobStatus.PENDENTE);
        job.setTotalAtletas(totalAtletas);
        return job;
    }
}
