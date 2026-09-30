package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.services.helper.SyncRetencaoPurger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncPullLogPurgeScheduler: corte de 90 dias pelo relógio injetado")
class SyncPullLogPurgeSchedulerTest {

    private static final Instant AGORA = Instant.parse("2026-09-30T07:30:00Z");

    @Mock
    private SyncRetencaoPurger purger;

    private SyncPullLogPurgeScheduler scheduler() {
        return new SyncPullLogPurgeScheduler(purger, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("corte é exatamente 90 dias antes do relógio, em lotes de 1000")
    void corteE90DiasAntes() {
        when(purger.expurgarAntesDe(any(), anyInt())).thenReturn(new SyncRetencaoPurger.Expurgo(0, 0));

        scheduler().expurgar();

        verify(purger).expurgarAntesDe(AGORA.minus(90, ChronoUnit.DAYS), 1000);
    }

    @Test
    @DisplayName("falha no expurgo é logada e não propaga (o scheduler roda de novo amanhã)")
    void falhaNaoPropaga() {
        when(purger.expurgarAntesDe(any(), anyInt())).thenThrow(new IllegalStateException("banco fora"));

        assertThatCode(() -> scheduler().expurgar()).doesNotThrowAnyException();
    }
}
