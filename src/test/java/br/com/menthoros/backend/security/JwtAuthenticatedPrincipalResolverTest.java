package br.com.menthoros.backend.security;

import br.com.menthoros.backend.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtAuthenticatedPrincipalResolver")
class JwtAuthenticatedPrincipalResolverTest {

    private final JwtAuthenticatedPrincipalResolver resolver = new JwtAuthenticatedPrincipalResolver();

    @AfterEach
    void limpaContexto() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("hasRole")
    class HasRole {

        @Test
        @DisplayName("reconhece a authority ROLE_<papel>")
        void reconhecePapel() {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                    "sub", null, List.of(new SimpleGrantedAuthority("ROLE_ATLETA"))));

            assertThat(resolver.hasRole(UserRole.ATLETA)).isTrue();
            assertThat(resolver.hasRole(UserRole.TECNICO)).isFalse();
        }

        @Test
        @DisplayName("sem autenticação devolve false")
        void semAutenticacao() {
            assertThat(resolver.hasRole(UserRole.ATLETA)).isFalse();
        }
    }
}
