package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsável por sincronizar usuários do Keycloak com o banco local (tb_usuario)
 *
 * IMPORTANTE: tb_usuario é um CACHE dos dados do Keycloak
 * - Não armazena senhas
 * - É atualizado automaticamente no login
 * - Fonte da verdade: Keycloak
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UsuarioSyncServiceImpl implements UsuarioSyncService {

    private final UsuarioRepository usuarioRepository;
    private final AssessoriaRepository assessoriaRepository;

    /**
     * Sincroniza usuário a partir do JWT do Keycloak
     * Chamado automaticamente pelo JwtTenantFilter em cada request
     *
     * @param jwt JWT do Keycloak com claims do usuário
     * @param tenantId UUID da assessoria (tenant)
     * @return Usuario sincronizado
     */
    @Transactional
    public Usuario syncUsuarioFromJwt(Jwt jwt, UUID tenantId) {
        String keycloakId = jwt.getSubject(); // subject (sub) do JWT
        String email = jwt.getClaimAsString("email");
        String nome = jwt.getClaimAsString("given_name");
        String sobrenome = jwt.getClaimAsString("family_name");
        Boolean emailVerificado = jwt.getClaimAsBoolean("email_verified");

        // Extrai roles do JWT (podem vir como lista ou string)
        List<String> roles = extractRoles(jwt);
        UserRole userRole = mapToUserRole(roles);

        log.debug("Sincronizando usuário: keycloakId={}, email={}, tenantId={}", keycloakId, email, tenantId);

        // Busca ou cria o usuário
        Usuario usuario = usuarioRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> createNewUsuario(keycloakId, tenantId));

        // Atualiza dados do Keycloak
        usuario.setEmail(email);
        usuario.setNome(nome != null ? nome : email.split("@")[0]); // Fallback para parte do email
        usuario.setSobrenome(sobrenome);
        usuario.setEmailVerificado(emailVerificado != null ? emailVerificado : false);
        usuario.setRole(userRole);
        usuario.registrarAcesso();
        usuario.registrarSincronizacao();

        usuario = usuarioRepository.save(usuario);

        log.info("Usuário sincronizado: id={}, email={}, role={}, tenant={}",
                usuario.getId(), usuario.getEmail(), usuario.getRole(), tenantId);

        return usuario;
    }

    /**
     * Cria um novo usuário no banco local
     */
    private Usuario createNewUsuario(String keycloakId, UUID tenantId) {
        log.info("Criando novo usuário local: keycloakId={}, tenantId={}", keycloakId, tenantId);

        // Busca a assessoria (tenant)
        Assessoria assessoria = assessoriaRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Assessoria não encontrada: " + tenantId + ". " +
                        "Certifique-se de que o tenant_id no Keycloak Group está correto."
                ));

        // Usa o próprio keycloakId (subject) como ID do usuário
        // Isso garante consistência: subject do JWT = ID na tb_usuario
        Usuario usuario = Usuario.builder()
                .id(UUID.fromString(keycloakId)) // ID = subject do JWT
                .keycloakId(keycloakId)
                .assessoria(assessoria)
                .ativo(true)
                .build();
        return usuario;
    }

    /**
     * Extrai roles do JWT no formato padrão do Keycloak.
     *
     * Ordem de busca:
     *   1. realm_access.roles — estrutura padrão emitida pelo Keycloak
     *   2. roles              — claim flat para mappers customizados
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt) {
        // 1. realm_access.roles (padrão Keycloak)
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object realmRoles = realmAccess.get("roles");
            if (realmRoles instanceof List) {
                return (List<String>) realmRoles;
            }
        }

        // 2. claim flat "roles" (mapper customizado)
        Object rolesClaim = jwt.getClaim("roles");
        if (rolesClaim instanceof List) {
            return (List<String>) rolesClaim;
        } else if (rolesClaim instanceof String) {
            return List.of((String) rolesClaim);
        }

        log.warn("JWT sem claim 'roles' ou 'realm_access.roles': {}", jwt.getSubject());
        return List.of();
    }

    /**
     * Mapeia roles do Keycloak para enum UserRole local
     * Prioridade: ADMIN > TECNICO > VISUALIZADOR
     */
    private UserRole mapToUserRole(List<String> roles) {
        if (roles.contains("ADMIN")) {
            return UserRole.ADMIN;
        } else if (roles.contains("TECNICO")) {
            return UserRole.TECNICO;
        } else if (roles.contains("VISUALIZADOR")) {
            return UserRole.VISUALIZADOR;
        } else {
            log.warn("Usuário sem role reconhecida, usando VISUALIZADOR como padrão. Roles: {}", roles);
            return UserRole.VISUALIZADOR; // Default seguro: apenas leitura
        }
    }

    /**
     * Verifica se um usuário precisa ser sincronizado
     * (última sincronização > 1 hora)
     */
    public boolean precisaSincronizar(Usuario usuario) {
        return usuario.precisaSincronizar();
    }

    /**
     * Sincroniza todos os usuários pendentes (última_sinc > 1 hora)
     * Útil para job scheduled em background
     *
     * NOTA: Requer Keycloak Admin Client para buscar dados atualizados
     */
    public void syncUsuariosPendentes() {
        List<Usuario> usuariosPendentes = usuarioRepository.findUsuariosPendenteSincronizacao();

        log.info("Sincronização em background: {} usuários pendentes", usuariosPendentes.size());

        // TODO: Implementar busca via Keycloak Admin API
        // Por enquanto, apenas registra para indicar que o job está funcionando
        for (Usuario usuario : usuariosPendentes) {
            log.debug("Usuário pendente de sincronização: id={}, email={}, ultima_sinc={}",
                    usuario.getId(), usuario.getEmail(), usuario.getUltimaSinc());
        }

        // Quando KeycloakAdminService estiver implementado:
        // 1. Para cada usuário pendente
        // 2. Buscar dados atualizados no Keycloak
        // 3. Atualizar no banco local
        // 4. Registrar sincronização
    }
}