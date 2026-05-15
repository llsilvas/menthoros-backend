package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProvaRepository extends JpaRepository<Prova, UUID> {

    /**
     * Busca provas de um atleta em um intervalo de datas, ordenadas pela data
     */
    List<Prova> findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(
            Atleta atleta,
            LocalDate dataInicio,
            LocalDate dataFim
    );

    /**
     * Busca todas as provas de um atleta
     */
    List<Prova> findByAtletaOrderByDataProvaAsc(Atleta atleta);

    /**
     * Busca a prova alvo do atleta
     */
    List<Prova> findByAtletaAndProvaAlvoTrue(Atleta atleta);

    /**
     * Busca todas as provas de todos os atletas nos próximos 15 dias,
     * ordenadas pela data ascendente (mais próximas primeiro).
     * Usa JOIN FETCH para eager-load o relacionamento com Atleta.
     */
    @Query("SELECT DISTINCT p FROM Prova p JOIN FETCH p.atleta WHERE p.dataProva >= CURRENT_DATE AND p.dataProva <= :endDate ORDER BY p.dataProva ASC")
    List<Prova> findUpcomingProvasNext15Days(@Param("endDate") LocalDate endDate);

    /**
     * Valida se uma Prova pertence a um tenant específico.
     * Usado pelo TenantValidationAspect para validação de isolamento.
     */
    @Query("""
       SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Prova p
       WHERE p.id = :id AND p.atleta.assessoria.id = :tenantId
       """)
    boolean existsByIdAndAtleta_TenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
