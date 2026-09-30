package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.enums.ErroCategoriaPull;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.ResultadoPull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CA8 no nível do writer: o registro do pull sobrevive ao rollback de quem o chamou
 * (fix-sync-cursor-data-loss, design D5).
 */
@DisplayName("SyncPullLogWriter: registro em transação própria")
class SyncPullLogWriterTest extends AbstractIntegrationTest {

    @Autowired
    private SyncPullLogWriter writer;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID inserirAtleta(UUID tenantId) {
        jdbc.update("""
                INSERT INTO tb_assessoria (id, nome, dominio, plano, ativo, max_atletas, max_tecnicos)
                VALUES (?, ?, ?, 'GRATUITO', true, 10, 1)
                """, tenantId, "Assessoria " + tenantId, "slug-" + tenantId);
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_atleta (id, tenant_id, nome, email, nivel_experiencia, ativo)
                VALUES (?, ?, 'Maria', ?, 'INICIANTE', 'ATIVO')
                """, id, tenantId, "maria-" + id + "@exemplo.com");
        return id;
    }

    @Test
    @DisplayName("chamado dentro de uma transação que faz rollback, o registro persiste com o tenant")
    void sobreviveAoRollbackDoChamador() {
        var tenant = UUID.randomUUID();
        var atleta = inserirAtleta(tenant);
        var resultado = new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.RATE_LIMIT, 3, 1);
        var executadoEm = Instant.parse("2026-09-30T10:00:00Z");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            writer.registrar(tenant, atleta, FonteDados.STRAVA, resultado, executadoEm);
            throw new IllegalStateException("rollback do lote");
        })).isInstanceOf(IllegalStateException.class);

        Map<String, Object> linha = jdbc.queryForMap("""
                SELECT tenant_id, plataforma, resultado, erro_categoria, insercoes, ignoradas
                  FROM tb_sync_pull_log WHERE atleta_id = ?
                """, atleta);
        assertThat(linha)
                .containsEntry("tenant_id", tenant)
                .containsEntry("plataforma", "STRAVA")
                .containsEntry("resultado", "PARCIAL")
                .containsEntry("erro_categoria", "RATE_LIMIT")
                .containsEntry("insercoes", 3)
                .containsEntry("ignoradas", 1);
    }
}
