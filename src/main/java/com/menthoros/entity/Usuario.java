package com.menthoros.entity;

import com.menthoros.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade Usuario - Cache/Sincronização do Keycloak
 *
 * IMPORTANTE: Esta tabela NÃO armazena senhas.
 * É apenas um cache local dos usuários do Keycloak para:
 * - Melhorar performance (evitar chamadas ao Keycloak em toda query)
 * - Permitir relacionamentos (FK) com outras entidades
 * - Manter auditoria local (created_at, ultimo_acesso, etc)
 *
 * Fonte da verdade: Keycloak
 * Sincronização: Automática no primeiro login após cada atualização
 */
@Entity
@Table(name = "tb_usuario",
    indexes = {
        @Index(name = "idx_usuario_keycloak_id", columnList = "keycloak_id", unique = true),
        @Index(name = "idx_usuario_email", columnList = "email"),
        @Index(name = "idx_usuario_tenant", columnList = "tenant_id"),
        @Index(name = "idx_usuario_tenant_ativo", columnList = "tenant_id, ativo"),
        @Index(name = "idx_usuario_tenant_role", columnList = "tenant_id, role")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    /**
     * ID do usuário - MESMO UUID do subject (sub) do JWT do Keycloak
     * Não é gerado automaticamente, é definido na sincronização
     */
    @Id
    @Column(name = "id")
    private UUID id;

    /**
     * Tenant (assessoria) ao qual o usuário pertence
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Assessoria assessoria;

    // ===== DADOS SINCRONIZADOS DO KEYCLOAK =====

    /**
     * Subject (sub) do JWT - Identificador único no Keycloak
     * Geralmente é o mesmo valor que o campo 'id'
     */
    @Column(name = "keycloak_id", unique = true, nullable = false, length = 100)
    private String keycloakId;

    /**
     * Email do usuário (sincronizado do Keycloak)
     */
    @Column(name = "email", nullable = false, length = 100)
    private String email;

    /**
     * Nome do usuário (sincronizado do Keycloak - given_name)
     */
    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    /**
     * Sobrenome do usuário (sincronizado do Keycloak - family_name)
     */
    @Column(name = "sobrenome", length = 200)
    private String sobrenome;

    /**
     * Email verificado no Keycloak (email_verified claim)
     */
    @Column(name = "email_verificado")
    private Boolean emailVerificado = false;

    /**
     * URL do avatar/foto do usuário
     */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    // ===== METADADOS LOCAIS =====

    /**
     * Role do usuário (ADMIN, TECNICO, VISUALIZADOR)
     * Sincronizado do Keycloak (client roles)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    /**
     * Indica se o usuário está ativo
     * Controle local, pode ser diferente do Keycloak
     */
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    /**
     * Data/hora do último acesso do usuário
     * Atualizado a cada login/request
     */
    @Column(name = "ultimo_acesso")
    private LocalDateTime ultimoAcesso;

    /**
     * Data/hora da última sincronização com o Keycloak
     * Usado para decidir se precisa sincronizar novamente
     */
    @Column(name = "ultima_sinc")
    private LocalDateTime ultimaSinc;

    // ===== AUDITORIA =====

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ===== MÉTODOS DE CONVENIÊNCIA =====

    /**
     * Retorna o nome completo do usuário
     */
    public String getNomeCompleto() {
        if (sobrenome != null && !sobrenome.isBlank()) {
            return nome + " " + sobrenome;
        }
        return nome;
    }

    /**
     * Verifica se o usuário é administrador
     */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /**
     * Verifica se o usuário é técnico
     */
    public boolean isTecnico() {
        return role == UserRole.TECNICO;
    }

    /**
     * Verifica se o usuário tem permissão de escrita
     */
    public boolean podeEscrever() {
        return role == UserRole.ADMIN || role == UserRole.TECNICO;
    }

    /**
     * Verifica se o usuário precisa ser sincronizado com o Keycloak
     * (se passou mais de 1 hora desde a última sincronização)
     */
    public boolean precisaSincronizar() {
        if (ultimaSinc == null) {
            return true;
        }
        return ultimaSinc.plusHours(1).isBefore(LocalDateTime.now());
    }

    /**
     * Atualiza o timestamp de último acesso
     */
    public void registrarAcesso() {
        this.ultimoAcesso = LocalDateTime.now();
    }

    /**
     * Atualiza o timestamp de sincronização
     */
    public void registrarSincronizacao() {
        this.ultimaSinc = LocalDateTime.now();
    }
}