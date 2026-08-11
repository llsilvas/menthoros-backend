package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * V75 — garante que a tabela de rastro do auto-cadastro sustenta as três coisas de que o
 * orquestrador depende: idempotência, sobrevivência à compensação e liberação do slug.
 *
 * <p>Nível SQL de propósito: a entidade JPA só chega na 2.4, e o que está sob teste aqui são as
 * constraints, não o mapeamento.</p>
 */
@DisplayName("V75 tb_signup_provisioning: idempotência, rastro e liberação do slug")
class SignupProvisioningMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private UUID inserirAssessoria(String slug) {
        var id = UUID.randomUUID();
        // Sem `trial`: a coluna existiu na V45 e foi removida quando o estado de cobrança migrou
        // para tb_assinatura. Escrever este INSERT a partir da V45 é o erro que o `verify` pegou.
        jdbc.update("""
                INSERT INTO tb_assessoria (id, nome, dominio, plano, ativo, max_atletas, max_tecnicos)
                VALUES (?, ?, ?, 'BASIC', true, 10, 1)
                """, id, "Assessoria " + slug, slug);
        return id;
    }

    private void inserirRastro(String chave, String slug, String status, UUID assessoriaId) {
        jdbc.update("""
                INSERT INTO tb_signup_provisioning
                    (idempotency_key, request_hash, email, slug, status, assessoria_id, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, chave, "hash-" + chave, "coach@exemplo.com", slug, status, assessoriaId, "corr-" + chave);
    }

    @Test
    @DisplayName("a mesma Idempotency-Key não entra duas vezes — é aqui que o duplo clique para")
    void chaveDeIdempotenciaEhUnica() {
        var chave = "idem-" + UUID.randomUUID();
        inserirRastro(chave, "slug-" + UUID.randomUUID(), "STARTED", null);

        assertThatThrownBy(() -> inserirRastro(chave, "outro-" + UUID.randomUUID(), "STARTED", null))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("dois cadastros com o mesmo slug: a UNIQUE de tb_assessoria.dominio resolve a corrida")
    void slugDuplicadoColideNaAssessoria() {
        var slug = "corrida-" + UUID.randomUUID().toString().substring(0, 8);
        inserirAssessoria(slug);

        // Não há verificação prévia que feche a janela entre checar e inserir; quem serializa é a
        // constraint. O orquestrador traduz esta violação em 409.
        assertThatThrownBy(() -> inserirAssessoria(slug))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("apagar a Assessoria libera o slug e preserva o rastro INTEIRO, id incluído (V76)")
    void compensacaoApagaAssessoriaSemPerderORastro() {
        var slug = "falha-" + UUID.randomUUID().toString().substring(0, 8);
        var assessoriaId = inserirAssessoria(slug);
        var chave = "idem-" + UUID.randomUUID();
        inserirRastro(chave, slug, "ASSESSORIA_CREATED", assessoriaId);

        // A compensação apaga a assessoria. Sem FK (V76), nada bloqueia e nada é zerado.
        assertThatCode(() -> jdbc.update("DELETE FROM tb_assessoria WHERE id = ?", assessoriaId))
                .doesNotThrowAnyException();

        var rastro = jdbc.queryForMap(
                "SELECT slug, status, assessoria_id, correlation_id FROM tb_signup_provisioning WHERE idempotency_key = ?",
                chave);

        // Era null até a V76, por ON DELETE SET NULL. A FK foi derrubada porque o zeramento
        // acontecia no banco enquanto a entidade em memória seguia com o id antigo: o UPDATE que
        // gravava o desfecho FAILED reescrevia a referência pendurada e violava a constraint,
        // deixando o rastro congelado no passo anterior. Ver CoachSignupCompensacaoIT.
        assertThat(rastro.get("assessoria_id"))
                .as("o id sobrevive à remoção — é perícia, e sem ele o rastro não diz qual tenant existiu")
                .isEqualTo(assessoriaId);
        assertThat(rastro.get("slug")).as("mas o slug tentado permanece legível").isEqualTo(slug);
        assertThat(rastro.get("correlation_id")).as("e o fio para o log sobrevive").isEqualTo("corr-" + chave);

        // E o slug volta ao pool: é isto que a marcação como falha teria impedido.
        assertThatCode(() -> inserirAssessoria(slug)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("status fora da lista é rejeitado — um typo sumiria da varredura de reconciliação")
    void statusInvalidoEhRejeitado() {
        assertThatThrownBy(() -> inserirRastro("idem-" + UUID.randomUUID(), "slug-x", "RECONCILIATION_REQUIRED_", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("correlation_id é obrigatório mesmo quando não houve assessoria")
    void correlationIdEhObrigatorio() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO tb_signup_provisioning
                    (idempotency_key, request_hash, email, slug, status, correlation_id)
                VALUES (?, 'h', 'coach@exemplo.com', 'slug-y', 'FAILED', NULL)
                """, "idem-" + UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
