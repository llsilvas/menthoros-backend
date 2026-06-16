package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.UsuarioMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.AtletaService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AtletaService atletaService;
    @Mock private UsuarioMapper usuarioMapper;
    @Mock private AuthenticatedPrincipalResolver principalResolver;

    @InjectMocks private UsuarioServiceImpl usuarioService;

    private UUID tenantId;
    private String sub;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        sub = UUID.randomUUID().toString();
        TenantContext.setTenantId(tenantId);
        when(principalResolver.getCurrentSubject()).thenReturn(sub);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getCurrentUser")
    class GetCurrentUser {

        @Test
        @DisplayName("TECNICO retorna identidade sem resolver atleta")
        void tecnicoSemAtleta() {
            Usuario usuario = usuario(UserRole.TECNICO);
            UsuarioMeOutputDto esperado = dtoStub();
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));
            when(usuarioMapper.toMeOutputDto(usuario, null)).thenReturn(esperado);

            UsuarioMeOutputDto resultado = usuarioService.getCurrentUser();

            assertThat(resultado).isEqualTo(esperado);
            verifyNoInteractions(atletaService);
        }

        @Test
        @DisplayName("ATLETA vinculado resolve o Atleta e mapeia com atletaId")
        void atletaVinculado() {
            Usuario usuario = usuario(UserRole.ATLETA);
            Atleta atleta = Atleta.builder().id(UUID.randomUUID()).nome("Atleta").build();
            UsuarioMeOutputDto esperado = dtoStub();
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));
            when(atletaService.findVinculadoAoUsuario(usuario.getId()))
                    .thenReturn(Optional.of(atleta));
            when(usuarioMapper.toMeOutputDto(usuario, atleta)).thenReturn(esperado);

            UsuarioMeOutputDto resultado = usuarioService.getCurrentUser();

            assertThat(resultado).isEqualTo(esperado);
            verify(atletaService).findVinculadoAoUsuario(usuario.getId());
        }

        @Test
        @DisplayName("ATLETA sem vínculo mapeia com atleta null")
        void atletaSemVinculo() {
            Usuario usuario = usuario(UserRole.ATLETA);
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));
            when(atletaService.findVinculadoAoUsuario(usuario.getId()))
                    .thenReturn(Optional.empty());
            when(usuarioMapper.toMeOutputDto(usuario, null)).thenReturn(dtoStub());

            usuarioService.getCurrentUser();

            verify(usuarioMapper).toMeOutputDto(usuario, null);
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando usuário não existe no tenant")
        void usuarioInexistenteNoTenant() {
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> usuarioService.getCurrentUser())
                    .isInstanceOf(DomainNotFoundException.class)
                    .hasMessageContaining("não encontrado")
                    .hasMessageNotContaining(sub);

            verifyNoInteractions(atletaService);
            verifyNoInteractions(usuarioMapper);
        }

        @Test
        @DisplayName("isolamento de tenant: Atleta de outro tenant não é resolvido (atleta null)")
        void isolamentoTenant() {
            Usuario usuario = usuario(UserRole.ATLETA);
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));
            when(atletaService.findVinculadoAoUsuario(usuario.getId()))
                    .thenReturn(Optional.empty());
            when(usuarioMapper.toMeOutputDto(usuario, null)).thenReturn(dtoStub());

            usuarioService.getCurrentUser();

            verify(usuarioMapper).toMeOutputDto(usuario, null);
            verify(usuarioMapper, never()).toMeOutputDto(any(), any(Atleta.class));
        }
    }

    private Usuario usuario(UserRole role) {
        return Usuario.builder()
                .id(UUID.fromString(sub))
                .keycloakId(sub)
                .nome("Usuário")
                .email("usuario@exemplo.com")
                .role(role)
                .assessoria(Assessoria.builder().id(tenantId).nome("Assessoria").dominio("dom").build())
                .build();
    }

    private UsuarioMeOutputDto dtoStub() {
        return new UsuarioMeOutputDto(UUID.fromString(sub), "Usuário", "usuario@exemplo.com",
                UserRole.TECNICO, null, null);
    }
}
