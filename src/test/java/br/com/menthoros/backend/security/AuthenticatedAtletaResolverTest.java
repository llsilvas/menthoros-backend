package br.com.menthoros.backend.security;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticatedAtletaResolver")
class AuthenticatedAtletaResolverTest {

    @Mock private AuthenticatedPrincipalResolver principalResolver;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AtletaRepository atletaRepository;

    @InjectMocks private AuthenticatedAtletaResolver resolver;

    private UUID tenantId;

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
    @DisplayName("resolverAtletaIdAtual")
    class ResolverAtletaIdAtual {

        @Test
        @DisplayName("resolve o atleta vinculado ao usuário do token")
        void resolveDoToken() {
            UUID usuarioId = UUID.randomUUID();
            UUID atletaId = UUID.randomUUID();
            Usuario usuario = new Usuario();
            usuario.setId(usuarioId);
            when(principalResolver.getCurrentSubject()).thenReturn("sub-123");
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id("sub-123", tenantId)).thenReturn(Optional.of(usuario));
            when(atletaRepository.findByUsuario_IdAndAssessoria_Id(usuarioId, tenantId))
                    .thenReturn(Optional.of(Atleta.builder().id(atletaId).build()));

            assertThat(resolver.resolverAtletaIdAtual()).isEqualTo(atletaId);
        }

        @Test
        @DisplayName("usuário do token não encontrado no tenant → not found")
        void usuarioNaoEncontrado() {
            when(principalResolver.getCurrentSubject()).thenReturn("sub-x");
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id("sub-x", tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> resolver.resolverAtletaIdAtual()).isInstanceOf(DomainNotFoundException.class);
        }

        @Test
        @DisplayName("usuário sem atleta vinculado → not found")
        void semAtletaVinculado() {
            UUID usuarioId = UUID.randomUUID();
            Usuario usuario = new Usuario();
            usuario.setId(usuarioId);
            when(principalResolver.getCurrentSubject()).thenReturn("sub-123");
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id("sub-123", tenantId)).thenReturn(Optional.of(usuario));
            when(atletaRepository.findByUsuario_IdAndAssessoria_Id(usuarioId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> resolver.resolverAtletaIdAtual()).isInstanceOf(DomainNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("atuaComoAtleta")
    class AtuaComoAtleta {

        @Test
        @DisplayName("ATLETA sem papel de coach atua como atleta")
        void somenteAtleta() {
            when(principalResolver.hasRole(UserRole.ATLETA)).thenReturn(true);
            when(principalResolver.hasRole(UserRole.TECNICO)).thenReturn(false);
            when(principalResolver.hasRole(UserRole.ADMIN)).thenReturn(false);

            assertThat(resolver.atuaComoAtleta()).isTrue();
        }

        @Test
        @DisplayName("ATLETA que também é TECNICO não é restrito")
        void atletaETecnico() {
            when(principalResolver.hasRole(UserRole.ATLETA)).thenReturn(true);
            when(principalResolver.hasRole(UserRole.TECNICO)).thenReturn(true);

            assertThat(resolver.atuaComoAtleta()).isFalse();
        }

        @Test
        @DisplayName("TECNICO puro não atua como atleta")
        void tecnico() {
            when(principalResolver.hasRole(UserRole.ATLETA)).thenReturn(false);

            assertThat(resolver.atuaComoAtleta()).isFalse();
        }
    }
}
