package com.menthoros.repository;

import com.menthoros.entity.TreinoRealizado;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.UUID;

public interface TreinoRealizadoRepository extends PagingAndSortingRepository<TreinoRealizado, UUID> {

    TreinoRealizado findById(UUID id);

    TreinoRealizado save(TreinoRealizado entity);
}
