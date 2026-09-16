package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V94/V95 — o que o ledger de chamadas LLM depende do banco (add-plan-generation-ledger, D10):
 * linha genérica sem enriquecimento é válida, os dois CHECKs de estado, a FK do atleta que vira
 * NULL na exclusão (linha de custo sobrevive), os índices de consulta e a coluna de ligação em
 * {@code tb_plano_semanal}.
 *
 * <p>Nível SQL de propósito: o que está sob teste são as constraints, não o mapeamento.</p>
 */
@DisplayName("V94 tb_llm_call + V95 generation_request_id: constraints do ledger")
class LlmCallMigrationTest extends AbstractIntegrationTest {

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
                INSERT INTO tb_atleta (id, tenant_id, nome, email, nivel_experiencia)
                VALUES (?, ?, 'Maria', ?, 'INICIANTE')
                """, id, tenantId, "maria-" + id + "@exemplo.com");
        return id;
    }

    private UUID inserirChamada(UUID tenantId, UUID atletaId, String result, String requestOutcome) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_llm_call
                    (id, tenant_id, atleta_id, generation_request_id, route, model,
                     input_tokens, output_tokens, latency_ms, result, request_outcome)
                VALUES (?, ?, ?, ?, 'plano', 'gpt-4o', 100, 20, 1500, ?, ?)
                """, id, tenantId, atletaId, UUID.randomUUID(), result, requestOutcome);
        return id;
    }

    @Nested
    @DisplayName("tb_llm_call")
    class TbLlmCall {

        @Test
        @DisplayName("linha genérica (sem tenant, atleta ou requisição) é válida — rotas sem contexto")
        void linhaGenericaSemEnriquecimento() {
            assertThatCode(() -> jdbc.update("""
                    INSERT INTO tb_llm_call (route, model, input_tokens, output_tokens, latency_ms, result)
                    VALUES ('standard', 'claude-haiku-4-5-20251001', 10, 5, 300, 'SUCCESS')
                    """)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("result só aceita os seis estados de D6")
        void resultRestrito() {
            var tenant = inserirAssessoria();
            assertThatThrownBy(() -> inserirChamada(tenant, null, "INVENTADO", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            for (String valido : new String[]{"PENDING", "SUCCESS", "VALIDATION_REJECTED", "PARSE_ERROR", "LLM_ERROR", "TIMEOUT"}) {
                assertThatCode(() -> inserirChamada(tenant, null, valido, null)).doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("request_outcome é opcional e só aceita os quatro desfechos de D6")
        void requestOutcomeRestrito() {
            var tenant = inserirAssessoria();
            assertThatThrownBy(() -> inserirChamada(tenant, null, "SUCCESS", "QUALQUER"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            for (String valido : new String[]{"PERSISTED", "CONFLICT", "REJECTED_POST_LLM", "PERSIST_ERROR"}) {
                assertThatCode(() -> inserirChamada(tenant, null, "SUCCESS", valido)).doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("excluir o atleta preserva a linha com atleta_id nulo (custo sobrevive à LGPD)")
        void exclusaoDoAtletaPreservaLinha() {
            var tenant = inserirAssessoria();
            var atleta = inserirAtleta(tenant);
            var chamada = inserirChamada(tenant, atleta, "SUCCESS", "PERSISTED");

            jdbc.update("DELETE FROM tb_atleta WHERE id = ?", atleta);

            var restante = jdbc.queryForMap("SELECT atleta_id, tenant_id FROM tb_llm_call WHERE id = ?", chamada);
            assertThat(restante.get("atleta_id")).isNull();
            assertThat(restante.get("tenant_id")).isEqualTo(tenant);
        }

        @Test
        @DisplayName("índices de consulta por tenant/período e por requisição existem")
        void indicesExistem() {
            var nomes = jdbc.queryForList(
                    "SELECT indexname FROM pg_indexes WHERE tablename = 'tb_llm_call'", String.class);
            assertThat(nomes).contains("idx_llm_call_tenant_created", "idx_llm_call_generation_request");
        }
    }

    @Nested
    @DisplayName("tb_plano_semanal.generation_request_id")
    class TbPlanoSemanal {

        @Test
        @DisplayName("coluna existe, é opcional e indexada")
        void colunaEIndice() {
            var tipo = jdbc.queryForObject("""
                    SELECT data_type || ':' || is_nullable FROM information_schema.columns
                    WHERE table_name = 'tb_plano_semanal' AND column_name = 'generation_request_id'
                    """, String.class);
            assertThat(tipo).isEqualTo("uuid:YES");

            var nomes = jdbc.queryForList(
                    "SELECT indexname FROM pg_indexes WHERE tablename = 'tb_plano_semanal'", String.class);
            assertThat(nomes).contains("idx_plano_semanal_generation_request");
        }
    }
}
