package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.entity.UsuarioLgpdConsent;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integração real (Testcontainers) contra o schema da V73.
 *
 * <p>O que estes testes provam não é CRUD: é que a constraint
 * {@code uk_usuario_lgpd_consent_versoes} de fato sustenta a idempotência do aceite e que o
 * registro é <b>append-only</b> — re-consentir depois de um bump de versão precisa criar linha nova
 * sem tocar na anterior, senão a prova do aceite antigo se perde, que é justamente o que a tabela
 * existe para evitar.
 */
@Transactional
class UsuarioLgpdConsentRepositoryTest extends AbstractIntegrationTest {

    private static final String POLICY_V1 = "2026-06-30";
    private static final String POLICY_V2 = "2026-11-01";
    private static final String TERMS_V1 = "2026-06-30";

    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private UsuarioLgpdConsentRepository consentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("uk_usuario_lgpd_consent_versoes")
    class ConstraintDeVersoes {

        @Test
        @DisplayName("segundo aceite das MESMAS versões viola a constraint")
        void mesmasVersoesViolam() {
            Usuario usuario = seedUsuario();
            consentRepository.saveAndFlush(consent(usuario, POLICY_V1, TERMS_V1));

            assertThatThrownBy(() ->
                    consentRepository.saveAndFlush(consent(usuario, POLICY_V1, TERMS_V1)))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_usuario_lgpd_consent_versoes");
        }

        @Test
        @DisplayName("aceite de versão DIFERENTE cria segunda linha e preserva a primeira intacta")
        void versaoDiferentePreservaHistorico() {
            Usuario usuario = seedUsuario();
            UsuarioLgpdConsent primeiro = consentRepository.saveAndFlush(
                    consent(usuario, POLICY_V1, TERMS_V1));
            Instant consentedAtOriginal = primeiro.getConsentedAt();

            consentRepository.saveAndFlush(consent(usuario, POLICY_V2, TERMS_V1));
            flushClear();

            assertThat(consentRepository.findAll()).hasSize(2);

            UsuarioLgpdConsent recarregado = consentRepository.findById(primeiro.getId()).orElseThrow();
            assertThat(recarregado.getPolicyVersion()).isEqualTo(POLICY_V1);
            assertThat(recarregado.getConsentedAt()).isEqualTo(consentedAtOriginal);
        }

        @Test
        @DisplayName("mesmas versões em TENANTS distintos não colidem — consentimento é por tenant")
        void tenantsDistintosNaoColidem() {
            Usuario usuario = seedUsuario();
            consentRepository.saveAndFlush(consent(usuario, POLICY_V1, TERMS_V1));

            // Mesmo usuário, mesmas versões, outro tenant: precisa criar linha nova. Se a constraint
            // não incluísse tenant_id, o aceite seria rejeitado e tratado como "já registrado",
            // enquanto a consulta tenant-scoped continuaria retornando false — bloqueio permanente.
            UsuarioLgpdConsent outroTenant = UsuarioLgpdConsent.builder()
                    .usuario(usuario)
                    .tenantId(UUID.randomUUID())
                    .policyVersion(POLICY_V1)
                    .termsVersion(TERMS_V1)
                    .build();
            consentRepository.saveAndFlush(outroTenant);
            flushClear();

            assertThat(consentRepository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("mesmas versões para usuários distintos não colidem")
        void usuariosDistintosNaoColidem() {
            consentRepository.saveAndFlush(consent(seedUsuario(), POLICY_V1, TERMS_V1));
            consentRepository.saveAndFlush(consent(seedUsuario(), POLICY_V1, TERMS_V1));
            flushClear();

            assertThat(consentRepository.findAll()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion")
    class ExistePorVersoesVigentes {

        @Test
        @DisplayName("true quando existe aceite exatamente das versões consultadas")
        void trueParaVersoesVigentes() {
            Usuario usuario = seedUsuario();
            consentRepository.saveAndFlush(consent(usuario, POLICY_V1, TERMS_V1));
            flushClear();

            assertThat(consentRepository.existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                    usuario.getId(), usuario.getAssessoria().getId(), POLICY_V1, TERMS_V1)).isTrue();
        }

        @Test
        @DisplayName("false quando só há aceite de versão antiga — é o bump reabrindo o gate")
        void falseQuandoSoTemVersaoAntiga() {
            Usuario usuario = seedUsuario();
            consentRepository.saveAndFlush(consent(usuario, POLICY_V1, TERMS_V1));
            flushClear();

            assertThat(consentRepository.existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                    usuario.getId(), usuario.getAssessoria().getId(), POLICY_V2, TERMS_V1)).isFalse();
        }

        @Test
        @DisplayName("false para tenant diferente — a query é tenant-scoped de fato")
        void falseParaOutroTenant() {
            Usuario usuario = seedUsuario();
            consentRepository.saveAndFlush(consent(usuario, POLICY_V1, TERMS_V1));
            flushClear();

            assertThat(consentRepository.existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                    usuario.getId(), UUID.randomUUID(), POLICY_V1, TERMS_V1)).isFalse();
        }
    }

    @Nested
    @DisplayName("findTopByUsuario_IdAndTenantIdOrderByConsentedAtDesc")
    class UltimoAceite {

        @Test
        @DisplayName("retorna o aceite mais recente quando há histórico")
        void retornaMaisRecente() {
            Usuario usuario = seedUsuario();
            UUID tenantId = usuario.getAssessoria().getId();
            Instant agora = Instant.now();

            UsuarioLgpdConsent antigo = consent(usuario, POLICY_V1, TERMS_V1);
            antigo.setConsentedAt(agora.minus(90, ChronoUnit.DAYS));
            consentRepository.saveAndFlush(antigo);

            UsuarioLgpdConsent recente = consent(usuario, POLICY_V2, TERMS_V1);
            recente.setConsentedAt(agora);
            consentRepository.saveAndFlush(recente);
            flushClear();

            Optional<UsuarioLgpdConsent> resultado = consentRepository
                    .findTopByUsuario_IdAndTenantIdOrderByConsentedAtDesc(usuario.getId(), tenantId);

            assertThat(resultado).isPresent();
            assertThat(resultado.get().getPolicyVersion()).isEqualTo(POLICY_V2);
        }

        @Test
        @DisplayName("vazio quando o usuário nunca consentiu")
        void vazioSemAceite() {
            Usuario usuario = seedUsuario();

            assertThat(consentRepository.findTopByUsuario_IdAndTenantIdOrderByConsentedAtDesc(
                    usuario.getId(), usuario.getAssessoria().getId())).isEmpty();
        }
    }

    private void flushClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Assessoria seedAssessoria() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria LGPD Test");
        assessoria.setDominio("lgpd-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(assessoria);
    }

    private Usuario seedUsuario() {
        UUID id = UUID.randomUUID();
        Usuario usuario = Usuario.builder()
                .id(id)
                .keycloakId(id.toString())
                .nome("Coach")
                .email("coach-" + id + "@exemplo.com")
                .role(UserRole.TECNICO)
                .ativo(true)
                .assessoria(seedAssessoria())
                .build();
        return usuarioRepository.save(usuario);
    }

    private UsuarioLgpdConsent consent(Usuario usuario, String policyVersion, String termsVersion) {
        return UsuarioLgpdConsent.builder()
                .usuario(usuario)
                .tenantId(usuario.getAssessoria().getId())
                .policyVersion(policyVersion)
                .termsVersion(termsVersion)
                .build();
    }
}
