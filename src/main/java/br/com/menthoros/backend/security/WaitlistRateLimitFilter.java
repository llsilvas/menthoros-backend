package br.com.menthoros.backend.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate-limit por IP no endpoint público {@code POST /api/v1/waitlist}.
 *
 * <p>Defesa anti-abuso complementar ao honeypot: bloqueia o loop de {@code curl} que um campo oculto
 * não pega. Usa Caffeine (já dependência do projeto) com janela deslizante de 1 minuto por IP.
 */
@Component
@Slf4j
public class WaitlistRateLimitFilter extends OncePerRequestFilter {

    private static final String PATH = "/api/v1/waitlist";

    private final int limitePorMinuto;
    private final Cache<String, AtomicInteger> acessosPorIp;

    public WaitlistRateLimitFilter(
            @Value("${app.waitlist.rate-limit.per-minute:5}") int limitePorMinuto) {
        this.limitePorMinuto = limitePorMinuto;
        this.acessosPorIp = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(10_000)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = clientIp(request);
        AtomicInteger contador = acessosPorIp.get(ip, k -> new AtomicInteger(0));
        if (contador.incrementAndGet() > limitePorMinuto) {
            log.warn("Waitlist: rate-limit excedido para o IP {}", ip);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Muitas solicitações. Tente novamente em instantes.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
