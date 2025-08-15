package com.menthoros.repository;

import com.menthoros.entity.Atleta;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtletaRepository extends PagingAndSortingRepository<Atleta, UUID> {
    Optional<Atleta> findById(UUID id);

    Atleta save(Atleta entity);

    @Query("""
    select distinct atl from Atleta atl 
    left join fetch atl.diasDisponiveis 
    where atl.ativo = 'ATIVO' 
    order by atl.nome ASC 
    """)
    List<Atleta> findAllAtletasWithDias();
    
    @Query("""
    select distinct atl from Atleta atl 
    left join fetch atl.provas 
    where atl.ativo = 'ATIVO' 
    order by atl.nome ASC 
    """)
    List<Atleta> findAllAtletasWithProvas();
    
    @Query("""
    select atl from Atleta atl where atl.ativo = 'ATIVO' order by atl.nome ASC 
    """)
    List<Atleta> findAllAtletasWithBasicInfo();
    
    @Query("""
    select atl from Atleta atl where atl.ativo = 'ATIVO' order by atl.nome ASC 
    """)
    List<Atleta> findAllAtletas();
    
    // Método para buscar atleta específico - será usado para inicialização manual de coleções
    @Query("""
    select atl from Atleta atl 
    where atl.id = :id
    """)
    Optional<Atleta> findByIdBasic(UUID id);
}
