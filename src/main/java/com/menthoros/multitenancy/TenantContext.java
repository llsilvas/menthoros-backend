package com.menthoros.multitenancy;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new InheritableThreadLocal<>();

    /**
     * Define o tenant_id para a thread atual
     * @param tenantId UUID do tenant
     */
    public static void setTenantId(UUID tenantId) {
        log.debug("Setting tenant {} for current thread", tenantId);
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Retorna o tenant_id da thread atual
     * @return UUID do tenant ou null se não configurado
     */
    public static UUID getTenantId() {
        UUID tenantId = CURRENT_TENANT.get();
        if(tenantId == null) {
            log.warn("Nenhum tenant configurado para a requisição");
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
