package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.events.FitWorkoutImportedEvent;
import br.com.menthoros.backend.services.helper.FitWorkoutReconciler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Concilia o realizado de .fit depois do commit da ingestão (fix-garmin-realizado-pos-ingestao).
 *
 * <p>Deliberadamente SEM {@code @Transactional}: a transação é do {@link FitWorkoutReconciler}
 * ({@code REQUIRES_NEW}), e o {@code try/catch} aqui fica fora dela para pegar também a falha do
 * commit. O link do FIT Garmin vale uma vez só — a ingestão já commitou e nada aqui pode
 * revertê-la nem chegar ao chamador (scheduler de pings ou upload manual).</p>
 *
 * <p>Síncrono: roda na thread de quem commitou. Se {@code persistir} rodar sob uma transação
 * externa, o evento só dispara no commit dela. Custo: durante o AFTER_COMMIT a conexão da ingestão
 * ainda não voltou ao pool, e o {@code REQUIRES_NEW} pega uma segunda — por alguns milissegundos,
 * duas conexões por ping/upload (o scheduler de pings é uma thread só).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FitWorkoutReconciliationListener {

    private final FitWorkoutReconciler reconciler;

    /**
     * <p><b>Idempotent:</b> YES — o reconciler ignora realizado já conciliado.
     * <p><b>Side Effects:</b> Database update/insert da conciliação (via reconciler); nenhuma exceção
     * propaga.
     * <p><b>Tenant-aware:</b> YES — usa o tenant do evento.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFitWorkoutImported(FitWorkoutImportedEvent event) {
        try {
            reconciler.reconcile(event.treinoRealizadoId(), event.tenantId());
        } catch (Exception e) {
            // O realizado fica com reconciliation_status nulo, como antes desta change; o log com o id
            // é o sinal para recuperá-lo pelo mesmo SQL do backfill.
            log.error("Falha ao conciliar realizado do FIT {} (tenant {}): {}",
                    event.treinoRealizadoId(), event.tenantId(), e.getMessage(), e);
        }
    }
}
