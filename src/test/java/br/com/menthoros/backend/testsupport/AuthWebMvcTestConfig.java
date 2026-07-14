package br.com.menthoros.backend.testsupport;

import br.com.menthoros.backend.config.core.CoreSecurityConfig;
import br.com.menthoros.backend.config.core.CoreSecurityProperties;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.TenantValidationAspect;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Ativa a cadeia de segurança REAL num {@code @WebMvcTest}: sem este import o slice usa a
 * security default do Boot e {@code @PreAuthorize}/{@code @RequireTenant} são silenciosamente
 * ignorados (falso verde — ver observação de 2026-06-23 "root cause @EnableMethodSecurity").
 *
 * O teste que importar esta config precisa mockar ({@code @MockitoBean}): {@code JwtDecoder},
 * {@code UsuarioSyncService}, {@code UsuarioRepository} (deps do JwtTenantFilter) e
 * {@code TenantValidationRepository} (dep do aspect) — e stubar
 * {@code usuarioSyncService.syncUsuarioFromJwt} com usuário ativo nas requisições autenticadas.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(CoreSecurityProperties.class)
@EnableAspectJAutoProxy
@Import({CoreSecurityConfig.class, JwtTenantFilter.class, TenantValidationAspect.class})
public class AuthWebMvcTestConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        return new UrlBasedCorsConfigurationSource();
    }
}
