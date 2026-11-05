package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.enums.GarminPingStatus;
import br.com.menthoros.backend.repository.GarminPingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GarminPingPurgeSchedulerTest {

    private static final Instant AGORA = Instant.parse("2026-11-05T07:45:00Z");

    @Mock private GarminPingRepository pingRepository;
    private GarminPingPurgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new GarminPingPurgeScheduler(pingRepository, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("expurgar")
    class Expurgar {

        @Test
        @DisplayName("PROCESSED/DISCARDED com mais de 30 dias e ERROR com mais de 90 dias; PENDING e SYNCING nunca")
        void filtros() {
            scheduler.expurgar();

            verify(pingRepository).deleteByStatusInAndCreatedAtBefore(
                    List.of(GarminPingStatus.PROCESSED, GarminPingStatus.DISCARDED), AGORA.minus(Duration.ofDays(30)));
            verify(pingRepository).deleteByStatusInAndCreatedAtBefore(
                    List.of(GarminPingStatus.ERROR), AGORA.minus(Duration.ofDays(90)));
            verifyNoMoreInteractions(pingRepository);
        }

        @Test
        @DisplayName("falha de banco não propaga para o agendador")
        void falhaNaoPropaga() {
            when(pingRepository.deleteByStatusInAndCreatedAtBefore(any(), any()))
                    .thenThrow(new QueryTimeoutException("timeout"));

            assertThatCode(() -> scheduler.expurgar()).doesNotThrowAnyException();
        }
    }
}
