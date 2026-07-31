package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.lgpd.LgpdProperties;
import br.com.menthoros.backend.dto.input.ConsentInputDto;
import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.entity.UsuarioLgpdConsent;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.ConsentVersionStaleException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.LgpdConsentStatus;
import br.com.menthoros.backend.mapper.UsuarioMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.UsuarioLgpdConsentRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.AtletaService;
import br.com.menthoros.backend.services.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    /** Nome da constraint da V73 — só ela caracteriza "já aceitou estas versões". */
    private static final String UK_CONSENT_VERSOES = "uk_usuario_lgpd_consent_versoes";

    private final UsuarioRepository usuarioRepository;
    private final UsuarioLgpdConsentRepository consentRepository;
    private final AtletaService atletaService;
    private final UsuarioMapper usuarioMapper;
    private final AuthenticatedPrincipalResolver principalResolver;
    private final LgpdProperties lgpdProperties;

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
        String sub = principalResolver.getCurrentSubject();
        log.info("Resolvendo usuário atual: sub={}, tenantId={}", sub, tenantId);

        Usuario usuario = usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId)
                .orElseThrow(() -> {
                    // sub apenas no log — não expor identificador do Keycloak na resposta HTTP
                    log.warn("Usuário não encontrado no tenant: sub={}, tenantId={}", sub, tenantId);
                    return new DomainNotFoundException("Usuário não encontrado no tenant atual");
                });

        Atleta atleta = null;
        if (usuario.getRole() == UserRole.ATLETA) {
            atleta = atletaService.findVinculadoAoUsuario(usuario.getId()).orElse(null);
        }

        LgpdConsentStatus lgpd = resolverConsentimento(usuario.getId(), tenantId);

        log.info("Usuário atual resolvido: id={}, role={}, atletaVinculado={}, lgpdConsentGranted={}",
                usuario.getId(), usuario.getRole(), atleta != null, lgpd.granted());
        return usuarioMapper.toMeOutputDto(usuario, atleta, lgpd);
    }

    /**
     * Monta o estado de consentimento: o que está vigente (config) e o que foi de fato aceito
     * (último registro, tenant-scoped).
     *
     * <p>O último aceite pode ser de versão anterior à vigente — nesse caso {@code granted} é
     * {@code false} e a tela de privacidade mostra as duas, que é o que permite ao coach ver que
     * os termos mudaram desde o aceite dele.
     */
    private LgpdConsentStatus resolverConsentimento(UUID usuarioId, UUID tenantId) {
        String policyVigente = lgpdProperties.getPolicyVersion();
        String termsVigente = lgpdProperties.getTermsVersion();

        boolean granted = consentRepository
                .existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                        usuarioId, tenantId, policyVigente, termsVigente);

        return consentRepository.findTopByUsuario_IdAndTenantIdOrderByConsentedAtDesc(usuarioId, tenantId)
                .map(ultimo -> new LgpdConsentStatus(granted, policyVigente, termsVigente,
                        ultimo.getConsentedAt(), ultimo.getPolicyVersion(), ultimo.getTermsVersion()))
                .orElseGet(() -> new LgpdConsentStatus(granted, policyVigente, termsVigente,
                        null, null, null));
    }

    /**
     * Registra o consentimento LGPD do usuário autenticado para as versões vigentes.
     *
     * Idempotent: YES — reenvio das mesmas versões é no-op; a constraint única do banco arbitra a
     *   corrida de aceites simultâneos.
     * Side Effects: Database insert (tb_usuario_lgpd_consent) — nunca update, nunca delete.
     * Tenant-aware: YES — resolve o caller pelo sub do JWT e valida que o tenant do usuário bate
     *   com o TenantContext antes de gravar.
     *
     * <p><b>Sem {@code @Transactional} de propósito</b> — cada chamada ao repositório roda na
     * própria transação. Capturar {@link DataIntegrityViolationException} dentro de uma transação
     * ativa a deixaria marcada <i>rollback-only</i>: o catch engoliria a exceção, o método
     * retornaria sucesso, e o commit estouraria depois, fora do alcance do try — virando 500 num
     * caminho que o contrato promete idempotente. Mesmo motivo e mesmo padrão de
     * {@code WaitlistServiceImpl}.
     */
    @Override
    public void registerConsent(ConsentInputDto input) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        String sub = principalResolver.getCurrentSubject();
        log.info("Registrando consentimento LGPD: sub={}, tenantId={}, policyVersion={}, termsVersion={}",
                sub, tenantId, input.policyVersion(), input.termsVersion());

        String policyVigente = lgpdProperties.getPolicyVersion();
        String termsVigente = lgpdProperties.getTermsVersion();

        // As versões que o cliente exibiu precisam ser as vigentes. Se a Política mudou enquanto a
        // página estava aberta, gravar produziria registro de aceite de um texto que ele não viu.
        if (!policyVigente.equals(input.policyVersion()) || !termsVigente.equals(input.termsVersion())) {
            log.warn("Consentimento recusado por versão defasada: recebido policy={}/terms={}, "
                            + "vigente policy={}/terms={}",
                    input.policyVersion(), input.termsVersion(), policyVigente, termsVigente);
            throw new ConsentVersionStaleException(
                    "Os termos foram atualizados. Recarregue a página e leia a versão vigente.");
        }

        Usuario usuario = usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId)
                .orElseThrow(() -> {
                    log.warn("Usuário não encontrado no tenant: sub={}, tenantId={}", sub, tenantId);
                    return new DomainNotFoundException("Usuário não encontrado no tenant atual");
                });

        // tenant_id da tabela não tem FK e o fail-safe do JwtTenantFilter resolve por keycloakId sem
        // escopo de tenant. Carimbar o tenant errado num registro legal é pior que falhar.
        if (usuario.getAssessoria() == null
                || !tenantId.equals(usuario.getAssessoria().getId())) {
            log.error("Tenant divergente ao registrar consentimento: usuarioId={}, tenantUsuario={}, "
                            + "tenantContexto={}",
                    usuario.getId(),
                    usuario.getAssessoria() != null ? usuario.getAssessoria().getId() : null,
                    tenantId);
            throw new DomainNotFoundException("Usuário não encontrado no tenant atual");
        }

        UsuarioLgpdConsent consent = UsuarioLgpdConsent.builder()
                .usuario(usuario)
                .tenantId(tenantId)
                .policyVersion(policyVigente)
                .termsVersion(termsVigente)
                .build();

        try {
            consentRepository.saveAndFlush(consent);
            log.info("Consentimento LGPD registrado: usuarioId={}, tenantId={}", usuario.getId(), tenantId);
        } catch (DataIntegrityViolationException e) {
            // Só é no-op se a violação for da constraint de versões (corrida entre dois aceites).
            // Qualquer outra violação de integridade é erro real — propaga, senão um insert que
            // falhou por FK ou NOT NULL viraria falso sucesso.
            if (!violouConstraintDeVersoes(e)) {
                throw e;
            }
            log.info("Consentimento já registrado para estas versões: usuarioId={}, tenantId={}",
                    usuario.getId(), tenantId);
        }
    }

    /**
     * A violação é da constraint de versões (corrida legítima) ou de outra coisa?
     *
     * <p>Prefere o nome estruturado que o Hibernate expõe em
     * {@link ConstraintViolationException#getConstraintName()}. O fallback por texto existe porque
     * nem todo driver/dialeto popula esse campo — mas depender só do texto seria frágil: ele varia
     * com versão de driver e locale do servidor, e um falso negativo aqui transforma a corrida que
     * o contrato promete ser idempotente num 500.
     */
    private boolean violouConstraintDeVersoes(DataIntegrityViolationException e) {
        Throwable causa = e.getMostSpecificCause();
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve
                    && UK_CONSENT_VERSOES.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
        }
        return causa.getMessage() != null && causa.getMessage().contains(UK_CONSENT_VERSOES);
    }
}
