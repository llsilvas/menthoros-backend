package br.com.menthoros.backend.multitenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("isolamento entre threads")
    class IsolamentoEntreThreads {

        @Test
        @DisplayName("thread filha NÃO herda o tenant da thread que a criou")
        void threadFilhaNaoHerda() throws InterruptedException {
            UUID tenantId = UUID.randomUUID();
            TenantContext.setTenantId(tenantId);

            AtomicReference<UUID> visto = new AtomicReference<>(tenantId);
            Thread filha = new Thread(() -> visto.set(TenantContext.getTenantId()));
            filha.start();
            filha.join();

            assertThat(visto.get())
                    .as("thread filha não deve herdar o tenant (evita vazamento em pools)")
                    .isNull();
        }

        @Test
        @DisplayName("thread de pool reutilizada não vaza tenant entre tarefas (padrão set+clear)")
        void poolNaoVazaEntreTarefas() throws Exception {
            UUID tenantA = UUID.randomUUID();
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                // Tarefa 1: padrão async correto — seta e limpa no finally
                pool.submit(() -> {
                    try {
                        TenantContext.setTenantId(tenantA);
                    } finally {
                        TenantContext.clear();
                    }
                }).get();

                // Tarefa 2: mesma thread do pool — não pode enxergar o tenant da tarefa anterior
                AtomicReference<UUID> visto = new AtomicReference<>(tenantA);
                pool.submit(() -> visto.set(TenantContext.getTenantId())).get();

                assertThat(visto.get())
                        .as("tarefa seguinte na mesma thread de pool não deve ver tenant vazado")
                        .isNull();
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("ciclo de vida na thread atual")
    class CicloDeVida {

        @Test
        @DisplayName("set/get/clear na mesma thread")
        void setGetClear() {
            UUID tenantId = UUID.randomUUID();

            TenantContext.setTenantId(tenantId);
            assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
            assertThat(TenantContext.hasTenant()).isTrue();

            TenantContext.clear();
            assertThat(TenantContext.getTenantId()).isNull();
            assertThat(TenantContext.hasTenant()).isFalse();
        }
    }
}
