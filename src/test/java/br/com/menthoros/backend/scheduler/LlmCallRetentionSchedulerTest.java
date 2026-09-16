package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.services.helper.LlmCallLedger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O corte é sempre "agora (do Clock) menos 90 dias" (design D8) — o Clock fixo é o que torna esse
 * cálculo verificável sem esperar 90 dias de verdade.
 */
@ExtendWith(MockitoExtension.class)
class LlmCallRetentionSchedulerTest {

    @Mock
    private LlmCallLedger llmCallLedger;

    private static final Instant AGORA = Instant.parse("2026-09-13T06:15:00Z");

    private LlmCallRetentionScheduler scheduler() {
        return new LlmCallRetentionScheduler(llmCallLedger, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("purgarRespostasAntigas")
    class PurgarRespostasAntigas {

        @Test
        @DisplayName("corte é exatamente 90 dias antes do relógio injetado")
        void corteE90DiasAntes() {
            when(llmCallLedger.purgarRespostasAntigas(any())).thenReturn(0);

            scheduler().purgarRespostasAntigas();

            ArgumentCaptor<Instant> corte = ArgumentCaptor.forClass(Instant.class);
            verify(llmCallLedger).purgarRespostasAntigas(corte.capture());
            assertThat(corte.getValue()).isEqualTo(AGORA.minus(90, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("total anulado > 0 não lança e não precisa de asserção além da chamada")
        void totalMaiorQueZero() {
            when(llmCallLedger.purgarRespostasAntigas(any())).thenReturn(2);

            scheduler().purgarRespostasAntigas();

            verify(llmCallLedger).purgarRespostasAntigas(any());
        }

        @Test
        @DisplayName("segunda execução no mesmo dia (0 anuladas) não lança")
        void segundaExecucaoZeroAnuladas() {
            when(llmCallLedger.purgarRespostasAntigas(any())).thenReturn(0);

            scheduler().purgarRespostasAntigas();
            scheduler().purgarRespostasAntigas();

            verify(llmCallLedger, org.mockito.Mockito.times(2)).purgarRespostasAntigas(any());
        }
    }
}
