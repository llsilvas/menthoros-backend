package br.com.menthoros.backend.testsupport;

import br.com.menthoros.backend.services.helper.TsbRecalculoExecutor;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Duplo de {@link TsbRecalculoExecutor} para testes unitários: executa o bloco e a consolidação
 * inline, sem transação e sem {@code EntityManager}.
 *
 * <p>Os testes unitários de {@code TsbServiceImpl} montam o serviço à mão, com mocks de repositório
 * e sem contexto Spring — não há proxy, logo não há {@code REQUIRES_NEW}. Este duplo reproduz o
 * comportamento observável que aqueles testes já assumiam: o laço dia a dia roda direto.</p>
 *
 * <p>A propagação transacional de verdade é coberta por {@code TsbRecalculoExecutorIT}, contra banco.
 * Este duplo <b>não</b> serve para isso, e usá-lo com essa intenção esconderia justamente o defeito
 * que a change existe para corrigir.</p>
 */
public class TsbRecalculoExecutorInline extends TsbRecalculoExecutor {

    public TsbRecalculoExecutorInline() {
        super(null, null, null);
    }

    @Override
    public void invalidarCacheMetadados(UUID atletaId, UUID tenantId) {
        // sem CacheManager nos testes unitarios
    }

    @Override
    public void registrarAborto(String fase) {
        // sem MeterRegistry nos testes unitarios
    }

    @Override
    public void recalcularBloco(UUID atletaId, LocalDate inicio, LocalDate fim,
                                BiConsumer<UUID, LocalDate> recalcularDia) {
        LocalDate dataAtual = inicio;
        while (!dataAtual.isAfter(fim)) {
            recalcularDia.accept(atletaId, dataAtual);
            dataAtual = dataAtual.plusDays(1);
        }
    }

    @Override
    public void consolidar(Runnable fase) {
        fase.run();
    }
}
