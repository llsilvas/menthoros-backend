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

    @Test
    @DisplayName("o auto-cadastro é isento — o front injeta Authorization globalmente, e um token "
            + "residual sem tenant_id derrubaria o cadastro com 403")
    void naoFiltraAutoCadastro() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/public/coach-signups");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("a isenção é por prefixo: /api/public/** inteiro é tenant-less por definição")
    void naoFiltraOutrasRotasPublicas() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/public/qualquer-coisa/futura");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("mas o prefixo não vaza para rotas que apenas COMEÇAM com o texto")
    void naoIsentaRotaQueSoParecePublica() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/publicidade/campanhas");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
