package com.menthoros.repository;

import com.menthoros.entity.Atleta;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.TreinoPlanejado;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.enums.FonteDados;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    List<TreinoRealizado> findByAtletaIdOrderByDataTreinoDesc(UUID atletaId);

    /**
     * Busca treinos realizados de um atleta a partir de uma data limite, ordenados pela data decrescente
     */
    List<TreinoRealizado> findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(
            Atleta atleta,
            LocalDate dataLimite
    );

    List<TreinoRealizado> findByAtletaIdAndDataTreino(UUID atletaId, LocalDate data);

    @Query("SELECT MIN(t.dataTreino) FROM TreinoRealizado t WHERE t.atleta.id = :atletaId")
    LocalDate findDataPrimeiroTreino(@Param("atletaId") UUID atletaId);

    List<TreinoRealizado> findByAtletaIdAndDataTreinoBetween(UUID id, LocalDate semanaInicio, LocalDate semanaFim);

    Optional<PlanoMetaDados> findByPlanoSemanalId(UUID planoSemanalId);
}
