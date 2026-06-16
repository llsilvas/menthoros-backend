package br.com.menthoros.backend.security;

import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.UsuarioSyncService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resolução do tenant_id pelo JwtTenantFilter a partir do JWT — exercita o parsing de forma
 * determinística com tokens construídos (sem depender de um Keycloak real). Cobre a task 6.3.
 */
@DisplayName("JwtTenantFilter — resolução de tenant a partir do JWT")
class JwtTenantFilterTenantResolutionTest {

    private final UsuarioSyncService usuarioSyncService = mock(UsuarioSyncService.class);
    private final br.com.menthoros.backend.repository.UsuarioRepository usuarioRepository =
            mock(br.com.menthoros.backend.repository.UsuarioRepository.class);
    private final JwtTenantFilter filter = new JwtTenantFilter(usuarioSyncService, usuarioRepository);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sub = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("tenant resolvido → prossegue a cadeia")
    class Resolvido {

        @Test
        @DisplayName("via claim organization.<alias>.tenant_id (modelo Keycloak Organizations)")
        void viaOrganizationClaim() throws Exception {
            autenticar(Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .subject(sub.toString())
                    .claim("organization", Map.of("assessoria-demo",
                            Map.of("tenant_id", tenantId.toString())))
                    .build());
            when(usuarioSyncService.syncUsuarioFromJwt(any(Jwt.class), eq(tenantId)))
                    .thenReturn(usuarioAtivo());

            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);
            filter.doFilterInternal(request(), response, chain);

            verify(chain).doFilter(any(), any());
            verify(response, never()).setStatus(403);
        }

        @Test
        @DisplayName("via claim direto tenant_id (transição)")
        void viaClaimDireto() throws Exception {
            autenticar(Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .subject(sub.toString())
                    .claim("tenant_id", tenantId.toString())
                    .build());
            when(usuarioSyncService.syncUsuarioFromJwt(any(Jwt.class), eq(tenantId)))
                    .thenReturn(usuarioAtivo());

            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);
            filter.doFilterInternal(request(), response, chain);

            verify(chain).doFilter(any(), any());
            verify(response, never()).setStatus(403);
        }
    }

    @Nested
    @DisplayName("tenant ausente/inválido → 403 e não prossegue")
    class Rejeitado {

        @Test
        @DisplayName("sem claim de tenant → 403")
        void semTenant() throws Exception {
            autenticar(Jwt.withTokenValue("token").header("alg", "none").subject(sub.toString()).build());

            HttpServletResponse response = mock(HttpServletResponse.class);
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilterInternal(request(), response, chain);

            verify(response).setStatus(403);
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("tenant_id não-UUID → 403")
        void tenantInvalido() throws Exception {
            autenticar(Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .subject(sub.toString())
                    .claim("tenant_id", "nao-e-uuid")
                    .build());

            HttpServletResponse response = mock(HttpServletResponse.class);
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            FilterChain chain = mock(FilterChain.class);
            filter.doFilterInternal(request(), response, chain);

            verify(response).setStatus(403);
            verify(chain, never()).doFilter(any(), any());
        }
    }

    private void autenticar(Jwt jwt) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.setContext(context);
    }

    private HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/atletas");
        return request;
    }

    private Usuario usuarioAtivo() {
        return Usuario.builder()
                .id(sub)
                .keycloakId(sub.toString())
                .nome("Usuário")
                .email("u@exemplo.com")
                .role(UserRole.TECNICO)
                .ativo(true)
                .build();
    }
}
