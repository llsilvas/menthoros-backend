package br.com.menthoros.backend.security;

import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.UsuarioSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("JwtTenantFilter.shouldNotFilter - Testes unitários")
class JwtTenantFilterShouldNotFilterTest {

    private final JwtTenantFilter filter =
            new JwtTenantFilter(mock(UsuarioSyncService.class), mock(UsuarioRepository.class));

    @Test
    @DisplayName("endpoints /api/admin/** são ignorados pelo filtro de tenant")
    void naoFiltraEndpointsAdmin() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/admin/assessorias");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("endpoints de negócio continuam sendo filtrados")
    void filtraEndpointsDeNegocio() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/atletas");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    @DisplayName("endpoint público da waitlist é isento do filtro de tenant (AC5b)")
    void naoFiltraWaitlist() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/waitlist");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }
}
