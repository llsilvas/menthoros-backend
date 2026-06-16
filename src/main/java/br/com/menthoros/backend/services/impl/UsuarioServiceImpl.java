package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.UsuarioMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final AtletaRepository atletaRepository;
    private final UsuarioMapper usuarioMapper;

    /**
     * Resolve o usuário atual pelo {@code sub} do JWT no tenant corrente. Quando a role for
     * {@code ATLETA}, resolve o {@link Atleta} vinculado de forma tenant-aware.
     *
     * Idempotent: YES — Read-only, sem mutação de estado.
     * Side Effects: NONE
     * Tenant-aware: YES — usa {@link TenantContext#getRequiredTenantId()} e queries tenant-scoped.
     *
     * @return identidade do usuário autenticado
     * @throws DomainNotFoundException se o usuário não existir no tenant atual
     */
    @Override
    @Transactional(readOnly = true)
    public UsuarioMeOutputDto getCurrentUser() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        String sub = resolveSubject();
        log.info("Resolvendo usuário atual: sub={}, tenantId={}", sub, tenantId);

        Usuario usuario = usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId)
                .orElseThrow(() -> {
                    // sub apenas no log — não expor identificador do Keycloak na resposta HTTP
                    log.warn("Usuário não encontrado no tenant: sub={}, tenantId={}", sub, tenantId);
                    return new DomainNotFoundException("Usuário não encontrado no tenant atual");
                });

        Atleta atleta = null;
        if (usuario.getRole() == UserRole.ATLETA) {
            atleta = atletaRepository
                    .findByUsuario_IdAndAssessoria_Id(usuario.getId(), tenantId)
                    .orElse(null);
        }

        log.info("Usuário atual resolvido: id={}, role={}, atletaVinculado={}",
                usuario.getId(), usuario.getRole(), atleta != null);
        return usuarioMapper.toMeOutputDto(usuario, atleta);
    }

    private String resolveSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        throw new IllegalStateException("Nenhum usuário autenticado na requisição atual");
    }
}
