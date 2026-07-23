package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.AtividadeProvenienciaDescartada;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface AtividadeProvenienciaDescartadaRepository extends CrudRepository<AtividadeProvenienciaDescartada, UUID> {

    /**
     * Tenant-scoped por design (correcao QA 2026-07-21, achado do security-reviewer) — sem
     * chamador ainda (auditoria de proveniencia descartada, ver Decisao 2 do design.md), mas
     * ja nasce tenant-safe para nao virar uma landmine cross-tenant quando um consumidor futuro
     * (ex.: tela de auditoria) for adicionado.
     */
    List<AtividadeProvenienciaDescartada> findByAtividadeIdAndTenantId(UUID atividadeId, UUID tenantId);
}
