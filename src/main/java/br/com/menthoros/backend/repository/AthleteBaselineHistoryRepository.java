package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.AthleteBaselineHistory;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface AthleteBaselineHistoryRepository extends CrudRepository<AthleteBaselineHistory, UUID> {

    List<AthleteBaselineHistory> findByAtletaIdAndTenantIdOrderByCriadoEmAsc(UUID atletaId, UUID tenantId);
}
