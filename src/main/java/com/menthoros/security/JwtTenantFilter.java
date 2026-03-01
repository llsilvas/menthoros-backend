package com.menthoros.security;

import com.menthoros.entity.Usuario;
import com.menthoros.multitenancy.TenantContext;
import com.menthoros.services.UsuarioSyncService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter que processa cada request autenticada via JWT:
 * 1. Extrai o tenant_id do JWT
 * 2. Configura o TenantContext (ThreadLocal)
 * 3. Sincroniza o usuário do Keycloak com tb_usuario
 * 4. Garante limpeza do contexto ao final
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTenantFilter extends OncePerRequestFilter {

    private final UsuarioSyncService usuarioSyncService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // Só processa se for um JWT válido
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {

                // Extrai tenant_id do JWT (claim configurado no Keycloak)
                String tenantIdStr = jwt.getClaimAsString("tenant_id");

                if (tenantIdStr == null || tenantIdStr.isBlank()) {
                    log.error("JWT sem tenant_id - REJEITADO: subject={}, uri={}",
                            jwt.getSubject(), request.getRequestURI());
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"JWT sem tenant_id. Verifique a configuração do Keycloak Group.\"}");
                    return; // Não continua o processamento
                }

                UUID tenantId;
                try {
                    tenantId = UUID.fromString(tenantIdStr);
                } catch (IllegalArgumentException e) {
                    log.error("tenant_id inválido no JWT: '{}' - subject={}", tenantIdStr, jwt.getSubject());
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"tenant_id inválido no JWT\"}");
                    return;
                }

                // Configura tenant no contexto da thread
                TenantContext.setTenantId(tenantId);

                log.debug("Tenant {} configurado para a requisição {} (user: {})",
                        tenantId, request.getRequestURI(), jwt.getSubject());

                // Sincroniza usuário do Keycloak com tb_usuario
                try {
                    Usuario usuario = usuarioSyncService.syncUsuarioFromJwt(jwt, tenantId);
                    log.debug("Usuário sincronizado: id={}, email={}", usuario.getId(), usuario.getEmail());
                } catch (Exception e) {
                    log.error("Erro ao sincronizar usuário do Keycloak: subject={}, tenant={}",
                            jwt.getSubject(), tenantId, e);
                    // Continua mesmo se sync falhar (pode ser problema temporário)
                    // Mas registra o erro para investigação
                }
            }

            // Continua o processamento da request
            filterChain.doFilter(request, response);

        } finally {
            // CRÍTICO: Sempre limpa o contexto ao final (evita vazamento entre requests)
            TenantContext.clear();
        }
    }
}
