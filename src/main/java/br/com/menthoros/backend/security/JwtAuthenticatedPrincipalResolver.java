package br.com.menthoros.backend.security;

import br.com.menthoros.backend.enums.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Implementação JWT da {@link AuthenticatedPrincipalResolver}: lê o subject do token autenticado
 * no {@code SecurityContextHolder}.
 */
@Component
public class JwtAuthenticatedPrincipalResolver implements AuthenticatedPrincipalResolver {

    @Override
    public String getCurrentSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        throw new IllegalStateException("Nenhum usuário autenticado na requisição atual");
    }

    @Override
    public boolean hasRole(UserRole role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || role == null) {
            return false;
        }
        String authority = "ROLE_" + role.name();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
