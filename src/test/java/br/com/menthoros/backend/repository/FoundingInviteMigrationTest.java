package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V84 — o que o serviço de convite depende do banco: um único convite aberto por inscrito, hash
 * único, e que a compensação (que apaga a assessoria) não derrube o convite.
 *
 * <p>Nível SQL de propósito: o que está sob teste são as constraints, não o mapeamento.</p>
 */
@DisplayName("V84 tb_founding_invite: unicidade do convite aberto e sobrevivência à compensação")
class FoundingInviteMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private UUID inserirInscrito() {
        var id = UUID.randomUUID();
        var email = "coach-" + id + "@exemplo.com";
        jdbc.update("""
                INSERT INTO tb_waitlist (id, nome, email, email_normalized, perfil, aceite_lgpd)
                VALUES (?, ?, ?, ?, 'TREINADOR', true)
                """, id, "Coach", email, email);
        return id;
    }

    private UUID inserirAssessoria(String slug) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_assessoria (id, nome, dominio, plano, ativo, max_atletas, max_tecnicos, founding)
                VALUES (?, ?, ?, 'GRATUITO', true, 10, 1, true)
                """, id, "Assessoria " + slug, slug);
        return id;
    }

    private UUID inserirConvite(UUID waitlistId, String hash, OffsetDateTime invalidatedAt,
                                OffsetDateTime convertedAt, UUID assessoriaId) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_founding_invite
                    (id, waitlist_id, token_hash, email, expires_at, invalidated_at, converted_at, assessoria_id, invited_by)
                VALUES (?, ?, ?, ?, NOW() + INTERVAL '7 days', ?, ?, ?, 'admin-sub')
                """, id, waitlistId, hash, "coach@exemplo.com", invalidatedAt, convertedAt, assessoriaId, "admin-sub");
        return id;
    }

    @Test
    @DisplayName("dois convites abertos para o mesmo inscrito colidem — mesmo que o primeiro esteja expirado")
    void umConviteAbertoPorInscrito() {
        var inscrito = inserirInscrito();
        inserirConvite(inscrito, "hash-" + UUID.randomUUID(), null, null, null);
        // Empurra o primeiro para o passado: o índice parcial não olha expires_at, e é isso que
        // obriga o serviço a invalidar o anterior antes de inserir.
        jdbc.update("UPDATE tb_founding_invite SET expires_at = NOW() - INTERVAL '1 day' WHERE waitlist_id = ?", inscrito);

        assertThatThrownBy(() -> inserirConvite(inscrito, "hash-" + UUID.randomUUID(), null, null, null))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("invalidar o anterior libera o inscrito para um novo convite")
    void invalidadoLiberaReenvio() {
        var inscrito = inserirInscrito();
        inserirConvite(inscrito, "hash-" + UUID.randomUUID(), OffsetDateTime.now(), null, null);

        assertThatCode(() -> inserirConvite(inscrito, "hash-" + UUID.randomUUID(), null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("o hash do token é único entre todos os convites")
    void hashUnico() {
        var hash = "hash-" + UUID.randomUUID();
        inserirConvite(inserirInscrito(), hash, null, null, null);

        assertThatThrownBy(() -> inserirConvite(inserirInscrito(), hash, null, null, null))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("apagar a assessoria (compensação) preserva o convite com assessoria_id nulo")
    void compensacaoNaoDerrubaOConvite() {
        var inscrito = inserirInscrito();
        var assessoria = inserirAssessoria("fund-" + UUID.randomUUID().toString().substring(0, 8));
        var convite = inserirConvite(inscrito, "hash-" + UUID.randomUUID(), null, OffsetDateTime.now(), assessoria);

        jdbc.update("DELETE FROM tb_assessoria WHERE id = ?", assessoria);

        var restante = jdbc.queryForMap(
                "SELECT assessoria_id, converted_at FROM tb_founding_invite WHERE id = ?", convite);
        assertThat(restante.get("assessoria_id")).isNull();
        assertThat(restante.get("converted_at")).isNotNull();
    }

    @Test
    @DisplayName("origin do rastro só aceita os dois valores conhecidos")
    void origemRestrita() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO tb_signup_provisioning
                    (idempotency_key, request_hash, email, slug, status, correlation_id, origin)
                VALUES (?, 'h', 'c@exemplo.com', ?, 'STARTED', 'corr', 'INVENTADA')
                """, "idem-" + UUID.randomUUID(), "slug-" + UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rastro sem origin explícita nasce como PUBLIC_SIGNUP — o cadastro existente não muda")
    void origemPadrao() {
        var chave = "idem-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_signup_provisioning
                    (idempotency_key, request_hash, email, slug, status, correlation_id)
                VALUES (?, 'h', 'c@exemplo.com', ?, 'STARTED', 'corr')
                """, chave, "slug-" + UUID.randomUUID());

        assertThat(jdbc.queryForObject(
                "SELECT origin FROM tb_signup_provisioning WHERE idempotency_key = ?", String.class, chave))
                .isEqualTo("PUBLIC_SIGNUP");
    }
}
