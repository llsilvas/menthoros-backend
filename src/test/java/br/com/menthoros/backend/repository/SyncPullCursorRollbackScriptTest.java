package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Script de rollback de fix-sync-cursor-data-loss (design, "Rollback"). Depois do deploy, os schedulers
 * gravam {@code ultima_sincronizacao = now} mesmo em pull PARCIAL; o código antigo lê esse campo como
 * cursor. Reverter só o binário faria o pull antigo pular a janela parcial — o script devolve ao campo
 * antigo o progresso confirmado. Lido do arquivo versionado, não de uma cópia: o teste é do script real.
 */
@DisplayName("Rollback fix-sync-cursor-data-loss: ultima_sincronizacao volta ao progresso confirmado")
class SyncPullCursorRollbackScriptTest extends AbstractIntegrationTest {

    private static final Path SCRIPT = Path.of("docs/rollback/fix-sync-cursor-data-loss.sql");

    @Autowired
    private JdbcTemplate jdbc;

    private UUID integracao(Instant ultimaSincronizacao, Instant pullCursor) {
        var tenant = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_assessoria (id, nome, dominio, plano, ativo, max_atletas, max_tecnicos)
                VALUES (?, ?, ?, 'GRATUITO', true, 10, 1)
                """, tenant, "Assessoria " + tenant, "slug-" + tenant);
        var atleta = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_atleta (id, tenant_id, nome, email, nivel_experiencia, ativo)
                VALUES (?, ?, 'Maria', ?, 'INICIANTE', 'ATIVO')
                """, atleta, tenant, "maria-" + atleta + "@exemplo.com");
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_integracao_externa (id, tenant_id, atleta_id, plataforma, ativo, ultima_sincronizacao, pull_cursor)
                VALUES (?, ?, ?, 'STRAVA', true, ?, ?)
                """, id, tenant, atleta, ts(ultimaSincronizacao), ts(pullCursor));
        return id;
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant ultima(UUID id) {
        Timestamp ts = jdbc.queryForObject(
                "SELECT ultima_sincronizacao FROM tb_integracao_externa WHERE id = ?", Timestamp.class, id);
        return ts == null ? null : ts.toInstant();
    }

    private void rodarScript() throws IOException {
        jdbc.execute(Files.readString(SCRIPT));
    }

    @Test
    @DisplayName("pull PARCIAL: ultima_sincronizacao adiantada volta para o pull_cursor")
    void parcialVoltaAoCursor() throws IOException {
        var cursor = Instant.parse("2026-09-10T00:00:00Z");
        var parcial = integracao(Instant.parse("2026-09-30T12:00:00Z"), cursor);

        rodarScript();

        assertThat(ultima(parcial)).isEqualTo(cursor);
    }

    @Test
    @DisplayName("ultima_sincronizacao nula ganha o pull_cursor; cursor nulo ou já atrás não muda nada")
    void casosDeBorda() throws IOException {
        var cursor = Instant.parse("2026-09-10T00:00:00Z");
        var semUltima = integracao(null, cursor);
        var semCursor = integracao(Instant.parse("2026-09-30T12:00:00Z"), null);
        var ultimaAtras = integracao(Instant.parse("2026-09-01T00:00:00Z"), cursor);

        rodarScript();

        assertThat(ultima(semUltima)).isEqualTo(cursor);
        assertThat(ultima(semCursor)).isEqualTo(Instant.parse("2026-09-30T12:00:00Z"));
        assertThat(ultima(ultimaAtras)).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    @DisplayName("idempotente: rodar duas vezes dá o mesmo resultado")
    void idempotente() throws IOException {
        var cursor = Instant.parse("2026-09-10T00:00:00Z");
        var parcial = integracao(Instant.parse("2026-09-30T12:00:00Z"), cursor);

        rodarScript();
        rodarScript();

        assertThat(ultima(parcial)).isEqualTo(cursor);
    }
}
