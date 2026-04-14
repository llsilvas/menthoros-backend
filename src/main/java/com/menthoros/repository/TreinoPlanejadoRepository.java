package com.menthoros.repository;

import com.menthoros.entity.TreinoPlanejado;
import com.menthoros.enums.TipoTreino;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreinoPlanejadoRepository extends BaseRepository<TreinoPlanejado, UUID>{
    List<TreinoPlanejado> findByAtletaId(UUID atletaId);

    Optional<TreinoPlanejado> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
       select tp from TreinoPlanejado tp
       where tp.atleta.id = :atletaId
         and tp.dataTreino = :data
         and (:tipoTreino is null or tp.tipoTreino = :tipoTreino)
       """)
    Optional<TreinoPlanejado> matchByAtletaAndDateAndType(@Param("atletaId") UUID atletaId,
                                                          @Param("data") LocalDate data,
                                                          @Param("tipoTreino") TipoTreino tipoTreino);
}
