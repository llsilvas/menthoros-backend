package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.ai.ledger.GenerationOutcome;
import br.com.menthoros.backend.ai.ledger.LlmCallContext;
import br.com.menthoros.backend.ai.ledger.LlmCallRegistro;
import br.com.menthoros.backend.ai.ledger.LlmCallResult;
import br.com.menthoros.backend.ai.ledger.Violacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CA8 (design D12): a linha do ledger sobrevive ao rollback do chamador porque a escrita é
 * {@code REQUIRES_NEW}. Os listeners assíncronos chamam o LLM dentro de
 * {@code @Transactional(REQUIRES_NEW)} — este é o cenário deles.
 */
@DisplayName("LlmCallLedger — isolamento transacional e desfecho na última tentativa")
class LlmCallLedgerIT extends AbstractIntegrationTest {

    @Autowired
    private LlmCallLedger ledger;
    @Autowired
    private PlatformTransactionManager txManager;
    @Autowired
    private JdbcTemplate jdbc;

    private static LlmCallRegistro registro(LlmCallResult result, LlmCallContext ctx) {
        return new LlmCallRegistro("plano", "gpt-4o", 10L, 5L, 0L, 0L, new BigDecimal("0.0000500000"),
                800, result, UUID.randomUUID(), 0, "{\"ok\":true}", Optional.ofNullable(ctx));
    }

    @Test
    @DisplayName("rollback do chamador em REQUIRES_NEW não apaga a linha gravada")
    void sobreviveAoRollbackDoChamador() {
        var def = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var status = txManager.getTransaction(def);
        Optional<UUID> id;
        try {
            id = ledger.registrarChamada(registro(LlmCallResult.SUCCESS, null));
        } finally {
            txManager.rollback(status);
        }

        assertThat(id).isPresent();
        Integer existe = jdbc.queryForObject("SELECT count(*) FROM tb_llm_call WHERE id = ?", Integer.class, id.get());
        assertThat(existe).isEqualTo(1);
    }

    @Test
    @DisplayName("resultado e violações fecham a linha; desfecho vai para a tentativa mais alta da requisição")
    void resultadoEDesfecho() {
        UUID req = UUID.randomUUID();
        var t1 = new LlmCallContext(req, null, "Maria Souza", 1, "plano-v1", "h", "schema-v1");
        var t2 = new LlmCallContext(req, null, "Maria Souza", 2, "plano-v1", "h", "schema-v1");
        UUID id1 = ledger.registrarChamada(registro(LlmCallResult.PENDING, t1)).orElseThrow();
        UUID id2 = ledger.registrarChamada(registro(LlmCallResult.PENDING, t2)).orElseThrow();

        ledger.registrarResultado(id1, LlmCallResult.VALIDATION_REJECTED, List.of(new Violacao("PACE_TETO", "acima")));
        ledger.registrarResultado(id2, LlmCallResult.SUCCESS, List.of());
        ledger.registrarDesfecho(req, GenerationOutcome.PERSISTED);

        var linha1 = jdbc.queryForMap("SELECT result, violations::text AS v, request_outcome FROM tb_llm_call WHERE id = ?", id1);
        var linha2 = jdbc.queryForMap("SELECT result, violations::text AS v, request_outcome FROM tb_llm_call WHERE id = ?", id2);
        assertThat(linha1.get("result")).isEqualTo("VALIDATION_REJECTED");
        assertThat((String) linha1.get("v")).contains("PACE_TETO");
        assertThat(linha1.get("request_outcome")).isNull();
        assertThat(linha2.get("result")).isEqualTo("SUCCESS");
        assertThat(linha2.get("v")).isNull();
        assertThat(linha2.get("request_outcome")).isEqualTo("PERSISTED");
    }

    @Test
    @DisplayName("resposta bruta gravada como JSONB com o nome redigido")
    void respostaRedigidaComoJsonb() {
        UUID req = UUID.randomUUID();
        var ctx = new LlmCallContext(req, null, "Maria Souza", 1, "plano-v1", "h", "schema-v1");
        var registro = new LlmCallRegistro("plano", "gpt-4o", 1L, 1L, 0L, 0L, null, 1, LlmCallResult.PENDING,
                null, 0, "{\"nota\":\"Maria Souza foi bem\"}", Optional.of(ctx));

        UUID id = ledger.registrarChamada(registro).orElseThrow();

        String nota = jdbc.queryForObject("SELECT response_json->>'nota' FROM tb_llm_call WHERE id = ?", String.class, id);
        assertThat(nota).isEqualTo("[ATLETA] foi bem");
    }
}
