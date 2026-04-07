package com.menthoros.repository;

import com.menthoros.entity.Atleta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtletaRepository extends PagingAndSortingRepository<Atleta, UUID> {
    Optional<Atleta> findById(UUID id);

    Atleta save(Atleta entity);

    @Query("""
    select distinct atl from Atleta atl
    left join fetch atl.diasDisponiveis
    where atl.assessoria.id = :tenantId
      and atl.ativo = 'ATIVO'
    order by atl.nome ASC
    """)
    List<Atleta> findAllAtletasWithDias(@Param("tenantId") UUID tenantId);

    @Query("""
    select distinct atl from Atleta atl
    left join fetch atl.provas
    where atl.assessoria.id = :tenantId
      and atl.ativo = 'ATIVO'
    order by atl.nome ASC
    """)
    List<Atleta> findAllAtletasWithProvas(@Param("tenantId") UUID tenantId);

    @Query("""
    select atl from Atleta atl
    where atl.assessoria.id = :tenantId
      and atl.ativo = 'ATIVO'
    order by atl.nome ASC
    """)
    List<Atleta> findAllAtletasWithBasicInfo(@Param("tenantId") UUID tenantId);

    @Query("""
    select atl from Atleta atl
    where atl.assessoria.id = :tenantId
      and atl.ativo = 'ATIVO'
    order by atl.nome ASC
    """)
    List<Atleta> findAllAtletas(@Param("tenantId") UUID tenantId);

    /**
     * Busca atleta por ID garantindo que pertence ao tenant correto.
     * Retorna vazio se o ID existir mas for de outro tenant (evita vazamento de dados).
     */
    @Query("""
    select atl from Atleta atl
    where atl.id = :id
      and atl.assessoria.id = :tenantId
    """)
    Optional<Atleta> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    // Mantido para uso interno (services que já validam ownership por outros meios)
    @Query("""
    select atl from Atleta atl
    where atl.id = :id
    """)
    Optional<Atleta> findByIdBasic(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select atl from Atleta atl
    where atl.id = :id
    """)
    Optional<Atleta> findByIdForUpdate(@Param("id") UUID id);
}
