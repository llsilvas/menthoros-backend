package br.com.menthoros.backend.services.helper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Limita quantas chamadas <em>reais</em> ao LLM ficam em voo simultaneamente,
 * independente do número de virtual threads do lote.
 *
 * <p>Virtual threads não enfileiram por si — sem este throttle, um lote de 20
 * atletas dispararia 20 chamadas simultâneas ao provedor (risco de 429). O
 * número de permits é configurável via {@code app.batch-plan.llm-concorrencia}
 * (default 4).
 *
 * <p>O {@link Semaphore} é por instância da JVM (não distribuído): com escala
 * horizontal, o teto real vira {@code permits × nº de instâncias}.
 */
@Component
public class LlmConcurrencyLimiter {

    private final Semaphore semaphore;

    public LlmConcurrencyLimiter(@Value("${app.batch-plan.llm-concorrencia:4}") int permits) {
        this.semaphore = new Semaphore(permits);
    }

    /**
     * Executa a chamada ao LLM respeitando o limite de concorrência: adquire um
     * permit antes de chamar e o libera ao final (mesmo em erro).
     *
     * @throws InterruptedException se a thread for interrompida enquanto aguarda um permit
     */
    public <T> T executar(Supplier<T> chamadaLlm) throws InterruptedException {
        semaphore.acquire();
        try {
            return chamadaLlm.get();
        } finally {
            semaphore.release();
        }
    }
}
