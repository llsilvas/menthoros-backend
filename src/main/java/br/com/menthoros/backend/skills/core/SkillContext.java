package br.com.menthoros.backend.skills.core;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Contexto de execução passado a todas as skills de domínio.
 *
 * <p>Encapsula dados de identidade (atleta, tenant) e contexto temporal para
 * garantir que as skills operam dentro do isolamento multi-tenant correto.</p>
 *
 * @param atletaId      ID do atleta sendo analisado
 * @param tenantId      ID do tenant (coach/organização) — isolamento multi-tenant
 * @param dataReferencia data de referência para cálculos temporais (geralmente hoje)
 * @param metadata      mapa extensível para contexto adicional específico de cada skill
 */
public record SkillContext(
        UUID atletaId,
        UUID tenantId,
        LocalDate dataReferencia,
        Map<String, Object> metadata
) {

    /**
     * Cria um contexto com metadata vazia (atalho para casos simples).
     *
     * @param atletaId       ID do atleta
     * @param tenantId       ID do tenant
     * @param dataReferencia data de referência para cálculos
     * @return novo SkillContext com metadata vazia
     */
    public static SkillContext of(UUID atletaId, UUID tenantId, LocalDate dataReferencia) {
        return new SkillContext(atletaId, tenantId, dataReferencia, Map.of());
    }
}
