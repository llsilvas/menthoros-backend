package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.Usuario;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Synchronize user information from Keycloak JWT into local cache.
 * Extracts user principal, roles, and maps to local Usuario entity.
 */
public interface UsuarioSyncService {
    Usuario syncUsuarioFromJwt(Jwt jwt, UUID tenantId);
    boolean precisaSincronizar(Usuario usuario);
    void syncUsuariosPendentes();
}
