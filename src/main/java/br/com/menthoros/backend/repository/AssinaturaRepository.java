package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Assinatura;
import br.com.menthoros.backend.enums.StatusAssinatura;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssinaturaRepository extends CrudRepository<Assinatura, UUID> {

    /** Lookup 1:1 por assessoria — idempotência do POST (CA14) e retrofit. */
    Optional<Assinatura> findByAssessoriaId(UUID assessoriaId);

    /** Lookup do webhook (o payload do Asaas não traz tenant_id — design.md Decisão 4). */
    Optional<Assinatura> findByAsaasSubscriptionId(String asaasSubscriptionId);

    /** Query do job de carência (CA5): inadimplentes com {@code overdueDesde} antes do corte. */
    List<Assinatura> findByStatusAndOverdueDesdeBefore(StatusAssinatura status, Instant corte);
}
