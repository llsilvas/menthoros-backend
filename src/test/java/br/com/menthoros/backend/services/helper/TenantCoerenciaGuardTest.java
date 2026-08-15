package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.exception.AccessDeniedException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O cenário que este guard existe para barrar não é hipotético: o banco proíbe um usuário em dois
 * tenants, o Keycloak não. Um coach adicionado a duas Organizations recebe um tenant arbitrário por
 * requisição (ver JavaDoc de {@link TenantCoerenciaGuard}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantCoerenciaGuard")
class TenantCoerenciaGuardTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuthenticatedPrincipalResolver principalResolver;

    @InjectMocks private TenantCoerenciaGuard guard;

    private UUID tenantId;
    private static final String SUB = "8a1f2c3d-0000-4000-8000-000000000001";

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("usuário do tenant corrente: devolve o tenant e segue")
    void usuarioCoerente() {
        when(principalResolver.getCurrentSubject()).thenReturn(SUB);
        when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(SUB, tenantId))
                .thenReturn(Optional.of(new Usuario()));

        assertThat(guard.exigirCoerencia()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("usuário de outro tenant: 403 em vez de escrever na assessoria errada")
    void usuarioDeOutroTenant() {
        when(principalResolver.getCurrentSubject()).thenReturn(SUB);
        when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(SUB, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.exigirCoerencia())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("não pertence à assessoria");
    }

    /**
     * A consulta filtra por sub E tenant — é ela que constitui o gate. Se algum dia virar uma
     * busca só por sub com comparação posterior, a checagem passa a depender de código que pode
     * ser removido sem quebrar nada visível.
     */
    @Test
    @DisplayName("a consulta é filtrada por sub e tenant juntos")
    void consultaFiltraPorAmbos() {
        when(principalResolver.getCurrentSubject()).thenReturn(SUB);
        when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(SUB, tenantId))
                .thenReturn(Optional.of(new Usuario()));

        guard.exigirCoerencia();

        verify(usuarioRepository).findByKeycloakIdAndAssessoria_Id(SUB, tenantId);
    }

    @Test
    @DisplayName("sem tenant no contexto, falha antes de consultar o usuário")
    void semTenant() {
        TenantContext.clear();

        assertThatThrownBy(() -> guard.exigirCoerencia())
                .isInstanceOf(IllegalStateException.class);
    }
}
