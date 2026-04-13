package com.menthoros.security;

import com.menthoros.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro de tenant para o profile 'local'.
 *
 * Injeta um tenant fixo de desenvolvimento no TenantContext em toda
 * requisição, dispensando JWT e Keycloak para desenvolvimento local.
 *
 * ⚠️  ATENÇÃO: Ativo apenas com -Dspring.profiles.active=local.
 *     NUNCA usar em produção ou CI.
 */
@Slf4j
@Component
@Profile("local")
public class LocalTenantFilter extends OncePerRequestFilter {

    private final UUID defaultTenantId;

    public LocalTenantFilter(
            @Value("${app.local-dev.default-tenant-id:00000000-0000-0000-0000-000000000001}") String defaultTenantId) {
        this.defaultTenantId = UUID.fromString(defaultTenantId);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            TenantContext.setTenantId(defaultTenantId);
            log.debug("[LOCAL] Tenant fixo injetado: {} para {}", defaultTenantId, request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
