package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.enums.FonteDados;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Descarte registrado (fix-sync-cursor-data-loss, design D7). */
@DisplayName("SyncDescarteWriter: permanente na 1ª, falha recorrente na 3ª")
class SyncDescarteWriterTest extends AbstractIntegrationTest {

    @Autowired
    private SyncDescarteWriter writer;
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
    @DisplayName("permanente descarta na primeira vez")
    void permanente() {
        var tenant = UUID.randomUUID();
        var atleta = inserirAtleta(tenant);

        writer.registrarPermanente(tenant, atleta, FonteDados.INTERVALS_ICU, "i1");

        assertThat(writer.descartadas(tenant, atleta, FonteDados.INTERVALS_ICU)).containsExactly("i1");
    }

    @Test
    @DisplayName("falha inesperada só descarta na terceira tentativa")
    void falhaRecorrente() {
        var tenant = UUID.randomUUID();
        var atleta = inserirAtleta(tenant);

        assertThat(writer.registrarFalha(tenant, atleta, FonteDados.STRAVA, "42")).isFalse();
        assertThat(writer.registrarFalha(tenant, atleta, FonteDados.STRAVA, "42")).isFalse();
        assertThat(writer.descartadas(tenant, atleta, FonteDados.STRAVA)).isEmpty();

        assertThat(writer.registrarFalha(tenant, atleta, FonteDados.STRAVA, "42")).isTrue();
        assertThat(writer.descartadas(tenant, atleta, FonteDados.STRAVA)).containsExactly("42");
        assertThat(jdbc.queryForObject("""
                SELECT motivo FROM tb_sync_atividade_descartada WHERE atleta_id = ? AND external_id = '42'
                """, String.class, atleta)).isEqualTo("FALHA_RECORRENTE");
    }

    @Test
    @DisplayName("rollback de quem chamou não desfaz a contagem de tentativas")
    void sobreviveAoRollback() {
        var tenant = UUID.randomUUID();
        var atleta = inserirAtleta(tenant);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                writer.registrarFalha(tenant, atleta, FonteDados.STRAVA, "99");
                throw new IllegalStateException("rollback da atividade");
            })).isInstanceOf(IllegalStateException.class);
        }

        assertThat(writer.descartadas(tenant, atleta, FonteDados.STRAVA)).containsExactly("99");
    }

    @Test
    @DisplayName("descartadas filtra por tenant e plataforma")
    void isolamento() {
        var tenant = UUID.randomUUID();
        var atleta = inserirAtleta(tenant);
        writer.registrarPermanente(tenant, atleta, FonteDados.INTERVALS_ICU, "i1");

        assertThat(writer.descartadas(UUID.randomUUID(), atleta, FonteDados.INTERVALS_ICU)).isEmpty();
        assertThat(writer.descartadas(tenant, atleta, FonteDados.STRAVA)).isEmpty();
    }
}
