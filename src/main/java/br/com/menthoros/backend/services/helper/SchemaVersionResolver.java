package br.com.menthoros.backend.services.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Allowlist de tenants elegíveis ao schema v2 (semantic-session-schema) — piloto controlado, não
 * uma flag de produto genérica. {@code app.llm.plano.schema-version-v2-tenants} (CSV de UUIDs,
 * vazio por default) é lida uma vez no startup; sem endpoint de refresh — tirar um tenant do
 * piloto exige mudar a env var e reiniciar a aplicação (design.md, Decisão 7 — correção do
 * proposal: não é "sem deploy").
 *
 * <p>Idempotent: YES — leitura pura do set resolvido no startup.
 * Side Effects: NONE. Tenant-aware: YES — decide por {@code tenantId}.</p>
 */
@Slf4j
@Component
public class SchemaVersionResolver {

    private final Set<UUID> tenantsV2;

    public SchemaVersionResolver(@Value("${app.llm.plano.schema-version-v2-tenants:}") String tenantsV2Csv) {
        this.tenantsV2 = parse(tenantsV2Csv);
        if (!this.tenantsV2.isEmpty()) {
            log.info("[semantic-session-schema] {} tenant(s) na allowlist do schema v2", this.tenantsV2.size());
        }
    }

    /** {@code true} se o tenant está na allowlist do piloto v2; {@code false} (default) usa v1. */
    public boolean usaV2(UUID tenantId) {
        return tenantId != null && tenantsV2.contains(tenantId);
    }

    private static Set<UUID> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<UUID> ids = new HashSet<>();
        for (String bruto : csv.split(",")) {
            String token = bruto.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(token));
            } catch (IllegalArgumentException e) {
                // CSV inválido não derruba o startup — loga e ignora o token (task 10.1).
                log.warn("[semantic-session-schema] token inválido em app.llm.plano.schema-version-v2-tenants, ignorado: {}", token);
            }
        }
        return Set.copyOf(ids);
    }
}
