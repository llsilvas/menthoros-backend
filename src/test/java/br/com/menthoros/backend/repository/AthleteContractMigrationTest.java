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
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V96 — o que o contrato do atleta depende do banco (design D1/D7): índice parcial de um
 * contrato ativo por atleta, UNIQUE (contract_id, due_date), CHECKs, cascata na exclusão do
 * atleta e o backfill dos campos legados.
 *
 * <p>O backfill é testado reexecutando o próprio script V96 (idempotente por desenho:
 * {@code IF NOT EXISTS} + {@code NOT EXISTS}), depois de inserir atletas com dado legado.
 * Nível SQL de propósito: o que está sob teste são as constraints e o SQL da migration.</p>
 */
@DisplayName("V96 tb_athlete_contract + tb_athlete_invoice: constraints e backfill")
class AthleteContractMigrationTest extends AbstractIntegrationTest {

    private static final String V96 = "db/migration/V96__Create_tb_athlete_contract_and_tb_athlete_invoice.sql";

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

    private UUID inserirAtleta(UUID tenantId, String tipoPlanoLegado, LocalDate vencimentoLegado) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_atleta (id, tenant_id, nome, email, nivel_experiencia, tipo_plano_atleta, data_vencimento_plano)
                VALUES (?, ?, 'Maria', ?, 'INICIANTE', ?, ?)
                """, id, tenantId, "maria-" + id + "@exemplo.com", tipoPlanoLegado,
                vencimentoLegado == null ? null : Date.valueOf(vencimentoLegado));
        return id;
    }

    private UUID inserirContrato(UUID tenantId, UUID atletaId, String periodicity, int dueDay) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_athlete_contract (id, tenant_id, athlete_id, periodicity, due_day, start_date)
                VALUES (?, ?, ?, ?, ?, '2026-09-21')
                """, id, tenantId, atletaId, periodicity, dueDay);
        return id;
    }

    private void inserirMensalidade(UUID tenantId, UUID contratoId, LocalDate dueDate, String status) {
        jdbc.update("""
                INSERT INTO tb_athlete_invoice (tenant_id, contract_id, due_date, amount, status)
                VALUES (?, ?, ?, 200.00, ?)
                """, tenantId, contratoId, Date.valueOf(dueDate), status);
    }

    private void reexecutarV96() throws IOException {
        String sql = new ClassPathResource(V96).getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);
    }

    private List<Map<String, Object>> contratosDoAtleta(UUID atletaId) {
        return jdbc.queryForList(
                "SELECT periodicity, due_day, start_date, amount, ended_at FROM tb_athlete_contract WHERE athlete_id = ?",
                atletaId);
    }

    @Nested
    @DisplayName("tb_athlete_contract")
    class TbAthleteContract {

        @Test
        @DisplayName("um contrato ativo por atleta; encerrado coexiste (uq_athlete_contract_active)")
        void umContratoAtivoPorAtleta() {
            var tenant = inserirAssessoria();
            var atleta = inserirAtleta(tenant, null, null);
            var primeiro = inserirContrato(tenant, atleta, "MONTHLY", 10);

            assertThatThrownBy(() -> inserirContrato(tenant, atleta, "MONTHLY", 15))
                    .isInstanceOf(DataIntegrityViolationException.class);

            jdbc.update("UPDATE tb_athlete_contract SET ended_at = NOW() WHERE id = ?", primeiro);
            assertThatCode(() -> inserirContrato(tenant, atleta, "MONTHLY", 15)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("periodicity só aceita os quatro valores; due_day 1–31; amount ≥ 0")
        void checks() {
            var tenant = inserirAssessoria();
            var atleta = inserirAtleta(tenant, null, null);

            assertThatThrownBy(() -> inserirContrato(tenant, atleta, "MENSAL", 10))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> inserirContrato(tenant, atleta, "MONTHLY", 0))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> inserirContrato(tenant, atleta, "MONTHLY", 32))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO tb_athlete_contract (tenant_id, athlete_id, periodicity, amount, due_day, start_date)
                    VALUES (?, ?, 'MONTHLY', -1, 10, '2026-09-21')
                    """, tenant, atleta))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("excluir o atleta leva contrato e mensalidades (ON DELETE CASCADE)")
        void cascataNaExclusaoDoAtleta() {
            var tenant = inserirAssessoria();
            var atleta = inserirAtleta(tenant, null, null);
            var contrato = inserirContrato(tenant, atleta, "MONTHLY", 10);
            inserirMensalidade(tenant, contrato, LocalDate.of(2026, 10, 10), "OPEN");

            jdbc.update("DELETE FROM tb_atleta WHERE id = ?", atleta);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_athlete_contract WHERE id = ?", Integer.class, contrato))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_athlete_invoice WHERE contract_id = ?", Integer.class, contrato))
                    .isZero();
        }

        @Test
        @DisplayName("índices de consulta existem")
        void indicesExistem() {
            var nomes = jdbc.queryForList(
                    "SELECT indexname FROM pg_indexes WHERE tablename = 'tb_athlete_contract'", String.class);
            assertThat(nomes).contains("uq_athlete_contract_active", "idx_athlete_contract_tenant_athlete");
        }
    }

    @Nested
    @DisplayName("tb_athlete_invoice")
    class TbAthleteInvoice {

        @Test
        @DisplayName("UNIQUE (contract_id, due_date) — rede contra geração duplicada")
        void unicidadePorContratoEVencimento() {
            var tenant = inserirAssessoria();
            var contrato = inserirContrato(tenant, inserirAtleta(tenant, null, null), "MONTHLY", 10);
            inserirMensalidade(tenant, contrato, LocalDate.of(2026, 10, 10), "OPEN");

            assertThatThrownBy(() -> inserirMensalidade(tenant, contrato, LocalDate.of(2026, 10, 10), "OPEN"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("status só aceita OPEN, PAID e CANCELLED")
        void statusRestrito() {
            var tenant = inserirAssessoria();
            var contrato = inserirContrato(tenant, inserirAtleta(tenant, null, null), "MONTHLY", 10);

            assertThatThrownBy(() -> inserirMensalidade(tenant, contrato, LocalDate.of(2026, 10, 10), "VENCIDA"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            int dia = 1;
            for (String valido : new String[]{"OPEN", "PAID", "CANCELLED"}) {
                LocalDate dueDate = LocalDate.of(2026, 10, dia++);
                assertThatCode(() -> inserirMensalidade(tenant, contrato, dueDate, valido)).doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("índice por tenant/status/vencimento existe")
        void indiceExiste() {
            var nomes = jdbc.queryForList(
                    "SELECT indexname FROM pg_indexes WHERE tablename = 'tb_athlete_invoice'", String.class);
            assertThat(nomes).contains("idx_athlete_invoice_tenant_status_due", "uq_athlete_invoice_contract_due_date");
        }
    }

    @Nested
    @DisplayName("backfill dos campos legados (design D7)")
    class Backfill {

        @Test
        @DisplayName("atleta com data legada vira contrato ativo com periodicidade mapeada, dia e início da data, valor nulo")
        void atletaComDataLegada() throws IOException {
            var tenant = inserirAssessoria();
            var atleta = inserirAtleta(tenant, "TRIMESTRAL", LocalDate.of(2026, 10, 5));

            reexecutarV96();

            var contratos = contratosDoAtleta(atleta);
            assertThat(contratos).hasSize(1);
            assertThat(contratos.get(0).get("periodicity")).isEqualTo("QUARTERLY");
            assertThat(contratos.get(0).get("due_day")).isEqualTo(5);
            assertThat(((Date) contratos.get(0).get("start_date")).toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 5));
            assertThat(contratos.get(0).get("amount")).isNull();
            assertThat(contratos.get(0).get("ended_at")).isNull();
        }

        @Test
        @DisplayName("tipo legado nulo ou fora do enum vira MONTHLY em vez de abortar a migration")
        void tipoLegadoNuloOuInvalido() throws IOException {
            var tenant = inserirAssessoria();
            var semTipo = inserirAtleta(tenant, null, LocalDate.of(2026, 3, 31));
            var tipoInvalido = inserirAtleta(tenant, "QUINZENAL", LocalDate.of(2026, 3, 31));

            reexecutarV96();

            assertThat(contratosDoAtleta(semTipo)).singleElement()
                    .satisfies(c -> assertThat(c.get("periodicity")).isEqualTo("MONTHLY"));
            assertThat(contratosDoAtleta(tipoInvalido)).singleElement()
                    .satisfies(c -> {
                        assertThat(c.get("periodicity")).isEqualTo("MONTHLY");
                        assertThat(c.get("due_day")).isEqualTo(31);
                    });
        }

        @Test
        @DisplayName("atleta sem data legada não ganha contrato, e nenhuma mensalidade nasce na migration")
        void atletaSemDataNaoGanhaContrato() throws IOException {
            var tenant = inserirAssessoria();
            var semData = inserirAtleta(tenant, "MENSAL", null);
            var comData = inserirAtleta(tenant, "MENSAL", LocalDate.of(2026, 10, 5));

            reexecutarV96();

            assertThat(contratosDoAtleta(semData)).isEmpty();
            var contrato = jdbc.queryForObject(
                    "SELECT id FROM tb_athlete_contract WHERE athlete_id = ?", UUID.class, comData);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM tb_athlete_invoice WHERE contract_id = ?", Integer.class, contrato))
                    .isZero();
        }

        @Test
        @DisplayName("reexecutar o backfill não duplica contrato de quem já tem um ativo")
        void backfillIdempotente() throws IOException {
            var tenant = inserirAssessoria();
            var atleta = inserirAtleta(tenant, "MENSAL", LocalDate.of(2026, 10, 5));

            reexecutarV96();
            reexecutarV96();

            assertThat(contratosDoAtleta(atleta)).hasSize(1);
        }
    }
}
