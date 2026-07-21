package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface PerfilOnboardingAtletaRepository extends CrudRepository<PerfilOnboardingAtleta, UUID> {

    Optional<PerfilOnboardingAtleta> findByAtletaIdAndTenantId(UUID atletaId, UUID tenantId);
}
