package com.menthoros.repository;

import com.menthoros.entity.TreinoRealizado;
import com.menthoros.enums.FonteDados;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreinoRealizadoRepository extends PagingAndSortingRepository<TreinoRealizado, UUID> {

    Optional<TreinoRealizado> findById(UUID id);

    TreinoRealizado save(TreinoRealizado entity);

    List<TreinoRealizado> findByAtletaIdOrderByDataTreinoAsc(UUID atletaId);

    Optional<TreinoRealizado> findByFonteDadosAndExternalId(FonteDados fonte, String externalId);

    @Query("select coalesce(sum(t.distanciaKm),0) from TreinoRealizado t where t.planoSemanal.id = :planoSemanalId")
    double sumDistanciaByPlanoSemanalId(@Param("planoSemanalId") UUID planoSemanalId);



}
