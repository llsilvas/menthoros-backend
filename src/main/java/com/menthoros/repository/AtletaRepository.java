package com.menthoros.repository;

import com.menthoros.entity.Atleta;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Optional;
import java.util.UUID;

public interface AtletaRepository extends PagingAndSortingRepository<Atleta, UUID> {
    Optional<Atleta> findById(UUID id);

    Atleta save(Atleta entity);
}
