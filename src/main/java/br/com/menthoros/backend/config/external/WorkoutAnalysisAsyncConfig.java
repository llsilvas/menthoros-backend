package br.com.menthoros.backend.config.external;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Pool dedicado para análise de treino via IA.
 * Chamadas ao Claude Sonnet podem durar até 30s — pool separado evita
 * bloquear o executor padrão de outras tarefas assíncronas.
 */
@Configuration
public class WorkoutAnalysisAsyncConfig {

    @Bean("workoutAnalysisExecutor")
    public Executor workoutAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("WORKOUT-ANALYSIS-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
