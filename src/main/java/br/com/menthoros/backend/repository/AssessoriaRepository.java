package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Assessoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository para Assessoria (Tenant)
 */
@Repository
public interface AssessoriaRepository extends JpaRepository<Assessoria, UUID> {

    /**
     * Busca assessoria pelo Keycloak Group ID
     */
    Optional<Assessoria> findByKeycloakGroupId(String keycloakGroupId);

    /**
     * Busca assessoria pelo CNPJ
     */
    Optional<Assessoria> findByCnpj(String cnpj);

    /**
     * Verifica se existe assessoria com o CNPJ
     */
    boolean existsByCnpj(String cnpj);

    /**
     * Retorna a primeira assessoria ativa — usada como fallback em ambiente sem autenticação
     */
    Optional<Assessoria> findFirstByAtivoTrue();

    /**
     * Lista assessorias ativas
     */
    @Query("SELECT a FROM Assessoria a WHERE a.ativo = true ORDER BY a.nome")
    java.util.List<Assessoria> findByAtivoTrue();

    /**
     * Conta assessorias ativas
     */
    @Query("SELECT COUNT(a) FROM Assessoria a WHERE a.ativo = true")
    Long countByAtivoTrue();

    /**
     * Busca assessoria por plano
     */
    @Query("SELECT a FROM Assessoria a WHERE a.plano = :plano AND a.ativo = true ORDER BY a.nome")
    java.util.List<Assessoria> findByPlanoAndAtivoTrue(@Param("plano") br.com.menthoros.backend.enums.PlanoAssessoria plano);
}