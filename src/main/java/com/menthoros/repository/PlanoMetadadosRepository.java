package com.menthoros.repository;

import com.menthoros.entity.PlanoMetaDados;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanoMetadadosRepository extends JpaRepository<PlanoMetaDados, UUID> {
    Optional<PlanoMetaDados> findByAtletaId(UUID atletaId);
}
