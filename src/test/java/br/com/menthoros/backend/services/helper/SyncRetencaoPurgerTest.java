package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Expurgo em lotes de {@code tb_sync_pull_log} e {@code tb_sync_atividade_descartada} (design D5/D7). */
@DisplayName("SyncRetencaoPurger: apaga o que passou do corte, em lotes")
class SyncRetencaoPurgerTest extends AbstractIntegrationTest {

    @Autowired
    private SyncRetencaoPurger purger;
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

    private void logs(UUID tenant, UUID atleta, int quantidade, Instant executadoEm) {
        jdbc.batchUpdate("""
                INSERT INTO tb_sync_pull_log (tenant_id, atleta_id, plataforma, executado_em, resultado)
                VALUES (?, ?, 'STRAVA', ?, 'COMPLETO')
                """, java.util.stream.IntStream.range(0, quantidade)
                .mapToObj(i -> new Object[]{tenant, atleta, Timestamp.from(executadoEm)}).toList());
    }

    private void descartes(UUID tenant, UUID atleta, int quantidade, Instant atualizadoEm) {
        List<Object[]> linhas = java.util.stream.IntStream.range(0, quantidade)
                .mapToObj(i -> new Object[]{tenant, atleta, "ext-" + UUID.randomUUID(), Timestamp.from(atualizadoEm)})
                .toList();
        jdbc.batchUpdate("""
                INSERT INTO tb_sync_atividade_descartada (tenant_id, atleta_id, plataforma, external_id, motivo, atualizado_em)
                VALUES (?, ?, 'STRAVA', ?, 'PERMANENTE', ?)
                """, linhas);
    }

    private int contar(String tabela, UUID atleta) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela + " WHERE atleta_id = ?", Integer.class, atleta);
    }

    @Test
    @DisplayName("2500 antigos + 10 recentes em cada tabela → sobram os 10, em lotes de 1000")
    void expurgaEmLotes() {
        var tenant = UUID.randomUUID();
        var atleta = inserirAtleta(tenant);
        Instant agora = Instant.now();
        Instant corte = agora.minus(90, ChronoUnit.DAYS);
        logs(tenant, atleta, 2500, corte.minus(1, ChronoUnit.DAYS));
        logs(tenant, atleta, 10, agora);
        descartes(tenant, atleta, 2500, corte.minus(1, ChronoUnit.DAYS));
        descartes(tenant, atleta, 10, agora);

        SyncRetencaoPurger.Expurgo expurgo = purger.expurgarAntesDe(corte, 1000);

        assertThat(contar("tb_sync_pull_log", atleta)).isEqualTo(10);
        assertThat(contar("tb_sync_atividade_descartada", atleta)).isEqualTo(10);
        assertThat(expurgo.registrosDePull()).isGreaterThanOrEqualTo(2500);
        assertThat(expurgo.descartes()).isGreaterThanOrEqualTo(2500);
    }

    @Test
    @DisplayName("nada antes do corte → zero, sem erro")
    void nadaAExpurgar() {
        var tenant = UUID.randomUUID();
        var atleta = inserirAtleta(tenant);
        logs(tenant, atleta, 3, Instant.now());

        purger.expurgarAntesDe(Instant.now().minus(90, ChronoUnit.DAYS), 1000);

        assertThat(contar("tb_sync_pull_log", atleta)).isEqualTo(3);
    }
}
