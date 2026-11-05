package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.enums.GarminPingStatus;
import br.com.menthoros.backend.repository.GarminPingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Limpeza diária da fila de pings do Garmin (garmin-activity-ingestion, D5): {@code PROCESSED} e
 * {@code DISCARDED} saem com 30 dias; {@code ERROR} fica 90 dias para investigação. O que vale para
 * o treinador ({@code IntegracaoExterna.ultimaSincronizacao}) não depende dos pings.
 *
 * <p>Cross-tenant por natureza, como o {@link SyncPullLogPurgeScheduler}: o corte é por idade.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GarminPingPurgeScheduler {

    static final int RETENCAO_DIAS = 30;
    static final int RETENCAO_ERRO_DIAS = 90;

    private final GarminPingRepository pingRepository;
    private final Clock clock;

    /**
     * <p><b>Idempotent:</b> YES — apagar de novo não encontra nada.
     * <p><b>Side Effects:</b> Database delete.
     * <p><b>Tenant-aware:</b> NO — corte por idade em todos os tenants.
     */
    @Scheduled(cron = "0 45 4 * * *", zone = "America/Sao_Paulo")
    public void expurgar() {
        Instant agora = clock.instant();
        try {
            int finalizados = pingRepository.deleteByStatusInAndCreatedAtBefore(
                    List.of(GarminPingStatus.PROCESSED, GarminPingStatus.DISCARDED),
                    agora.minus(RETENCAO_DIAS, ChronoUnit.DAYS));
            int erros = pingRepository.deleteByStatusInAndCreatedAtBefore(
                    List.of(GarminPingStatus.ERROR), agora.minus(RETENCAO_ERRO_DIAS, ChronoUnit.DAYS));
            log.info("[garmin-ping] expurgo diário: {} finalizado(s) e {} com erro", finalizados, erros);
        } catch (RuntimeException ex) {
            // idempotente: o que ficou sai amanhã
            log.warn("[garmin-ping] expurgo diário falhou: {}", ex.getClass().getSimpleName());
        }
    }
}
