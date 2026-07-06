package br.com.menthoros.backend.config.external;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Executor da geração de planos em lote — uma virtual thread por atleta.
 *
 * <p>A geração chama o LLM (I/O-bound, ~80s por tentativa): dimensionar um pool
 * de platform threads seria desperdício e serializaria o lote. Virtual threads
 * (Java 21) bloqueiam "de graça" sem prender uma platform thread do pool HTTP.
 *
 * <p>Não repete {@code @EnableAsync}: já está ativo no contexto via
 * {@link StravaWebhookAsyncConfig}. O throttle da concorrência real ao LLM é do
 * {@code LlmConcurrencyLimiter} (Semaphore), não deste executor.
 */
@Configuration
public class BatchPlanAsyncConfig {

    @Bean("batchPlanExecutor")
    public Executor batchPlanExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
