package br.com.menthoros.backend.testsupport;

import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.services.UsuarioSyncService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Helpers de JWT para testes de autorização com {@link AuthWebMvcTestConfig}: papéis reais do
 * Keycloak (ROLE_*) + claim tenant_id, num único lugar — mudar o formato do token aqui propaga
 * para todas as classes de teste de autorização.
 */
public final class JwtTestSupport {

    public static final UUID TENANT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private JwtTestSupport() {
    }

    public static RequestPostProcessor tecnicoJwt() {
        return comPapel("TECNICO", "tecnico-keycloak-id");
    }

    public static RequestPostProcessor adminJwt() {
        return comPapel("ADMIN", "admin-keycloak-id");
    }

    public static RequestPostProcessor atletaJwt() {
        return comPapel("ATLETA", "atleta-keycloak-id");
    }

    private static RequestPostProcessor comPapel(String papel, String subject) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.claim("tenant_id", TENANT_ID.toString()).subject(subject));
    }

    /**
     * Stub do JwtTenantFilter: toda requisição autenticada sincroniza o usuário — sem este stub
     * o filtro recebe null e derruba a requisição antes do controller.
     */
    public static void stubUsuarioAtivo(UsuarioSyncService usuarioSyncService) {
        Usuario usuario = new Usuario();
        usuario.setAtivo(true);
        when(usuarioSyncService.syncUsuarioFromJwt(any(), any())).thenReturn(usuario);
    }
}
