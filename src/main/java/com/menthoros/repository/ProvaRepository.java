package com.menthoros.repository;

import com.menthoros.entity.Atleta;
import com.menthoros.entity.Prova;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvaRepository extends JpaRepository<Prova, UUID> {

    @Query("select p from Prova p where p.id = :id and p.assessoria.id = :tenantId")
    Optional<Prova> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

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
}
