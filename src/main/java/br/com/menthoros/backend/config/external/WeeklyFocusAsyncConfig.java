package br.com.menthoros.backend.config.external;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Pool dedicado para a narrativa do foco semanal (D8).
 *
 * <p>Separado do executor padrão porque o encerramento em lote
 * ({@code EncerramentoSemanaScheduler}) pode disparar N narrativas de uma vez; sem pool próprio,
 * essas chamadas competiriam com as demais tarefas assíncronas do módulo. Menor que o pool de
 * análise de treino: é uma chamada por atleta por semana, não por treino registrado.
 */
@Configuration
public class WeeklyFocusAsyncConfig {

    @Bean("weeklyFocusExecutor")
    public Executor weeklyFocusExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("WEEKLY-FOCUS-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
