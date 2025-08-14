package com.menthoros.repository;

import com.menthoros.entity.PlanoSemanal;
import com.menthoros.enums.PlanoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanoSemanalRepository extends JpaRepository<PlanoSemanal, UUID> {
    Optional<PlanoSemanal> findPlanoSemanalByAtletaIdAndTreinosPlanejadosDataTreino(UUID id, LocalDate localDate);

    @Query("""
      select ps from PlanoSemanal ps
      where ps.atleta.id = :atletaId
        and :data between ps.semanaInicio and ps.semanaFim
    """)
    Optional<PlanoSemanal> findByAtletaIdAndSemana(@Param("atletaId") UUID atletaId,
                                                   @Param("data") LocalDate data);

    Optional<PlanoSemanal> findTopByAtletaIdOrderBySemanaInicioDesc(UUID atletaId);

    Optional<PlanoSemanal> findByAtletaIdAndSemanaInicioBetween(UUID atletaId, LocalDate with, LocalDate with1);

    boolean existsByAtletaIdAndSemanaInicioLessThanEqualAndSemanaFimGreaterThanEqualAndStatusNot(UUID atletaId, LocalDate hoje, LocalDate hoje1, PlanoStatus status);
}
