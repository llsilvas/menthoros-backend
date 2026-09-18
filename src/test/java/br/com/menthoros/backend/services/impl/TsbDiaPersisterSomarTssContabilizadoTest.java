package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.TreinoRealizado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prova o comportamento de {@code somarTssContabilizado} (CA3, `backfill-tss-legado-producao`)
 * depois da remoção do fallback "nulo → calcula e persiste agora": {@code tssCalculado} nulo
 * passa a contar como 0 na soma do dia, sem cálculo silencioso nem persistência.
 *
 * <p>Migrado de {@code TsbServiceImplSomarTssContabilizadoTest}
 * (refactor-threshold-call-outside-transaction, seção 3) — o método saiu de {@code TsbServiceImpl}
 * pra {@code TsbDiaPersister}. Package-private direto (sem reflection): o método já não é mais
 * privado nesta classe, então não precisa de reflection pra chamar.
 *
 * <p>Todos os colaboradores são passados como {@code null} de propósito — se o fallback ainda
 * existisse e chamasse qualquer um deles para um treino com {@code tssCalculado} nulo, este teste
 * lançaria {@code NullPointerException} em vez de afirmar o total.</p>
 */
class TsbDiaPersisterSomarTssContabilizadoTest {

    @Test
    @DisplayName("tssCalculado nulo conta como 0 — sem calcular nem persistir on-the-fly")
    void tssNuloContaComoZero_semCalcularNemPersistir() {
        TsbDiaPersister persister = new TsbDiaPersister(
                null, null, null, null, null, null, null);

        TreinoRealizado semTss = new TreinoRealizado();
        semTss.setTssCalculado(null);

        TreinoRealizado comTss = new TreinoRealizado();
        comTss.setTssCalculado(50);

        int total = persister.somarTssContabilizado(List.of(semTss, comTss));

        assertEquals(50, total,
                "treino com tssCalculado nulo deve contar como 0, não ser calculado on-the-fly");
    }
}
