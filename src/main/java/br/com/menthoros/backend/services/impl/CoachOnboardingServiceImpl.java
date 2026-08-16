package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.CoachOnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoachOnboardingServiceImpl implements CoachOnboardingService {

    private final UsuarioRepository usuarioRepository;
    private final AuthenticatedPrincipalResolver principalResolver;

    @Override
    @Transactional
    public void concluir() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        String sub = principalResolver.getCurrentSubject();

        // Filtrar por sub E tenant é o isolamento: um token com sub válido não alcança o usuário
        // homônimo de outra assessoria.
        Usuario usuario = usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId)
                .orElseThrow(() -> {
                    // sub apenas no log — não expor identificador do Keycloak na resposta HTTP
                    log.warn("Conclusão de onboarding recusada: usuário não encontrado no tenant. "
                            + "sub={}, tenantId={}", sub, tenantId);
                    return new DomainNotFoundException("Usuário não encontrado no tenant atual");
                });

        if (usuario.isOnboardingConcluido()) {
            log.debug("Onboarding já concluído, nada a fazer: tenantId={}", tenantId);
            return;
        }

        usuario.setOnboardingConcluido(true);
        usuarioRepository.save(usuario);
        log.info("Onboarding do coach concluído: tenantId={}", tenantId);
    }
}
