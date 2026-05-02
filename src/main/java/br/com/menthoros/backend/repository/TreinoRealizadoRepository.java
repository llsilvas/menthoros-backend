package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import org.springframework.data.jpa.repository.Modifying;
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

    Optional<TreinoRealizado> findByExternalIdAndAtletaId(String externalId, UUID atletaId);

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

    List<TreinoRealizado> findTreinoRealizadosByAtleta(Atleta atleta);

    List<TreinoRealizado> findTreinoRealizadosByAtletaId(UUID atletaId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TreinoRealizado t SET t.planoSemanal = null WHERE t.planoSemanal.id = :planoSemanalId")
    void desvinculardePlanoSemanal(@Param("planoSemanalId") UUID planoSemanalId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TreinoRealizado t SET t.treinoPlanejado = null WHERE t.treinoPlanejado.id IN " +
           "(SELECT tp.id FROM TreinoPlanejado tp WHERE tp.planoSemanal.id = :planoSemanalId)")
    void desvinculardeTreinosPlanejados(@Param("planoSemanalId") UUID planoSemanalId);

    @Query("""
       select t from TreinoRealizado t
       where t.atleta.id = :atletaId
         and t.dataTreino = :dataTreino
         and t.reconciliationStatus = :status
       order by t.dataTreino ASC
       """)
    List<TreinoRealizado> findByAtletaIdAndDataTreinoAndReconciliationStatus(
            @Param("atletaId") UUID atletaId,
            @Param("dataTreino") LocalDate dataTreino,
            @Param("status") ReconciliationStatus status);
}
