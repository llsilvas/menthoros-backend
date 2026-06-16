package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para Usuario
 *
 * IMPORTANTE: Esta tabela é um CACHE do Keycloak.
 * Para operações de criação/atualização de usuários, use o KeycloakAdminService.
 * Este repository é usado apenas para leitura e sincronização.
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /**
     * Busca usuário pelo Keycloak ID (subject do JWT)
     * Usado na sincronização automática durante o login
     */
    Optional<Usuario> findByKeycloakId(String keycloakId);

    /**
     * Busca usuário pelo Keycloak ID (subject do JWT) dentro de um tenant específico.
     * Resolução tenant-aware do usuário atual (GET /api/v1/users/me).
     */
    Optional<Usuario> findByKeycloakIdAndAssessoria_Id(String keycloakId, UUID tenantId);

    /**
     * Busca usuário por email
     */
    Optional<Usuario> findByEmail(String email);

    /**
     * Busca usuário por email dentro de um tenant específico
     */
    Optional<Usuario> findByEmailAndAssessoriaId(String email, UUID tenantId);

    /**
     * Lista todos os usuários ativos de um tenant
     */
    @Query("SELECT u FROM Usuario u WHERE u.assessoria.id = :tenantId AND u.ativo = true ORDER BY u.nome")
    List<Usuario> findByTenantIdAndAtivoTrue(@Param("tenantId") UUID tenantId);

    /**
     * Lista todos os usuários de um tenant (ativos e inativos)
     */
    @Query("SELECT u FROM Usuario u WHERE u.assessoria.id = :tenantId ORDER BY u.ativo DESC, u.nome")
    List<Usuario> findByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Lista usuários por role dentro de um tenant
     */
    @Query("SELECT u FROM Usuario u WHERE u.assessoria.id = :tenantId AND u.role = :role AND u.ativo = true ORDER BY u.nome")
    List<Usuario> findByTenantIdAndRole(@Param("tenantId") UUID tenantId, @Param("role") UserRole role);

    /**
     * Conta usuários ativos de um tenant
     */
    @Query("SELECT COUNT(u) FROM Usuario u WHERE u.assessoria.id = :tenantId AND u.ativo = true")
    Long countByTenantIdAndAtivoTrue(@Param("tenantId") UUID tenantId);

    /**
     * Conta usuários com role específica em um tenant
     */
    @Query("SELECT COUNT(u) FROM Usuario u WHERE u.assessoria.id = :tenantId AND u.role = :role AND u.ativo = true")
    Long countByTenantIdAndRoleAndAtivoTrue(@Param("tenantId") UUID tenantId, @Param("role") UserRole role);

    /**
     * Verifica se existe usuário com email em um tenant
     */
    boolean existsByEmailAndAssessoriaId(String email, UUID tenantId);

    /**
     * Lista usuários que precisam sincronização (última sincronização > 1 hora)
     * Útil para job de sincronização em background
     */
    @Query(value = """
        SELECT * FROM tb_usuario u
        WHERE u.ativo = true
        AND (u.ultima_sinc IS NULL OR u.ultima_sinc < CURRENT_TIMESTAMP - INTERVAL '1 hour')
        ORDER BY u.ultima_sinc ASC NULLS FIRST
        """, nativeQuery = true)
    List<Usuario> findUsuariosPendenteSincronizacao();

    /**
     * Busca administradores de um tenant
     * Útil para envio de notificações
     */
    @Query("SELECT u FROM Usuario u WHERE u.assessoria.id = :tenantId AND u.role = 'ADMIN' AND u.ativo = true")
    List<Usuario> findAdminsByTenantId(@Param("tenantId") UUID tenantId);
}