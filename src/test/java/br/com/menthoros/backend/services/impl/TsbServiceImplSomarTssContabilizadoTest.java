package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.TreinoRealizado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prova o comportamento de {@code somarTssContabilizado} (CA3, `backfill-tss-legado-producao`)
 * depois da remoção do fallback "nulo → calcula e persiste agora": {@code tssCalculado} nulo
 * passa a contar como 0 na soma do dia, sem cálculo silencioso nem persistência.
 *
 * <p>Todos os colaboradores são passados como {@code null} de propósito — se o fallback ainda
 * existisse e chamasse qualquer um deles para um treino com {@code tssCalculado} nulo, este teste
 * lançaria {@code NullPointerException} em vez de afirmar o total.</p>
 */
class TsbServiceImplSomarTssContabilizadoTest {

    @Test
    @DisplayName("tssCalculado nulo conta como 0 — sem calcular nem persistir on-the-fly")
    void tssNuloContaComoZero_semCalcularNemPersistir() throws Exception {
        TsbServiceImpl service = new TsbServiceImpl(
                null, null, null, null, null, null, null, null);

        TreinoRealizado semTss = new TreinoRealizado();
        semTss.setTssCalculado(null);

        TreinoRealizado comTss = new TreinoRealizado();
        comTss.setTssCalculado(50);

        Method somarTssContabilizado = TsbServiceImpl.class
                .getDeclaredMethod("somarTssContabilizado", List.class);
        somarTssContabilizado.setAccessible(true);

        int total = (int) somarTssContabilizado.invoke(service, List.of(semTss, comTss));

        assertEquals(50, total,
                "treino com tssCalculado nulo deve contar como 0, não ser calculado on-the-fly");
    }
}
