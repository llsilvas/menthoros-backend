package br.com.menthoros.backend.security;

import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.UsuarioSyncService;
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
import java.util.Collection;
import java.util.Map;
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
    private final UsuarioRepository usuarioRepository;

    /**
     * Isenta {@code /api/admin/**} do filtro de tenant. Contrato: rotas admin são
     * <b>tenant-less por design</b> (operações de plataforma / provisionamento que atuam
     * sobre múltiplos tenants), protegidas por role administrativa, não por isolamento de tenant.
     *
     * <p>Consequência: nessas rotas o {@code TenantContext} NÃO é populado. Uma rota admin que
     * precise de tenant deve resolvê-lo <b>explicitamente</b> (ex.: por parâmetro), nunca via
     * {@code TenantContext.getRequiredTenantId()} — que lançaria {@code IllegalStateException}
     * (mapeada para 403) de forma difícil de diagnosticar.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // /api/v1/waitlist: endpoint público (pré-signup), tenant-less por design. Isentar garante
        // que um usuário autenticado que acerte essa rota (token injetado globalmente) não seja
        // rejeitado por ausência de tenant_id.
        return uri.startsWith("/api/admin/") || uri.startsWith("/api/v1/waitlist");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // Só processa se for um JWT válido
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {

                // Extrai tenant_id do JWT (suporta claim direta e estrutura organization.<group>.tenant_id)
                String tenantIdStr = extractTenantId(jwt);

                if (tenantIdStr == null || tenantIdStr.isBlank()) {
                    log.error("JWT sem tenant_id - REJEITADO: subject={}, uri={}",
                            jwt.getSubject(), request.getRequestURI());
                    writeJsonError(response, HttpStatus.FORBIDDEN,
                            "JWT sem tenant_id. Verifique a configuração do Keycloak Group.");
                    return; // Não continua o processamento
                }

                UUID tenantId;
                try {
                    tenantId = UUID.fromString(tenantIdStr);
                } catch (IllegalArgumentException e) {
                    log.error("tenant_id inválido no JWT: '{}' - subject={}", tenantIdStr, jwt.getSubject());
                    writeJsonError(response, HttpStatus.FORBIDDEN, "tenant_id inválido no JWT");
                    return;
                }

                // Configura tenant no contexto da thread
                TenantContext.setTenantId(tenantId);

                log.debug("Tenant {} configurado para a requisição {} (user: {})",
                        tenantId, request.getRequestURI(), jwt.getSubject());

                // Sincroniza usuário do Keycloak com tb_usuario
                Usuario usuario = null;
                boolean syncFalhou = false;
                try {
                    usuario = usuarioSyncService.syncUsuarioFromJwt(jwt, tenantId);
                    log.debug("Usuário sincronizado: id={}, email={}", usuario.getId(), usuario.getEmail());
                } catch (Exception e) {
                    log.error("Erro ao sincronizar usuário do Keycloak: subject={}, tenant={}",
                            jwt.getSubject(), tenantId, e);
                    syncFalhou = true;
                }

                // Fail-safe: o sync alimenta a decisão de acesso (ativo). Se ele falhar, faz uma
                // leitura direta do status para não deixar um usuário inativo passar. Se nem a leitura
                // direta for possível (ex.: banco fora), responde 503 — não decide acesso "no escuro".
                if (syncFalhou) {
                    try {
                        usuario = usuarioRepository.findByKeycloakId(jwt.getSubject()).orElse(null);
                    } catch (Exception e) {
                        log.error("Não foi possível verificar o status da conta (leitura direta): subject={}",
                                jwt.getSubject(), e);
                        writeJsonError(response, HttpStatus.SERVICE_UNAVAILABLE,
                                "Não foi possível verificar o status da conta");
                        return;
                    }
                }

                // Rejeita usuário desativado localmente (ativo=false): conta autenticada, mas bloqueada.
                // Só alcança aqui requisições com JWT — rotas públicas sem autenticação não passam por isto.
                if (usuario != null && Boolean.FALSE.equals(usuario.getAtivo())) {
                    log.warn("Usuário inativo - REJEITADO: id={}, tenant={}, uri={}",
                            usuario.getId(), tenantId, request.getRequestURI());
                    writeJsonError(response, HttpStatus.LOCKED, "Usuário inativo");
                    return;
                }
            }

            // Continua o processamento da request
            filterChain.doFilter(request, response);

        } finally {
            // CRÍTICO: Sempre limpa o contexto ao final (evita vazamento entre requests)
            TenantContext.clear();
        }
    }

    private void writeJsonError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private String extractTenantId(Jwt jwt) {
        String directTenantId = jwt.getClaimAsString("tenant_id");
        if (directTenantId != null && !directTenantId.isBlank()) {
            log.debug("tenant_id extraído via claim direto: {}", directTenantId);
            return directTenantId;
        }

        Object organizationClaim = jwt.getClaim("organization");
        if (organizationClaim == null) {
            log.warn("JWT sem claim 'organization': sub={}", jwt.getSubject());
            return null;
        }
        if (!(organizationClaim instanceof Map<?, ?> organizationMap)) {
            log.warn("Claim 'organization' não é Map ({}): sub={}", organizationClaim.getClass().getName(), jwt.getSubject());
            return null;
        }

        for (Map.Entry<?, ?> entry : organizationMap.entrySet()) {
            Object groupDataObj = entry.getValue();
            if (!(groupDataObj instanceof Map<?, ?> groupData)) {
                log.debug("Grupo '{}' não é Map, ignorando", entry.getKey());
                continue;
            }

            Object tenantIdObj = groupData.get("tenant_id");
            log.debug("Grupo '{}' tenant_id raw: {} ({})", entry.getKey(), tenantIdObj,
                    tenantIdObj == null ? "null" : tenantIdObj.getClass().getName());

            if (tenantIdObj instanceof String tenantId && !tenantId.isBlank()) {
                return tenantId;
            }

            if (tenantIdObj instanceof Collection<?> tenantIds && !tenantIds.isEmpty()) {
                Object first = tenantIds.iterator().next();
                if (first instanceof String tenantId && !tenantId.isBlank()) {
                    log.debug("tenant_id extraído via organization.{}.tenant_id[0]: {}", entry.getKey(), tenantId);
                    return tenantId;
                }
            }
        }

        log.warn("tenant_id não encontrado em nenhum grupo organization: sub={}, grupos={}",
                jwt.getSubject(), organizationMap.keySet());
        return null;
    }
}
