package br.com.menthoros.backend.security;

import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.UsuarioRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("JwtTenantFilter.doFilterInternal - rejeição de usuário inativo")
class JwtTenantFilterInactiveUserTest {

    private final UsuarioSyncService usuarioSyncService = mock(UsuarioSyncService.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final JwtTenantFilter filter = new JwtTenantFilter(usuarioSyncService, usuarioRepository);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sub = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("com JWT autenticado")
    class ComJwt {

        @Test
        @DisplayName("usuário ativo=false → 423 Locked e não prossegue a cadeia")
        void rejeitaInativo() throws Exception {
            autenticar();
            when(usuarioSyncService.syncUsuarioFromJwt(any(Jwt.class), eq(tenantId)))
                    .thenReturn(usuario(false));

            HttpServletRequest request = request("/api/v1/atletas");
            HttpServletResponse response = mock(HttpServletResponse.class);
            StringWriter body = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(body));
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(423);
            verify(response).setContentType("application/json;charset=UTF-8");
            assertThat(body.toString()).contains("inativo");
            verify(chain, never()).doFilter(any(), any());
            assertThat(TenantContext.getTenantId()).isNull(); // contexto limpo no finally
        }

        @Test
        @DisplayName("usuário ativo=true → prossegue a cadeia normalmente")
        void permiteAtivo() throws Exception {
            autenticar();
            when(usuarioSyncService.syncUsuarioFromJwt(any(Jwt.class), eq(tenantId)))
                    .thenReturn(usuario(true));

            HttpServletRequest request = request("/api/v1/atletas");
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).setStatus(anyInt());
        }
    }

    @Nested
    @DisplayName("fail-safe quando o sync falha")
    class SyncFalha {

        @Test
        @DisplayName("sync lança exceção + leitura direta inativa → 423")
        void fallbackInativo() throws Exception {
            autenticar();
            when(usuarioSyncService.syncUsuarioFromJwt(any(Jwt.class), eq(tenantId)))
                    .thenThrow(new RuntimeException("sync indisponível"));
            when(usuarioRepository.findByKeycloakId(sub.toString()))
                    .thenReturn(Optional.of(usuario(false)));

            HttpServletRequest request = request("/api/v1/atletas");
            HttpServletResponse response = mock(HttpServletResponse.class);
            StringWriter body = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(body));
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(423);
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("sync lança exceção + leitura direta ativa → prossegue")
        void fallbackAtivo() throws Exception {
            autenticar();
            when(usuarioSyncService.syncUsuarioFromJwt(any(Jwt.class), eq(tenantId)))
                    .thenThrow(new RuntimeException("sync indisponível"));
            when(usuarioRepository.findByKeycloakId(sub.toString()))
                    .thenReturn(Optional.of(usuario(true)));

            HttpServletRequest request = request("/api/v1/atletas");
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).setStatus(anyInt());
        }

        @Test
        @DisplayName("sync e leitura direta falham → 503, não decide acesso no escuro")
        void naoVerificavel() throws Exception {
            autenticar();
            when(usuarioSyncService.syncUsuarioFromJwt(any(Jwt.class), eq(tenantId)))
                    .thenThrow(new RuntimeException("sync indisponível"));
            when(usuarioRepository.findByKeycloakId(sub.toString()))
                    .thenThrow(new RuntimeException("banco indisponível"));

            HttpServletRequest request = request("/api/v1/atletas");
            HttpServletResponse response = mock(HttpServletResponse.class);
            StringWriter body = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(body));
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(503);
            verify(chain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("sem JWT (rota pública/anônima)")
    class SemJwt {

        @Test
        @DisplayName("não sincroniza nem bloqueia — apenas prossegue")
        void prossegueSemAutenticacao() throws Exception {
            HttpServletRequest request = request("/api/v1/status");
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(usuarioSyncService);
        }
    }

    private void autenticar() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(sub.toString())
                .claim("tenant_id", tenantId.toString())
                .build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.setContext(context);
    }

    private HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    private Usuario usuario(boolean ativo) {
        return Usuario.builder()
                .id(sub)
                .keycloakId(sub.toString())
                .nome("Usuário")
                .email("u@exemplo.com")
                .role(UserRole.ATLETA)
                .ativo(ativo)
                .build();
    }
}
