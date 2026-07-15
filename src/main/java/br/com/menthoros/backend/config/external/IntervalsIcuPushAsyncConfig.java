package br.com.menthoros.backend.config.external;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Pool dedicado ao push de treinos para o intervals.icu. Push é rápido (~200ms HTTP,
 * teto de 10s pelo responseTimeout do WebClient) mas não pode competir com as chamadas
 * LLM do workoutAnalysisExecutor (até 30s).
 */
@Configuration
public class IntervalsIcuPushAsyncConfig {

    @Bean("intervalsIcuPushExecutor")
    public Executor intervalsIcuPushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("INTERVALS-ICU-PUSH-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
