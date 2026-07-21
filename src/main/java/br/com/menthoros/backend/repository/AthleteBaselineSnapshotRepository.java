package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.AthleteBaselineSnapshot;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface AthleteBaselineSnapshotRepository extends CrudRepository<AthleteBaselineSnapshot, UUID> {

    Optional<AthleteBaselineSnapshot> findByAtletaIdAndTenantId(UUID atletaId, UUID tenantId);
}
