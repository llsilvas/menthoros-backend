package br.com.menthoros.backend.security;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolve o {@code Atleta} do usuário autenticado (tenant + {@code sub} do JWT → {@code Usuario}
 * → {@code Atleta}) e informa se o principal atua apenas como atleta. Ponto único da regra
 * "atleta só opera no próprio {@code atletaId}", extraído de {@code AtletaProgressServiceImpl}.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedAtletaResolver {

    private final AuthenticatedPrincipalResolver principalResolver;
    private final UsuarioRepository usuarioRepository;
    private final AtletaRepository atletaRepository;

    /**
     * Idempotent: YES — leitura. Side Effects: NONE. Tenant-aware: YES.
     *
     * @throws DomainNotFoundException se o usuário do token não existe no tenant ou não tem atleta vinculado
     */
    @Transactional(readOnly = true)
    public UUID resolverAtletaIdAtual() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        String sub = principalResolver.getCurrentSubject();
        Usuario usuario = usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Usuário autenticado não encontrado no tenant"));
        Atleta atleta = atletaRepository.findByUsuario_IdAndAssessoria_Id(usuario.getId(), tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta vinculado ao usuário não encontrado"));
        return atleta.getId();
    }

    /**
     * {@code true} quando o principal tem papel ATLETA e nenhum papel de coach (TECNICO/ADMIN) —
     * é esse principal que fica restrito ao próprio {@code atletaId}.
     *
     * Idempotent: YES · Side Effects: NONE · Tenant-aware: N/A.
     */
    public boolean atuaComoAtleta() {
        return principalResolver.hasRole(UserRole.ATLETA)
                && !principalResolver.hasRole(UserRole.TECNICO)
                && !principalResolver.hasRole(UserRole.ADMIN);
    }
}
