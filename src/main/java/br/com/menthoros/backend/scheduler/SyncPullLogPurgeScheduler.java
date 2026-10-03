package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.services.helper.SyncRetencaoPurger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Expurgo diário dos registros de pull e dos descartes com mais de {@link #RETENCAO_DIAS} dias
 * (fix-sync-cursor-data-loss, design D5/D7). O registro de pull é instrumento de medição, não
 * histórico; o descarte antigo já saiu da janela do pull, então apagá-lo não reabre nada.
 *
 * <p>Cross-tenant por natureza, como o {@link LlmCallRetentionScheduler}: o corte é por idade.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncPullLogPurgeScheduler {

    static final int RETENCAO_DIAS = 90;
    static final int LOTE = 1000;

    private final SyncRetencaoPurger purger;
    private final Clock clock;

    /** 4h30 (Brasília): depois das purgas das 3h, longe dos ciclos de pull de 2 em 2 horas. */
    @Scheduled(cron = "0 30 4 * * *", zone = "America/Sao_Paulo")
    public void expurgar() {
        Instant corte = clock.instant().minus(RETENCAO_DIAS, ChronoUnit.DAYS);
        try {
            SyncRetencaoPurger.Expurgo expurgo = purger.expurgarAntesDe(corte, LOTE);
            log.info("[sync-retencao] expurgo diário: {} registro(s) de pull e {} descarte(s) (retenção de {} dias)",
                    expurgo.registrosDePull(), expurgo.descartes(), RETENCAO_DIAS);
        } catch (RuntimeException ex) {
            // idempotente: o que ficou sai amanhã
            log.warn("[sync-retencao] expurgo diário falhou: {}", ex.getMessage());
        }
    }
}
