package br.com.menthoros.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PublicEndpointRateLimitFilterTest {

    private static final String PATH = "/api/v1/waitlist";

    private final PublicEndpointRateLimitFilter filter = new PublicEndpointRateLimitFilter(3, 3);

    @Test
    void permiteAteOLimiteEBloqueiaAcima() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(post("1.1.1.1", null).getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
        assertThat(post("1.1.1.1", null).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void contaPeloRemoteAddr_naoEscapaRotacionandoXForwardedFor() throws Exception {
        // Mesmo remoteAddr, X-Forwarded-For rotacionado a cada chamada: não pode escapar do contador.
        for (int i = 0; i < 3; i++) {
            assertThat(post("9.9.9.9", "spoof-" + i).getStatus())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
        assertThat(post("9.9.9.9", "outro-spoof").getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void ipsDistintosTemContadoresSeparados() throws Exception {
        for (int i = 0; i < 3; i++) {
            post("2.2.2.2", null);
        }
        assertThat(post("3.3.3.3", null).getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    private MockHttpServletResponse post(String remoteAddr, String xForwardedFor)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
        request.setRemoteAddr(remoteAddr);
        if (xForwardedFor != null) {
            request.addHeader("X-Forwarded-For", xForwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void protegeTambemOAutoCadastro() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(postEm("/api/public/coach-signups", "2.2.2.2").getStatus())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
        assertThat(postEm("/api/public/coach-signups", "2.2.2.2").getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void contadoresDasDuasRotasSaoIndependentes() throws Exception {
        // Esgotar a waitlist não pode fechar o cadastro para o mesmo IP: são recursos distintos.
        for (int i = 0; i < 4; i++) {
            postEm("/api/v1/waitlist", "3.3.3.3");
        }
        assertThat(postEm("/api/public/coach-signups", "3.3.3.3").getStatus())
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void rotaNaoProtegidaPassaDireto() throws Exception {
        assertThat(postEm("/api/v1/atletas", "4.4.4.4").getStatus())
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    private MockHttpServletResponse postEm(String path, String ip) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
