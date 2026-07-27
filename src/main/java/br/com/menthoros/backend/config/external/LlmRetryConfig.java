package br.com.menthoros.backend.config.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

/**
 * Substitui o {@code RetryTemplate} do Spring AI para que timeout deixe de ser
 * retentado (ADR-0008).
 *
 * <p>O bean do {@code SpringAiRetryAutoConfiguration} é
 * {@code @ConditionalOnMissingBean}, então declarar o nosso o desliga. Ele
 * retentava {@code TransientAiException} <b>e</b> {@code ResourceAccessException},
 * e o segundo é o timeout: com o default de 10 tentativas, a rota {@code plano}
 * (teto de 120s) teria pior caso de ~20 minutos — segurando uma conexão do pool,
 * já que a geração de plano roda dentro de uma {@code @Transactional}. O teto por
 * rota da task 1.2 não teria efeito prático.
 *
 * <p><b>Consequência aceita:</b> erros de transporte deixam de ser retentados aqui,
 * inclusive os que falham rápido (conexão recusada). O {@code RetryTemplateBuilder}
 * não deixa combinar lista de inclusão com lista de exclusão, e não há como
 * distinguir "conexão recusada" de "read timeout" por classe — ambos chegam como
 * {@code ResourceAccessException}. Entre perder o retry de um blip de conexão e
 * pagar o timeout 10 vezes sob pressão, a escolha é a primeira.
 *
 * <p>{@code maxAttempts} e backoff seguem vindo de {@code spring.ai.retry}.
 */
@Slf4j
@Configuration
public class LlmRetryConfig {

    @Bean
    public RetryTemplate retryTemplate(SpringAiRetryProperties properties) {
        return RetryTemplate.builder()
                .maxAttempts(properties.getMaxAttempts())
                .retryOn(TransientAiException.class)
                .exponentialBackoff(
                        properties.getBackoff().getInitialInterval(),
                        properties.getBackoff().getMultiplier(),
                        properties.getBackoff().getMaxInterval())
                .withListener(new RetryListener() {
                    @Override
                    public <T, E extends Throwable> void onError(RetryContext context,
                                                                 RetryCallback<T, E> callback,
                                                                 Throwable throwable) {
                        log.warn("[llm-retry] tentativa {} falhou: {}",
                                context.getRetryCount(), throwable.getMessage(), throwable);
                    }
                })
                .build();
    }
}
