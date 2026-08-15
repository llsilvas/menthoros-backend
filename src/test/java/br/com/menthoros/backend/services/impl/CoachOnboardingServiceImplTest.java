package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachOnboardingServiceImplTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuthenticatedPrincipalResolver principalResolver;

    @InjectMocks private CoachOnboardingServiceImpl service;

    private UUID tenantId;
    private static final String SUB = "8a1f2c3d-0000-4000-8000-000000000042";

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("concluir")
    class Concluir {

        @Test
        @DisplayName("marca o onboarding como concluído")
        void marcaConcluido() {
            Usuario usuario = usuarioPendente();
            when(principalResolver.getCurrentSubject()).thenReturn(SUB);
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(SUB, tenantId))
                    .thenReturn(Optional.of(usuario));

            service.concluir();

            assertThat(usuario.isOnboardingConcluido()).isTrue();
            verify(usuarioRepository).save(usuario);
        }

        /**
         * Idempotência não é luxo aqui: o wizard chama isto ao concluir e ao "pular tudo", e um
         * duplo clique não pode virar erro na cara de quem acabou de terminar o cadastro.
         */
        @Test
        @DisplayName("concluir de novo é no-op, sem escrita")
        void concluirDeNovoNaoEscreve() {
            Usuario usuario = usuarioPendente();
            usuario.setOnboardingConcluido(true);
            when(principalResolver.getCurrentSubject()).thenReturn(SUB);
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(SUB, tenantId))
                    .thenReturn(Optional.of(usuario));

            service.concluir();

            assertThat(usuario.isOnboardingConcluido()).isTrue();
            verify(usuarioRepository, never()).save(any());
        }

        /**
         * A consulta filtra por sub E tenant — concluir o meu onboarding não pode alcançar o
         * usuário de outra assessoria que por acaso tenha o mesmo `sub` num token forjado.
         */
        @Test
        @DisplayName("usuário fora do tenant corrente não é encontrado")
        void usuarioForaDoTenant() {
            when(principalResolver.getCurrentSubject()).thenReturn(SUB);
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(SUB, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.concluir())
                    .isInstanceOf(DomainNotFoundException.class);

            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("sem tenant no contexto, falha antes de consultar")
        void semTenant() {
            TenantContext.clear();

            assertThatThrownBy(() -> service.concluir())
                    .isInstanceOf(IllegalStateException.class);

            verify(usuarioRepository, never()).save(any());
        }
    }

    private Usuario usuarioPendente() {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.fromString(SUB));
        usuario.setKeycloakId(SUB);
        usuario.setOnboardingConcluido(false);
        return usuario;
    }
}
