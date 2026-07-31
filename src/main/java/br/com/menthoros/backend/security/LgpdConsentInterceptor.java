package br.com.menthoros.backend.security;

import br.com.menthoros.backend.config.lgpd.LgpdProperties;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.ConsentEnforcementMode;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.ConsentResolutionUnavailableException;
import br.com.menthoros.backend.exception.LgpdConsentRequiredException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.UsuarioLgpdConsentRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;
import java.util.UUID;

/**
 * Impede que um coach opere a plataforma sem ter aceitado as versões vigentes dos Termos e da
 * Política de Privacidade.
 *
 * <p><b>O que este gate é e o que não é:</b> ele não é a base legal do tratamento — o
 * {@link JwtTenantFilter} já sincroniza os dados do coach em {@code tb_usuario} em toda request
 * autenticada, antes deste ponto. É um controle de produto, e o modal do frontend é conveniência de
 * UX: a garantia real é este {@code 403}, porque qualquer cliente de API contornaria o modal.
 *
 * <p><b>Não é {@code @Component} de propósito:</b> {@code @WebMvcTest} inclui automaticamente todo
 * {@link HandlerInterceptor} component-scanned na fatia, mas não inclui as properties nem o
 * repositório de que este depende — o que derrubaria o contexto de 22 slices que nada têm a ver com
 * consentimento. Criado como {@code @Bean} em {@code LgpdWebMvcConfig}, fica fora do scan da fatia
 * e continua com injeção por construtor, que falha rápido se uma dependência sumir em produção.
 *
 * <p>A ordem das guardas é contrato, não estilo. Uma rota pública, tenant-less ou de outra role
 * precisa passar <b>antes</b> de qualquer consulta ao banco, e a consulta só acontece no caminho
 * estreito (escrita de coach fora da whitelist) — leitura nunca paga por ela.
 */
@Slf4j
@RequiredArgsConstructor
public class LgpdConsentInterceptor implements HandlerInterceptor {

    /** Métodos sem efeito de escrita no contrato REST do projeto. */
    private static final Set<String> METODOS_DE_LEITURA = Set.of("GET", "HEAD", "OPTIONS");

    /**
     * Rotas isentas por padrão MVC. O consentimento precisa estar aqui, senão bloquear a escrita
     * bloquearia o próprio aceite — deadlock. As demais rotas públicas (webhooks, callbacks,
     * waitlist) e as admin já são cobertas pelas guardas 1 e 2, que passam por ausência de
     * autenticação ou de tenant.
     */
    private static final Set<String> PADROES_ISENTOS = Set.of("/api/v1/users/me/consent");

    private final LgpdProperties lgpdProperties;
    private final UsuarioLgpdConsentRepository consentRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        ConsentEnforcementMode modo = lgpdProperties.getConsentEnforcement();
        if (modo == ConsentEnforcementMode.OFF) {
            return true;
        }

        if (METODOS_DE_LEITURA.contains(request.getMethod())) {
            return true;
        }

        // Guarda 1 — sem JWT: rota pública (webhook, callback, waitlist). Não é assunto deste gate.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            return true;
        }

        // Guarda 2 — sem tenant: rota tenant-less por design (/api/admin/**, /api/v1/waitlist),
        // que o JwtTenantFilter isenta explicitamente.
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return true;
        }

        // Guarda 3 — só TECNICO. Lida das authorities do JWT para não depender do Usuario ainda.
        if (!temRoleTecnico(authentication)) {
            return true;
        }

        // Guarda 4 — whitelist por PADRÃO MVC resolvido, nunca pela URI crua: o padrão já
        // normalizou context path, "//", barra final, ";matrix=params" e percent-encoding.
        // Comparar getRequestURI() textualmente seria bypass trivial.
        if (isIsento(request)) {
            return true;
        }

        // Guarda 5 — sem usuário resolvido não se decide consentimento. 503, jamais 403.
        Usuario usuario = resolverUsuario(request, tenantId);

        // Guarda 6 — decisão.
        boolean consentiu = consentRepository
                .existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                        usuario.getId(), tenantId,
                        lgpdProperties.getPolicyVersion(), lgpdProperties.getTermsVersion());
        if (consentiu) {
            return true;
        }

        if (modo == ConsentEnforcementMode.REPORT_ONLY) {
            log.warn("[lgpd][report-only] escrita SERIA bloqueada por falta de consentimento: "
                            + "usuarioId={}, tenantId={}, metodo={}, rota={}",
                    usuario.getId(), tenantId, request.getMethod(), padraoDe(request));
            return true;
        }

        log.warn("[lgpd] escrita bloqueada por falta de consentimento: usuarioId={}, tenantId={}, "
                        + "metodo={}, rota={}",
                usuario.getId(), tenantId, request.getMethod(), padraoDe(request));
        throw new LgpdConsentRequiredException(
                "É necessário aceitar os Termos de Uso e a Política de Privacidade para continuar.");
    }

    private boolean temRoleTecnico(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + UserRole.TECNICO.name())::equals);
    }

    private boolean isIsento(HttpServletRequest request) {
        return PADROES_ISENTOS.contains(padraoDe(request));
    }

    private String padraoDe(HttpServletRequest request) {
        Object padrao = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return padrao instanceof String s ? s : null;
    }

    private Usuario resolverUsuario(HttpServletRequest request, UUID tenantId) {
        Object atributo = request.getAttribute(JwtTenantFilter.USUARIO_ATTR);
        if (!(atributo instanceof Usuario usuario)) {
            log.error("[lgpd] usuário não resolvido na request (atributo ausente ou de tipo "
                    + "inesperado): tenantId={}, rota={}", tenantId, padraoDe(request));
            throw new ConsentResolutionUnavailableException(
                    "Não foi possível verificar o consentimento");
        }

        // O fail-safe do JwtTenantFilter resolve por keycloakId sem escopo de tenant, então a
        // instância pode, em tese, ser de outra assessoria. Decidir com ela seria decidir no escuro.
        if (usuario.getAssessoria() == null || !tenantId.equals(usuario.getAssessoria().getId())) {
            log.error("[lgpd] tenant divergente ao verificar consentimento: usuarioId={}, "
                            + "tenantUsuario={}, tenantContexto={}",
                    usuario.getId(),
                    usuario.getAssessoria() != null ? usuario.getAssessoria().getId() : null,
                    tenantId);
            throw new ConsentResolutionUnavailableException(
                    "Não foi possível verificar o consentimento");
        }
        return usuario;
    }
}
