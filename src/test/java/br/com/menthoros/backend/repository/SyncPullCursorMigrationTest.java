package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V98 — cursor exclusivo do pull, registro de pull e descarte registrado (fix-sync-cursor-data-loss,
 * design D5/D6/D7).
 *
 * <p>O backfill é testado reexecutando o próprio script (idempotente por desenho: {@code IF NOT
 * EXISTS} + {@code WHERE pull_cursor IS NULL}), depois de inserir integrações com e sem
 * {@code ultima_sincronizacao}. Nível SQL de propósito: o que está sob teste é o SQL da migration.</p>
 */
@DisplayName("V98 pull_cursor + tb_sync_pull_log + tb_sync_atividade_descartada")
class SyncPullCursorMigrationTest extends AbstractIntegrationTest {

    private static final String V98 = "db/migration/V98__Add_pull_cursor_and_sync_pull_log.sql";

    @Autowired
    private JdbcTemplate jdbc;

    private UUID inserirAssessoria() {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_assessoria (id, nome, dominio, plano, ativo, max_atletas, max_tecnicos)
                VALUES (?, ?, ?, 'GRATUITO', true, 10, 1)
                """, id, "Assessoria " + id, "slug-" + id);
        return id;
    }

    private UUID inserirAtleta(UUID tenantId) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_atleta (id, tenant_id, nome, email, nivel_experiencia, ativo)
                VALUES (?, ?, 'Maria', ?, 'INICIANTE', 'ATIVO')
                """, id, tenantId, "maria-" + id + "@exemplo.com");
        return id;
    }

    private UUID inserirIntegracao(UUID tenantId, UUID atletaId, Instant ultimaSincronizacao) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_integracao_externa (id, tenant_id, atleta_id, plataforma, ativo, ultima_sincronizacao)
                VALUES (?, ?, ?, 'STRAVA', true, ?)
                """, id, tenantId, atletaId,
                ultimaSincronizacao == null ? null : Timestamp.from(ultimaSincronizacao));
        return id;
    }

    private Instant pullCursor(UUID integracaoId) {
        Timestamp ts = jdbc.queryForObject(
                "SELECT pull_cursor FROM tb_integracao_externa WHERE id = ?", Timestamp.class, integracaoId);
        return ts == null ? null : ts.toInstant();
    }

    private void reexecutarV98() throws IOException {
        jdbc.execute(new ClassPathResource(V98).getContentAsString(StandardCharsets.UTF_8));
    }

    private void inserirPullLog(UUID tenantId, UUID atletaId, String resultado) {
        jdbc.update("""
                INSERT INTO tb_sync_pull_log (tenant_id, atleta_id, plataforma, executado_em, resultado)
                VALUES (?, ?, 'STRAVA', NOW(), ?)
                """, tenantId, atletaId, resultado);
    }

    private void inserirDescarte(UUID tenantId, UUID atletaId, String externalId) {
        jdbc.update("""
                INSERT INTO tb_sync_atividade_descartada (tenant_id, atleta_id, plataforma, external_id, motivo)
                VALUES (?, ?, 'INTERVALS_ICU', ?, 'PERMANENTE')
                """, tenantId, atletaId, externalId);
    }

    @Nested
    @DisplayName("pull_cursor em tb_integracao_externa")
    class PullCursor {

        @Test
        @DisplayName("backfill copia ultima_sincronizacao; nula continua nula")
        void backfill() throws IOException {
            var tenant = inserirAssessoria();
            var ultima = Instant.parse("2026-09-10T12:00:00Z");
            var comSync = inserirIntegracao(tenant, inserirAtleta(tenant), ultima);
            var semSync = inserirIntegracao(tenant, inserirAtleta(tenant), null);
            jdbc.update("UPDATE tb_integracao_externa SET pull_cursor = NULL WHERE id IN (?, ?)", comSync, semSync);

            reexecutarV98();

            assertThat(pullCursor(comSync)).isEqualTo(ultima);
            assertThat(pullCursor(semSync)).isNull();
        }

        @Test
        @DisplayName("reexecutar não sobrescreve um pull_cursor já preenchido")
        void naoSobrescreveCursorExistente() throws IOException {
            var tenant = inserirAssessoria();
            var integracao = inserirIntegracao(tenant, inserirAtleta(tenant), Instant.parse("2026-09-20T00:00:00Z"));
            var cursor = Instant.parse("2026-09-01T00:00:00Z").truncatedTo(ChronoUnit.MICROS);
            jdbc.update("UPDATE tb_integracao_externa SET pull_cursor = ? WHERE id = ?", Timestamp.from(cursor), integracao);

            reexecutarV98();

            assertThat(pullCursor(integracao)).isEqualTo(cursor);
        }
    }

    @Nested
    @DisplayName("tb_sync_pull_log")
    class PullLog {

        @Test
        @DisplayName("resultado só aceita COMPLETO, PARCIAL e FALHA")
        void checkResultado() {
            var tenant = inserirAssessoria();
            var atleta = inserirAtleta(tenant);

            assertThatCode(() -> inserirPullLog(tenant, atleta, "COMPLETO")).doesNotThrowAnyException();
            assertThatCode(() -> inserirPullLog(tenant, atleta, "PARCIAL")).doesNotThrowAnyException();
            assertThatCode(() -> inserirPullLog(tenant, atleta, "FALHA")).doesNotThrowAnyException();
            assertThatThrownBy(() -> inserirPullLog(tenant, atleta, "OK"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("insercoes e ignoradas têm default 0; excluir o atleta apaga os registros")
        void defaultsECascata() {
            var tenant = inserirAssessoria();
            var atleta = inserirAtleta(tenant);
            inserirPullLog(tenant, atleta, "COMPLETO");

            assertThat(jdbc.queryForObject(
                    "SELECT insercoes + ignoradas FROM tb_sync_pull_log WHERE atleta_id = ?", Integer.class, atleta))
                    .isZero();

            jdbc.update("DELETE FROM tb_atleta WHERE id = ?", atleta);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM tb_sync_pull_log WHERE atleta_id = ?", Integer.class, atleta))
                    .isZero();
        }
    }

    @Nested
    @DisplayName("tb_sync_atividade_descartada")
    class Descarte {

        @Test
        @DisplayName("uma linha por (atleta, plataforma, external_id); tentativas começa em 0 e descartada_em nula")
        void unicidadeEDefaults() {
            var tenant = inserirAssessoria();
            var atleta = inserirAtleta(tenant);
            inserirDescarte(tenant, atleta, "i123");

            assertThatThrownBy(() -> inserirDescarte(tenant, atleta, "i123"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatCode(() -> inserirDescarte(tenant, atleta, "i124")).doesNotThrowAnyException();
            assertThat(jdbc.queryForObject("""
                    SELECT tentativas = 0 AND descartada_em IS NULL
                      FROM tb_sync_atividade_descartada WHERE atleta_id = ? AND external_id = 'i123'
                    """, Boolean.class, atleta)).isTrue();
        }
    }
}
