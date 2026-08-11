package br.com.menthoros.backend.security;

import br.com.menthoros.backend.config.signup.CoachSignupProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PublicRequestSizeLimitFilter: teto de corpo nas rotas públicas")
class PublicRequestSizeLimitFilterTest {

    private static final int LIMITE = 100;

    private final PublicRequestSizeLimitFilter filter = new PublicRequestSizeLimitFilter(propriedades());

    private static CoachSignupProperties propriedades() {
        var p = new CoachSignupProperties();
        p.setMaxRequestBytes(LIMITE);
        return p;
    }

    private MockHttpServletRequest requisicao(String uri, byte[] corpo, boolean declararTamanho) {
        // Sem declarar o tamanho, getContentLengthLong() precisa devolver -1 de verdade. O
        // MockHttpServletRequest o deriva do conteúdo, o que faria o teste do caso `chunked`
        // passar pelo corte antecipado — ou seja, verde sem exercitar a contagem no stream.
        var request = declararTamanho
                ? new MockHttpServletRequest("POST", uri)
                : new MockHttpServletRequest("POST", uri) {
                    @Override public long getContentLengthLong() { return -1; }
                    @Override public int getContentLength() { return -1; }
                };
        request.setContent(corpo);
        if (!declararTamanho) {
            request.addHeader("Transfer-Encoding", "chunked");
            request.setContentType("application/json");
        }
        return request;
    }

    private MockHttpServletResponse executar(MockHttpServletRequest request, FilterChain chain) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("corpo dentro do limite passa")
    void dentroDoLimite() throws Exception {
        var response = executar(requisicao("/api/public/coach-signups", "x".repeat(50).getBytes(StandardCharsets.UTF_8), true),
                new MockFilterChain());

        assertThat(response.getStatus()).isNotEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
    }

    @Test
    @DisplayName("corpo acima do limite com Content-Length declarado é cortado antes de ser lido")
    void acimaComContentLength() throws Exception {
        var chain = new MockFilterChain();
        var response = executar(requisicao("/api/public/coach-signups", "x".repeat(LIMITE + 1).getBytes(StandardCharsets.UTF_8), true), chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(chain.getRequest()).as("a cadeia nem foi chamada").isNull();
    }

    @Test
    @DisplayName("SEM Content-Length o corte ainda acontece — é o caso que uma checagem de header não pegaria")
    void acimaSemContentLength() throws Exception {
        // A cadeia lê o corpo, como o Jackson faria: é durante a leitura que o teto morde.
        FilterChain lendoOCorpo = (req, res) -> StreamUtils.copyToByteArray(req.getInputStream());

        var response = executar(
                requisicao("/api/public/coach-signups", "x".repeat(LIMITE + 500).getBytes(StandardCharsets.UTF_8), false),
                lendoOCorpo);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
    }

    @Test
    @DisplayName("rota fora de /api/public não é filtrada")
    void rotaPrivadaNaoEhFiltrada() throws Exception {
        var response = executar(requisicao("/api/v1/atletas", "x".repeat(LIMITE + 1000).getBytes(StandardCharsets.UTF_8), true),
                new MockFilterChain());

        assertThat(response.getStatus()).isNotEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
    }
}
