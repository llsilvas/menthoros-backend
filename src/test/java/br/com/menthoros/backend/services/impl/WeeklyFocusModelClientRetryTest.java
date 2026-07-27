package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prova o CA3 da change {@code add-external-call-resilience}: timeout não é retentado.
 *
 * <p>Timeout e 5xx/429 são categorias diferentes. 5xx falha rápido e barato, então
 * uma segunda tentativa custa quase nada; timeout falha <em>devagar por definição</em>,
 * e retentar paga o pior caso duas vezes exatamente quando o sistema já está sob
 * pressão. Os dois testes abaixo fixam essa distinção.
 *
 * <p>Precisa do proxy do Spring Retry — {@code @Retryable} não faz nada numa instância
 * construída com {@code new}. Daí o {@link ApplicationContextRunner} com {@code @EnableRetry}.
 */
class WeeklyFocusModelClientRetryTest {

    private final ModelRouter modelRouter = mock(ModelRouter.class);
    private final PromptTemplateLoader templateLoader = mock(PromptTemplateLoader.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withBean(ModelRouter.class, () -> modelRouter)
            .withBean(PromptTemplateLoader.class, () -> templateLoader);

    @TestConfiguration
    @EnableRetry
    static class TestConfig {
        @Bean
        WeeklyFocusModelClient weeklyFocusModelClient(ModelRouter router, PromptTemplateLoader loader) {
            return new WeeklyFocusModelClient(router, loader);
        }
    }

    @Nested
    @DisplayName("redigirFoco")
    class RedigirFoco {

        @Test
        @DisplayName("timeout não dispara segunda tentativa (CA3)")
        void timeoutNaoRetenta() {
            when(templateLoader.loadAndFormat(anyString(), any())).thenReturn("prompt");
            when(modelRouter.route(TaskComplexity.SIMPLE))
                    .thenThrow(new ResourceAccessException("timeout",
                            new SocketTimeoutException("Read timed out")));

            contextRunner.run(ctx -> {
                WeeklyFocusModelClient client = ctx.getBean(WeeklyFocusModelClient.class);

                assertThatThrownBy(() -> client.redigirFoco(revisao()))
                        .isInstanceOf(ResourceAccessException.class);

                verify(modelRouter, times(1)).route(TaskComplexity.SIMPLE);
            });
        }

        @Test
        @DisplayName("5xx/429 continua sendo retentado")
        void transienteRetenta() {
            when(templateLoader.loadAndFormat(anyString(), any())).thenReturn("prompt");
            when(modelRouter.route(TaskComplexity.SIMPLE))
                    .thenThrow(new TransientAiException("503 Service Unavailable"));

            contextRunner.run(ctx -> {
                WeeklyFocusModelClient client = ctx.getBean(WeeklyFocusModelClient.class);

                assertThatThrownBy(() -> client.redigirFoco(revisao()))
                        .isInstanceOf(TransientAiException.class);

                verify(modelRouter, times(2)).route(TaskComplexity.SIMPLE);
            });
        }

        @Test
        @DisplayName("o proxy de retry está realmente ativo no bean sob teste")
        void proxyAtivo() {
            contextRunner.run(ctx ->
                    assertThat(ctx).hasSingleBean(WeeklyFocusModelClient.class));
        }
    }

    private RevisaoSemanal revisao() {
        PlanoSemanal plano = new PlanoSemanal();
        RevisaoSemanal revisao = new RevisaoSemanal();
        revisao.setPlanoSemanal(plano);
        return revisao;
    }
}
