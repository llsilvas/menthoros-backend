package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.lgpd.LgpdProperties;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.dto.output.TrajetoShareConsentOutputDto;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.entity.UsuarioTrajetoShareConsent;
import br.com.menthoros.backend.enums.TrajetoShareConsentEvento;
import br.com.menthoros.backend.exception.ConsentVersionStaleException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.repository.UsuarioTrajetoShareConsentRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.TrajetoShareConsentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrajetoShareConsentServiceImpl implements TrajetoShareConsentService {

    private final UsuarioTrajetoShareConsentRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final AuthenticatedPrincipalResolver principalResolver;
    private final LgpdProperties lgpdProperties;

    @Override
    @Transactional(readOnly = true)
    public TrajetoShareConsentOutputDto estadoAtual() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Usuario usuario = usuarioAtual(tenantId);
        return toDto(ultimo(usuario, tenantId));
    }

    @Override
    @Transactional
    public TrajetoShareConsentOutputDto conceder(String policyVersionApresentada) {
        String vigente = lgpdProperties.getPolicyVersion();
        if (!vigente.equals(policyVersionApresentada)) {
            // Gravar a vigente aqui registraria aceite de um texto que o atleta não viu.
            throw new ConsentVersionStaleException(
                    "Versão apresentada " + policyVersionApresentada + " difere da vigente " + vigente);
        }
        return registrar(TrajetoShareConsentEvento.CONCEDIDO);
    }

    @Override
    @Transactional
    public TrajetoShareConsentOutputDto revogar() {
        return registrar(TrajetoShareConsentEvento.REVOGADO);
    }

    private TrajetoShareConsentOutputDto registrar(TrajetoShareConsentEvento evento) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Usuario usuario = usuarioAtual(tenantId);
        Optional<UsuarioTrajetoShareConsent> atual = ultimo(usuario, tenantId);
        if (jaEsta(atual, evento)) {
            return toDto(atual);
        }
        UsuarioTrajetoShareConsent salvo = repository.save(UsuarioTrajetoShareConsent.builder()
                .usuario(usuario)
                .tenantId(tenantId)
                .policyVersion(lgpdProperties.getPolicyVersion())
                .evento(evento)
                .build());
        log.info("Consentimento de trajeto nos cards: evento={} usuarioId={} versao={}",
                evento, usuario.getId(), salvo.getPolicyVersion());
        return toDto(Optional.of(salvo));
    }

    /** Concessão repetida só é no-op se a anterior for na versão vigente; revogação repetida sempre é. */
    private boolean jaEsta(Optional<UsuarioTrajetoShareConsent> atual, TrajetoShareConsentEvento evento) {
        if (atual.isEmpty()) {
            return evento == TrajetoShareConsentEvento.REVOGADO;
        }
        UsuarioTrajetoShareConsent ultimo = atual.get();
        if (ultimo.getEvento() != evento) {
            return false;
        }
        return evento == TrajetoShareConsentEvento.REVOGADO
                || lgpdProperties.getPolicyVersion().equals(ultimo.getPolicyVersion());
    }

    private Optional<UsuarioTrajetoShareConsent> ultimo(Usuario usuario, UUID tenantId) {
        return repository.findFirstByUsuario_IdAndTenantIdOrderByRegistradoEmDesc(usuario.getId(), tenantId);
    }

    private Usuario usuarioAtual(UUID tenantId) {
        return usuarioRepository.findByKeycloakIdAndAssessoria_Id(principalResolver.getCurrentSubject(), tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Usuário autenticado não encontrado no tenant"));
    }

    private TrajetoShareConsentOutputDto toDto(Optional<UsuarioTrajetoShareConsent> ultimo) {
        String vigente = lgpdProperties.getPolicyVersion();
        boolean granted = ultimo
                .filter(e -> e.getEvento() == TrajetoShareConsentEvento.CONCEDIDO)
                .filter(e -> vigente.equals(e.getPolicyVersion()))
                .isPresent();
        return new TrajetoShareConsentOutputDto(granted, vigente,
                ultimo.map(UsuarioTrajetoShareConsent::getRegistradoEm).orElse(null));
    }
}
