package com.menthoros.repository;

import com.menthoros.entity.Atleta;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.UUID;

public interface AtletaRepository extends PagingAndSortingRepository<Atleta, UUID> {
    Atleta findById(UUID id);

    Atleta save(Atleta entity);
}
