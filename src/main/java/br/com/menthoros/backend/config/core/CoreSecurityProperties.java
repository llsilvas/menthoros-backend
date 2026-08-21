package br.com.menthoros.backend.config.core;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.security")
public class CoreSecurityProperties {

    @NotEmpty(message = "publicPaths cannot be empty")
    private List<String> publicPaths = new ArrayList<>(List.of(
        "/api/public/**",
        "/swagger-ui/**",
        "/api-docs/**",
        "/v3/api-docs/**",
        "/actuator/health",
        "/api/v1/status",
        "/api/v1/waitlist"
    ));

    @NotEmpty(message = "stravaPaths cannot be empty")
    private List<String> stravaPaths = new ArrayList<>(List.of(
        "/api/v1/strava/webhook",
        "/api/v1/strava/callback"
    ));

    @NotEmpty(message = "asaasPaths cannot be empty")
    private List<String> asaasPaths = new ArrayList<>(List.of(
        "/api/v1/asaas/webhook"
    ));

    /**
     * Lista própria, e não uma entrada em {@code stravaPaths}: o callback do intervals.icu chega
     * do browser do atleta redirecionado pelo provedor, sem JWT (D5). Fica deliberadamente fora do
     * prefixo {@code /api/v1/integracoes/me/**}, que é inteiramente autenticado — abrir um buraco
     * de {@code permitAll} dentro daquela árvore seria exatamente o tipo de exceção que se esquece
     * depois.
     *
     * <p>A identidade do atleta não vem da sessão: vem do {@code state} assinado com HMAC
     * (ver {@code IntervalsIcuStateSigner}).
     */
    @NotEmpty(message = "intervalsIcuPaths cannot be empty")
    private List<String> intervalsIcuPaths = new ArrayList<>(List.of(
        "/api/v1/integracoes/intervals-icu/callback"
    ));
}
