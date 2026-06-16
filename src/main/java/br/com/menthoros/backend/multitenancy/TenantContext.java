package br.com.menthoros.backend.multitenancy;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class TenantContext {

    // ThreadLocal simples (não InheritableThreadLocal): evita que threads filhas/de pool
    // herdem o tenant da requisição que as criou — vazamento cross-tenant. Código assíncrono
    // deve setar o tenant explicitamente (ver StravaWebhookServiceImpl / schedulers).
    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    /**
     * Define o tenant_id para a thread atual
     * @param tenantId UUID do tenant
     */
    public static void setTenantId(UUID tenantId) {
        log.debug("Setting tenant {} for current thread", tenantId);
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Retorna o tenant_id da thread atual, ou {@code null} se não configurado.
     *
     * <p>Accessor nullable: ausência de tenant é legítima em fluxos sem contexto (callback OAuth,
     * schedulers). Quem exige tenant deve usar {@link #getRequiredTenantId()} (que falha alto).
     */
    public static UUID getTenantId() {
        UUID tenantId = CURRENT_TENANT.get();
        if(tenantId == null) {
            log.debug("Nenhum tenant configurado na thread atual");
        }
        return tenantId;
    }

    /**
     * Retorna o tenant_id da thread atual ou lança exceção se não configurado
     * @return UUID do tenant
     * @throws IllegalStateException se tenant não estiver configurado
     */
    public static UUID getRequiredTenantId() {
        UUID tenantId = CURRENT_TENANT.get();
        if(tenantId == null) {
            throw new IllegalStateException("Tenant não configurado para a requisição atual");
        }
        return tenantId;
    }

    /**
     * Limpa o tenant_id da thread atual
     */
    public static void clear() {
        log.debug("Clearing tenant context");
        CURRENT_TENANT.remove();
    }

    /**
     * Verifica se há um tenant configurado na thread atual
     * @return true se tenant está configurado
     */
    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }
}
