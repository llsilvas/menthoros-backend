package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.SeasonWeek;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonWeekRepository extends JpaRepository<SeasonWeek, UUID> {

    /** Semanas de uma temporada, em ordem, restritas ao tenant. */
    List<SeasonWeek> findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(UUID seasonPlanId, UUID tenantId);

    Optional<SeasonWeek> findByIdAndTenantId(UUID id, UUID tenantId);
}
