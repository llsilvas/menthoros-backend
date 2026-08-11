package br.com.menthoros.backend.security;

import br.com.menthoros.backend.config.signup.CoachSignupProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Teto de tamanho de corpo nas rotas públicas.
 *
 * <p>Existe porque a validação do DTO só roda <b>depois</b> de o corpo ser lido e desserializado:
 * sem teto, um POST de vários megabytes é trabalho que o servidor faz antes de ter a chance de
 * recusar — e numa rota anônima isso sai de graça para quem ataca.</p>
 *
 * <p><b>Registrado como {@code @Bean}, não {@code @Component}:</b> o slice do {@code @WebMvcTest}
 * inclui automaticamente todo {@code Filter} do contexto. Como {@code @Component}, este filtro
 * arrastava {@code CoachSignupProperties} para dentro de <b>todos</b> os slices e quebrava 136
 * testes de uma vez — a armadilha está descrita no {@code CLAUDE.md} do módulo, e eu a reproduzi
 * com o mesmo número de falhas antes de lembrar dela.</p>
 *
 * <p><b>Por que contar bytes e não confiar no {@code Content-Length}:</b> o header é declarado pelo
 * cliente e pode simplesmente não vir ({@code Transfer-Encoding: chunked}). Uma checagem baseada
 * nele pareceria proteção e seria contornável trocando uma linha do request. Aqui o corte acontece
 * na leitura real do stream.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class PublicRequestSizeLimitFilter extends OncePerRequestFilter {

    private static final String PREFIXO_PUBLICO = "/api/public/";

    private final CoachSignupProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIXO_PUBLICO);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        int limite = properties.getMaxRequestBytes();

        // Corte antecipado quando o cliente declara o tamanho: evita ler o que já se sabe grande.
        if (request.getContentLengthLong() > limite) {
            responder413(response, limite);
            return;
        }

        try {
            chain.doFilter(new RequestComTeto(request, limite), response);
        } catch (CorpoExcedeuOLimite e) {
            if (!response.isCommitted()) {
                responder413(response, limite);
            }
        }
    }

    private void responder413(HttpServletResponse response, int limite) throws IOException {
        log.warn("Corpo de requisição pública acima do limite de {} bytes", limite);
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":413,\"error\":\"Payload Too Large\","
                + "\"message\":\"Requisição maior que o permitido.\"}");
    }

    /** Sinaliza o estouro de dentro da leitura do stream, onde não há como devolver resposta. */
    private static class CorpoExcedeuOLimite extends RuntimeException {
        CorpoExcedeuOLimite() {
            super(null, null, false, false); // sem stack trace: é controle de fluxo, não defeito
        }
    }

    private static class RequestComTeto extends HttpServletRequestWrapper {
        private final int limite;

        RequestComTeto(HttpServletRequest request, int limite) {
            super(request);
            this.limite = limite;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream original = super.getInputStream();
            return new ServletInputStream() {
                private long lidos;

                private int contar(int b) {
                    if (b != -1 && ++lidos > limite) {
                        throw new CorpoExcedeuOLimite();
                    }
                    return b;
                }

                @Override
                public int read() throws IOException {
                    return contar(original.read());
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    int n = original.read(b, off, len);
                    if (n > 0 && (lidos += n) > limite) {
                        throw new CorpoExcedeuOLimite();
                    }
                    return n;
                }

                @Override public boolean isFinished() { return original.isFinished(); }
                @Override public boolean isReady() { return original.isReady(); }
                @Override public void setReadListener(ReadListener l) { original.setReadListener(l); }
            };
        }
    }
}
