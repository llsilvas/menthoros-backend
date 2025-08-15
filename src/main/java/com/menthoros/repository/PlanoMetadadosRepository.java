package com.menthoros.repository;

import com.menthoros.entity.PlanoMetaDados;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanoMetadadosRepository extends JpaRepository<PlanoMetaDados, UUID> {
    Optional<PlanoMetaDados> findByAtletaId(UUID atletaId);

    Optional<PlanoMetaDados> findByPlanoSemanalId(UUID planoSemanalId);
    
    @Query("""
    SELECT pm FROM PlanoMetaDados pm 
    WHERE pm.atleta.id = :atletaId 
    ORDER BY pm.dataCriacao DESC
    LIMIT 1
    """)
    Optional<PlanoMetaDados> findLatestByAtletaId(UUID atletaId);
}
