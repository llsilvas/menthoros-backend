package br.com.menthoros.backend.config.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("jwtAuthenticationConverter")
class JwtAuthoritiesConverterTest {

    private JwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CoreSecurityConfig(null, null).jwtAuthenticationConverter();
    }

    @Nested
    @DisplayName("conversão de roles do realm_access")
    class RealmAccessRoles {

        @Test
        @DisplayName("mapeia ATLETA para a authority ROLE_ATLETA")
        void mapeiaAtleta() {
            Jwt jwt = jwtComRoles(List.of("ATLETA"));

            var authorities = converter.convert(jwt).getAuthorities();

            assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                    .contains("ROLE_ATLETA");
        }

        @Test
        @DisplayName("prefixa todas as roles com ROLE_")
        void mapeiaMultiplasRoles() {
            Jwt jwt = jwtComRoles(List.of("ATLETA", "TECNICO"));

            var authorities = converter.convert(jwt).getAuthorities();

            assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("ROLE_ATLETA", "ROLE_TECNICO");
        }

        @Test
        @DisplayName("retorna vazio quando realm_access ausente")
        void semRealmAccess() {
            Jwt jwt = Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .subject("sub")
                    .build();

            var authorities = converter.convert(jwt).getAuthorities();

            assertThat(authorities).isEmpty();
        }

        @Test
        @DisplayName("retorna vazio quando roles não é uma coleção")
        void rolesInvalido() {
            Jwt jwt = Jwt.withTokenValue("token")
                    .header("alg", "none")
                    .subject("sub")
                    .claim("realm_access", Map.of("roles", "ATLETA"))
                    .build();

            var authorities = converter.convert(jwt).getAuthorities();

            assertThat(authorities).isEmpty();
        }
    }

    private Jwt jwtComRoles(List<String> roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("sub")
                .claim("realm_access", Map.of("roles", roles))
                .build();
    }
}
