package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.SyncPullLog;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.repository.SyncPullLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Escrita do registro de pull (fix-sync-cursor-data-loss, design D5). Bean próprio de propósito: os
 * schedulers chamam de fora, então o {@code REQUIRES_NEW} passa pelo proxy (auto-invocação não
 * passaria). Assim, o rollback de quem chamou não apaga o registro de que o pull falhou.
 *
 * <p>Best-effort fica no chamador: o {@code catch} precisa estar fora desta transação (mesmo motivo de
 * {@link LlmCallLedgerWriter}).</p>
 */
@Component
@RequiredArgsConstructor
public class SyncPullLogWriter {

    static final int TIMEOUT_SEGUNDOS = 5;

    private final SyncPullLogRepository repository;

    /**
     * Idempotent: NÃO — uma linha por ciclo de pull.
     * Side Effects: Database insert.
     * Tenant-aware: SIM — grava o tenant da integração recebido.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = TIMEOUT_SEGUNDOS)
    public void registrar(UUID tenantId, UUID atletaId, FonteDados plataforma, PullResultado resultado,
                          Instant executadoEm) {
        SyncPullLog log = new SyncPullLog();
        log.setTenantId(tenantId);
        log.setAtletaId(atletaId);
        log.setPlataforma(plataforma);
        log.setExecutadoEm(executadoEm);
        log.setResultado(resultado.resultado());
        log.setErroCategoria(resultado.erro());
        log.setInsercoes(resultado.insercoes());
        log.setIgnoradas(resultado.ignoradas());
        repository.save(log);
    }
}
