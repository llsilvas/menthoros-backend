package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.TipoTreino;
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

    @Query("""
       select tp from TreinoPlanejado tp
       where tp.atleta.id = :atletaId
         and tp.dataTreino between :dataInicio and :dataFim
       order by tp.dataTreino ASC
       """)
    List<TreinoPlanejado> findByAtletaIdAndDataBetween(@Param("atletaId") UUID atletaId,
                                                        @Param("dataInicio") LocalDate dataInicio,
                                                        @Param("dataFim") LocalDate dataFim);

    @Query("""
       SELECT COUNT(tp) FROM TreinoPlanejado tp
       WHERE tp.atleta.id = :atletaId
         AND tp.dataTreino >= :weekStart
         AND tp.dataTreino <= :weekEnd
       """)
    Integer countPlannedTrainings(@Param("atletaId") UUID atletaId,
                                  @Param("weekStart") LocalDate weekStart,
                                  @Param("weekEnd") LocalDate weekEnd);
}
