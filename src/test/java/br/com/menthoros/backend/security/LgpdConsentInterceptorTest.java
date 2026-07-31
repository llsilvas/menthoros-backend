package br.com.menthoros.backend.security;

import br.com.menthoros.backend.config.lgpd.LgpdProperties;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.ConsentEnforcementMode;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.LgpdConsentRequiredException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.UsuarioLgpdConsentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Comportamento do gate de consentimento em toda escrita de coach.
 *
 * <p>A ordem das guardas é o contrato: uma requisição pública, tenant-less ou de outra role precisa
 * <b>passar</b> antes de qualquer consulta, e um usuário que não resolve precisa virar {@code 503}
 * — nunca {@code 403}. Confundir "não consegui verificar" com "não consentiu" trava operação por
 * falha de infraestrutura e mente sobre a causa.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LgpdConsentInterceptorTest {

    private static final String POLICY = "2026-06-30";
    private static final String TERMS = "2026-06-30";

    @Mock private UsuarioLgpdConsentRepository consentRepository;

    private LgpdProperties lgpdProperties;
    private LgpdConsentInterceptor interceptor;
    private UUID tenantId;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        lgpdProperties = new LgpdProperties();
        lgpdProperties.setConsentEnforcement(ConsentEnforcementMode.ON);
        lgpdProperties.setPolicyVersion(POLICY);
        lgpdProperties.setTermsVersion(TERMS);
        interceptor = new LgpdConsentInterceptor(lgpdProperties, consentRepository);
        response = new MockHttpServletResponse();
        semConsentimento();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("guardas que deixam passar sem consultar nada")
    class GuardasDePassagem {

        @Test
        @DisplayName("1 — requisição sem Authentication (rota pública/webhook) passa")
        void semAuthentication() {
            MockHttpServletRequest request = post("/api/v1/strava/webhook");

            assertThat(interceptor.preHandle(request, response, handler())).isTrue();
            verifyNoInteractions(consentRepository);
        }

        @Test
        @DisplayName("2 — sem TenantContext (rota admin/waitlist) passa")
        void semTenantContext() {
            TenantContext.clear();
            autenticarComoTecnico();
            MockHttpServletRequest request = post("/api/admin/assessorias");
            comUsuario(request, usuarioTecnico());

            assertThat(interceptor.preHandle(request, response, handler())).isTrue();
            verifyNoInteractions(consentRepository);
        }

        @Test
        @DisplayName("3 — role diferente de TECNICO passa (atleta e admin fora do escopo)")
        void outraRole() {
            autenticarCom("ROLE_ATLETA");
            MockHttpServletRequest request = post("/api/v1/treinos");
            comUsuario(request, usuario(UserRole.ATLETA, tenantId));

            assertThat(interceptor.preHandle(request, response, handler())).isTrue();
            verifyNoInteractions(consentRepository);
        }

        @Test
        @DisplayName("4 — o próprio endpoint de consentimento passa (senão é deadlock)")
        void endpointDeConsentimento() {
            autenticarComoTecnico();
            MockHttpServletRequest request = post("/api/v1/users/me/consent");
            comUsuario(request, usuarioTecnico());

            assertThat(interceptor.preHandle(request, response, handler())).isTrue();
            verifyNoInteractions(consentRepository);
        }

        @ParameterizedTest(name = "método {0} nunca bloqueia")
        @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
        @DisplayName("leitura nunca é bloqueada")
        void leituraPassa(String metodo) {
            autenticarComoTecnico();
            MockHttpServletRequest request = new MockHttpServletRequest(metodo, "/api/v1/atletas");
            comPadrao(request, "/api/v1/atletas");
            comUsuario(request, usuarioTecnico());

            assertThat(interceptor.preHandle(request, response, handler())).isTrue();
            verifyNoInteractions(consentRepository);
        }
    }

    @Nested
    @DisplayName("guarda 5 — usuário não resolvido vira 503, nunca 403")
    class UsuarioNaoResolvido {

        @Test
        @DisplayName("atributo ausente → 503")
        void atributoAusente() {
            autenticarComoTecnico();
            MockHttpServletRequest request = post("/api/v1/atletas");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler()))
                    .isNotInstanceOf(LgpdConsentRequiredException.class);
            verifyNoInteractions(consentRepository);
        }

        @Test
        @DisplayName("atributo de tipo inesperado → 503, não 403")
        void atributoDeTipoErrado() {
            autenticarComoTecnico();
            MockHttpServletRequest request = post("/api/v1/atletas");
            request.setAttribute(JwtTenantFilter.USUARIO_ATTR, "não é um Usuario");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler()))
                    .isNotInstanceOf(LgpdConsentRequiredException.class);
            verifyNoInteractions(consentRepository);
        }

        @Test
        @DisplayName("tenant do usuário divergente do contexto → 503, não 403")
        void tenantDivergente() {
            autenticarComoTecnico();
            MockHttpServletRequest request = post("/api/v1/atletas");
            comUsuario(request, usuario(UserRole.TECNICO, UUID.randomUUID()));

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler()))
                    .isNotInstanceOf(LgpdConsentRequiredException.class);
            verifyNoInteractions(consentRepository);
        }
    }

    @Nested
    @DisplayName("guarda 6 — decisão de consentimento")
    class DecisaoDeConsentimento {

        @Test
        @DisplayName("coach sem consentimento em escrita → 403 LGPD_CONSENT_REQUIRED")
        void semConsentimentoBloqueia() {
            autenticarComoTecnico();
            MockHttpServletRequest request = post("/api/v1/atletas");
            comUsuario(request, usuarioTecnico());

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler()))
                    .isInstanceOf(LgpdConsentRequiredException.class);
        }

        @Test
        @DisplayName("coach com consentimento das versões vigentes passa")
        void comConsentimentoPassa() {
            autenticarComoTecnico();
            comConsentimento();
            MockHttpServletRequest request = post("/api/v1/atletas");
            comUsuario(request, usuarioTecnico());

            assertThat(interceptor.preHandle(request, response, handler())).isTrue();
        }

        @ParameterizedTest(name = "método de escrita {0} é bloqueado")
        @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
        @DisplayName("todos os métodos de escrita são cobertos")
        void todosOsMetodosDeEscrita(String metodo) {
            autenticarComoTecnico();
            MockHttpServletRequest request = new MockHttpServletRequest(metodo, "/api/v1/atletas");
            comPadrao(request, "/api/v1/atletas");
            comUsuario(request, usuarioTecnico());

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler()))
                    .isInstanceOf(LgpdConsentRequiredException.class);
        }
    }

    @Nested
    @DisplayName("GET com efeito colateral entra no gate")
    class GetComEfeitoColateral {

        @ParameterizedTest(name = "GET {0} exige consentimento")
        @ValueSource(strings = {"/api/v1/strava/auth", "/api/v1/strava/auth/url/{atletaId}"})
        @DisplayName("início de OAuth Strava é operação de coach e não escapa por ser GET")
        void oauthStravaNaoEscapa(String padrao) {
            autenticarComoTecnico();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", padrao);
            comPadrao(request, padrao);
            comUsuario(request, usuarioTecnico());

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler()))
                    .isInstanceOf(LgpdConsentRequiredException.class);
        }

        @Test
        @DisplayName("GET comum segue liberado")
        void getComumSegueLiberado() {
            autenticarComoTecnico();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/atletas");
            comPadrao(request, "/api/v1/atletas");
            comUsuario(request, usuarioTecnico());

            assertThat(interceptor.preHandle(request, response, handler())).isTrue();
            verifyNoInteractions(consentRepository);
        }
    }

    @Nested
    @DisplayName("flag de rollout")
    class FlagDeRollout {

        @Test
        @DisplayName("OFF não bloqueia nem consulta")
        void offNaoBloqueia() {
            lgpdProperties.setConsentEnforcement(ConsentEnforcementMode.OFF);
            autenticarComoTecnico();
            MockHttpServletRequest request = post("/api/v1/atletas");
            comUsuario(request, usuarioTecnico());

            assertThat(interceptor.preHandle(request, response, handler())).isTrue();
            verifyNoInteractions(consentRepository);
        }

        @Test
        @DisplayName("REPORT_ONLY não bloqueia, mas consulta para poder registrar")
        void reportOnlyNaoBloqueia() {
            lgpdProperties.setConsentEnforcement(ConsentEnforcementMode.REPORT_ONLY);
            autenticarComoTecnico();
            MockHttpServletRequest request = post("/api/v1/atletas");
            comUsuario(request, usuarioTecnico());

            assertThat(interceptor.preHandle(request, response, handler())).isTrue();
        }
    }

    @Nested
    @DisplayName("matching de rota — comparar URI cru seria bypass fácil")
    class MatchingDeRota {

        @ParameterizedTest(name = "URI \"{0}\" com padrão do consentimento continua isenta")
        @ValueSource(strings = {
                "/api/v1/users/me/consent",
                "/api/v1/users/me/consent/",
                "//api/v1/users/me/consent",
                "/api/v1/users/me/consent;jsessionid=abc",
                "/context/api/v1/users/me/consent"
        })
        @DisplayName("a isenção segue o padrão MVC resolvido, não a URI textual")
        void variacoesDeUriNaoBurlamAIsencao(String uri) {
            autenticarComoTecnico();
            MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
            // O Spring resolve o padrão do handler; é ele que o interceptor deve consultar.
            comPadrao(request, "/api/v1/users/me/consent");
            comUsuario(request, usuarioTecnico());

            assertThat(interceptor.preHandle(request, response, handler())).isTrue();
        }
    }

    // ---------- helpers ----------

    private MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        comPadrao(request, uri);
        return request;
    }

    private void comPadrao(MockHttpServletRequest request, String pattern) {
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
    }

    private void comUsuario(MockHttpServletRequest request, Usuario usuario) {
        request.setAttribute(JwtTenantFilter.USUARIO_ATTR, usuario);
    }

    private void autenticarComoTecnico() {
        autenticarCom("ROLE_TECNICO");
    }

    private void autenticarCom(String authority) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("scope", "openid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null,
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    private Usuario usuarioTecnico() {
        return usuario(UserRole.TECNICO, tenantId);
    }

    private Usuario usuario(UserRole role, UUID tenant) {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .keycloakId(UUID.randomUUID().toString())
                .nome("Coach")
                .email("coach@exemplo.com")
                .role(role)
                .ativo(true)
                .assessoria(Assessoria.builder().id(tenant).nome("Assessoria").build())
                .build();
    }

    private void semConsentimento() {
        when(consentRepository.existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                any(), any(), anyString(), anyString())).thenReturn(false);
    }

    private void comConsentimento() {
        when(consentRepository.existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                any(), any(), anyString(), anyString())).thenReturn(true);
    }

    private Object handler() {
        return new Object();
    }
}
