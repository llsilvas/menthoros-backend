package br.com.menthoros.backend.security;

import br.com.menthoros.backend.enums.UserRole;

/**
 * Porta para resolver a identidade do principal autenticado, isolando a camada de serviço do
 * acesso direto ao {@code SecurityContextHolder} (DIP — facilita teste e troca de mecanismo).
 */
public interface AuthenticatedPrincipalResolver {

    /**
     * @return o {@code sub} (subject) do JWT do usuário autenticado na requisição atual
     * @throws IllegalStateException se não houver um JWT autenticado no contexto
     */
    String getCurrentSubject();

    /**
     * @return {@code true} se o principal autenticado carrega a authority {@code ROLE_<papel>};
     *         {@code false} sem autenticação no contexto
     */
    boolean hasRole(UserRole role);
}
