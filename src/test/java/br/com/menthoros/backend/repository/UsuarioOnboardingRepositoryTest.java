package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A coluna `onboarding_concluido` nasce {@code true} (V80), e isso é a decisão mais fácil de
 * inverter por engano: o reflexo é "pendente até concluir", e {@code boolean} primitivo em Java
 * nasce {@code false} — o valor errado.
 *
 * <p>Se o default do banco parar de valer, todo usuário criado pelo {@code UsuarioSyncServiceImpl}
 * (que roda a cada requisição, não só no primeiro acesso) entraria pendente e **veria o wizard de
 * boas-vindas apesar de já usar o produto**. Estes testes são a rede disso.
 */
@Transactional
@DisplayName("onboarding_concluido — default do banco")
class UsuarioOnboardingRepositoryTest extends AbstractIntegrationTest {

    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    @PersistenceContext private EntityManager entityManager;

    @Test
    @DisplayName("usuário criado sem informar o campo nasce concluído")
    void defaultEhConcluido() {
        Usuario salvo = usuarioRepository.saveAndFlush(novoUsuario(null));
        entityManager.clear();

        Usuario recarregado = usuarioRepository.findById(salvo.getId()).orElseThrow();

        assertThat(recarregado.isOnboardingConcluido())
                .as("quem já usa o produto não pode ser interrompido por um wizard de boas-vindas")
                .isTrue();
    }

    @Test
    @DisplayName("o caminho do signup consegue gravar pendente")
    void signupGravaPendente() {
        Usuario salvo = usuarioRepository.saveAndFlush(novoUsuario(false));
        entityManager.clear();

        assertThat(usuarioRepository.findById(salvo.getId()).orElseThrow().isOnboardingConcluido())
                .isFalse();
    }

    /**
     * O estado é por usuário, não por assessoria: um técnico convidado depois não pode reabrir o
     * wizard do dono, nem o inverso.
     */
    @Test
    @DisplayName("o estado é por usuário, não por tenant")
    void estadoEhPorUsuario() {
        Assessoria assessoria = seedAssessoria();
        Usuario dono = usuarioRepository.saveAndFlush(novoUsuario(assessoria, false));
        Usuario tecnico = usuarioRepository.saveAndFlush(novoUsuario(assessoria, true));
        entityManager.clear();

        assertThat(usuarioRepository.findById(dono.getId()).orElseThrow().isOnboardingConcluido()).isFalse();
        assertThat(usuarioRepository.findById(tecnico.getId()).orElseThrow().isOnboardingConcluido()).isTrue();
    }

    private Usuario novoUsuario(Boolean concluido) {
        return novoUsuario(seedAssessoria(), concluido);
    }

    private Usuario novoUsuario(Assessoria assessoria, Boolean concluido) {
        UUID id = UUID.randomUUID();
        Usuario.UsuarioBuilder builder = Usuario.builder()
                .id(id)
                .keycloakId(id.toString())
                .nome("Usuario Onboarding")
                .email("onboarding-" + id + "@exemplo.com")
                .role(UserRole.TECNICO)
                .ativo(true)
                .assessoria(assessoria);
        if (concluido != null) {
            builder.onboardingConcluido(concluido);
        }
        return builder.build();
    }

    private Assessoria seedAssessoria() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Onboarding");
        assessoria.setDominio("onboarding-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(assessoria);
    }
}
