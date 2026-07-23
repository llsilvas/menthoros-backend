package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.AthleteBaselineState;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface AthleteBaselineStateRepository extends CrudRepository<AthleteBaselineState, UUID> {

    Optional<AthleteBaselineState> findByAtletaIdAndTenantId(UUID atletaId, UUID tenantId);
}
