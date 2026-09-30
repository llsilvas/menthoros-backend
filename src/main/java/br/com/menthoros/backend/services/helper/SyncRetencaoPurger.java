package br.com.menthoros.backend.services.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Retenção de 90 dias de {@code tb_sync_pull_log} e {@code tb_sync_atividade_descartada}
 * (fix-sync-cursor-data-loss, design D5/D7).
 *
 * <p>Em lotes e sem transação em volta do laço, de propósito: cada {@code DELETE} commita sozinho, então
 * o expurgo nunca segura lock sobre milhares de linhas que o pull está gravando ao lado. É idempotente,
 * e duas instâncias concorrentes só dividem o trabalho — por isso não precisa de lock distribuído
 * (o projeto não tem ShedLock).</p>
 */
@Component
@RequiredArgsConstructor
public class SyncRetencaoPurger {

    private final JdbcTemplate jdbc;

    public record Expurgo(int registrosDePull, int descartes) {}

    /**
     * Idempotent: YES — reexecutar sem nada antes do corte não apaga nada.
     * Side Effects: Database delete, em lotes.
     * Tenant-aware: NO — o corte é global, por idade.
     */
    public Expurgo expurgarAntesDe(Instant corte, int lote) {
        int logs = emLotes("""
                DELETE FROM tb_sync_pull_log WHERE id IN (
                    SELECT id FROM tb_sync_pull_log WHERE executado_em < ? LIMIT ?)
                """, corte, lote);
        int descartes = emLotes("""
                DELETE FROM tb_sync_atividade_descartada WHERE id IN (
                    SELECT id FROM tb_sync_atividade_descartada WHERE atualizado_em < ? LIMIT ?)
                """, corte, lote);
        return new Expurgo(logs, descartes);
    }

    private int emLotes(String sql, Instant corte, int lote) {
        int total = 0;
        int apagadas;
        do {
            apagadas = jdbc.update(sql, Timestamp.from(corte), lote);
            total += apagadas;
        } while (apagadas == lote);
        return total;
    }
}
