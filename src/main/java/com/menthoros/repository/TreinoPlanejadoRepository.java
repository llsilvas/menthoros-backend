package com.menthoros.repository;

import com.menthoros.entity.TreinoPlanejado;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreinoPlanejadoRepository extends BaseRepository<TreinoPlanejado, UUID>{
    List<TreinoPlanejado> findByAtletaId(UUID atletaId);

    @Query("""
       select tp from TreinoPlanejado tp
       where tp.atleta.id = :atletaId
         and tp.dataTreino = :data
         and (:tipoTreino is null or lower(tp.tipoTreino) = lower(:tipoTreino))
       """)
    Optional<TreinoPlanejado> matchByAtletaAndDateAndType(@Param("atletaId") UUID atletaId,
                                                          @Param("data") LocalDate data,
                                                          @Param("tipoTreino") String tipoTreino);
}
