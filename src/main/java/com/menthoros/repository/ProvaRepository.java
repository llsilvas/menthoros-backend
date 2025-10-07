package com.menthoros.repository;

import com.menthoros.entity.Atleta;
import com.menthoros.entity.Prova;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
