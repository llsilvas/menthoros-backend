package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.lgpd.LgpdProperties;
import br.com.menthoros.backend.dto.input.ConsentInputDto;
import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.dao.DataIntegrityViolationException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    private static final String POLICY_VIGENTE = "2026-06-30";
    private static final String TERMS_VIGENTE = "2026-06-30";

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioLgpdConsentRepository consentRepository;
    @Mock private AtletaService atletaService;
    @Mock private UsuarioMapper usuarioMapper;
    @Mock private AuthenticatedPrincipalResolver principalResolver;
    @Spy private LgpdProperties lgpdProperties = novasProperties();

    @InjectMocks private UsuarioServiceImpl usuarioService;

    private UUID tenantId;
    private String sub;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        sub = UUID.randomUUID().toString();
        TenantContext.setTenantId(tenantId);
        when(principalResolver.getCurrentSubject()).thenReturn(sub);
    }

    private static LgpdProperties novasProperties() {
        LgpdProperties props = new LgpdProperties();
        props.setPolicyVersion(POLICY_VIGENTE);
        props.setTermsVersion(TERMS_VIGENTE);
        return props;
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getCurrentUser")
    class GetCurrentUser {

        @Test
        @DisplayName("TECNICO retorna identidade sem resolver atleta")
        void tecnicoSemAtleta() {
            Usuario usuario = usuario(UserRole.TECNICO);
            UsuarioMeOutputDto esperado = dtoStub();
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));
            when(usuarioMapper.toMeOutputDto(eq(usuario), eq(null), any(LgpdConsentStatus.class)))
                    .thenReturn(esperado);

            UsuarioMeOutputDto resultado = usuarioService.getCurrentUser();

            assertThat(resultado).isEqualTo(esperado);
            verifyNoInteractions(atletaService);
        }

        @Test
        @DisplayName("ATLETA vinculado resolve o Atleta e mapeia com atletaId")
        void atletaVinculado() {
            Usuario usuario = usuario(UserRole.ATLETA);
            Atleta atleta = Atleta.builder().id(UUID.randomUUID()).nome("Atleta").build();
            UsuarioMeOutputDto esperado = dtoStub();
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));
            when(atletaService.findVinculadoAoUsuario(usuario.getId()))
                    .thenReturn(Optional.of(atleta));
            when(usuarioMapper.toMeOutputDto(eq(usuario), eq(atleta), any(LgpdConsentStatus.class)))
                    .thenReturn(esperado);

            UsuarioMeOutputDto resultado = usuarioService.getCurrentUser();

            assertThat(resultado).isEqualTo(esperado);
            verify(atletaService).findVinculadoAoUsuario(usuario.getId());
        }

        @Test
        @DisplayName("ATLETA sem vínculo mapeia com atleta null")
        void atletaSemVinculo() {
            Usuario usuario = usuario(UserRole.ATLETA);
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));
            when(atletaService.findVinculadoAoUsuario(usuario.getId()))
                    .thenReturn(Optional.empty());
            when(usuarioMapper.toMeOutputDto(eq(usuario), eq(null), any(LgpdConsentStatus.class)))
                    .thenReturn(dtoStub());

            usuarioService.getCurrentUser();

            verify(usuarioMapper).toMeOutputDto(eq(usuario), eq(null), any(LgpdConsentStatus.class));
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando usuário não existe no tenant")
        void usuarioInexistenteNoTenant() {
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> usuarioService.getCurrentUser())
                    .isInstanceOf(DomainNotFoundException.class)
                    .hasMessageContaining("não encontrado")
                    .hasMessageNotContaining(sub);

            verifyNoInteractions(atletaService);
            verifyNoInteractions(usuarioMapper);
        }

        @Test
        @DisplayName("isolamento de tenant: Atleta de outro tenant não é resolvido (atleta null)")
        void isolamentoTenant() {
            Usuario usuario = usuario(UserRole.ATLETA);
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));
            when(atletaService.findVinculadoAoUsuario(usuario.getId()))
                    .thenReturn(Optional.empty());
            when(usuarioMapper.toMeOutputDto(eq(usuario), eq(null), any(LgpdConsentStatus.class)))
                    .thenReturn(dtoStub());

            usuarioService.getCurrentUser();

            verify(usuarioMapper).toMeOutputDto(eq(usuario), eq(null), any(LgpdConsentStatus.class));
            verify(usuarioMapper, never())
                    .toMeOutputDto(any(), any(Atleta.class), any(LgpdConsentStatus.class));
        }
    }

    @Nested
    @DisplayName("getCurrentUser — derivação de lgpdConsentGranted")
    class DerivacaoConsentimento {

        @Test
        @DisplayName("sem registro de consentimento → granted false")
        void semRegistro() {
            prepararUsuario();
            when(consentRepository.existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                    any(), eq(tenantId), eq(POLICY_VIGENTE), eq(TERMS_VIGENTE))).thenReturn(false);

            usuarioService.getCurrentUser();

            assertThat(capturarLgpd().granted()).isFalse();
        }

        @Test
        @DisplayName("com registro das versões vigentes → granted true")
        void comRegistroVigente() {
            prepararUsuario();
            when(consentRepository.existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                    any(), eq(tenantId), eq(POLICY_VIGENTE), eq(TERMS_VIGENTE))).thenReturn(true);

            usuarioService.getCurrentUser();

            assertThat(capturarLgpd().granted()).isTrue();
        }

        @Test
        @DisplayName("consentimento só de versão antiga → granted false (bump reabre o gate)")
        void apenasVersaoAntiga() {
            prepararUsuario();
            // O repositório é consultado com as versões VIGENTES; um aceite antigo não casa.
            when(consentRepository.existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                    any(), eq(tenantId), eq(POLICY_VIGENTE), eq(TERMS_VIGENTE))).thenReturn(false);

            usuarioService.getCurrentUser();

            assertThat(capturarLgpd().granted()).isFalse();
        }

        private void prepararUsuario() {
            Usuario usuario = usuario(UserRole.TECNICO);
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));
            when(usuarioMapper.toMeOutputDto(any(), any(), any(LgpdConsentStatus.class)))
                    .thenReturn(dtoStub());
        }
    }

    @Nested
    @DisplayName("getCurrentUser — último aceite exposto")
    class UltimoAceiteExposto {

        @Test
        @DisplayName("propaga data e versões do último aceite")
        void propagaUltimoAceite() {
            prepararUsuario();
            Instant quando = Instant.parse("2026-07-31T19:23:43Z");
            when(consentRepository.findTopByUsuario_IdAndTenantIdOrderByConsentedAtDesc(any(), eq(tenantId)))
                    .thenReturn(Optional.of(UsuarioLgpdConsent.builder()
                            .tenantId(tenantId)
                            .policyVersion(POLICY_VIGENTE)
                            .termsVersion(TERMS_VIGENTE)
                            .consentedAt(quando)
                            .build()));

            usuarioService.getCurrentUser();

            LgpdConsentStatus lgpd = capturarLgpd();
            assertThat(lgpd.consentedAt()).isEqualTo(quando);
            assertThat(lgpd.acceptedPolicyVersion()).isEqualTo(POLICY_VIGENTE);
            assertThat(lgpd.acceptedTermsVersion()).isEqualTo(TERMS_VIGENTE);
        }

        @Test
        @DisplayName("sem nenhum aceite, os três campos vêm nulos sem quebrar")
        void semAceiteNaoQuebra() {
            prepararUsuario();
            when(consentRepository.findTopByUsuario_IdAndTenantIdOrderByConsentedAtDesc(any(), eq(tenantId)))
                    .thenReturn(Optional.empty());

            usuarioService.getCurrentUser();

            LgpdConsentStatus lgpd = capturarLgpd();
            assertThat(lgpd.consentedAt()).isNull();
            assertThat(lgpd.acceptedPolicyVersion()).isNull();
            assertThat(lgpd.acceptedTermsVersion()).isNull();
        }

        @Test
        @DisplayName("aceite de versão antiga: granted false, mas a versão ACEITA é exposta")
        void versaoAceitaAntigaAindaEExposta() {
            prepararUsuario();
            // É este o caso que a tela precisa mostrar: houve bump, o coach consentiu com o texto
            // antigo, e ele tem de conseguir ver COM O QUÊ consentiu — não só que está pendente.
            when(consentRepository.existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                    any(), eq(tenantId), anyString(), anyString())).thenReturn(false);
            when(consentRepository.findTopByUsuario_IdAndTenantIdOrderByConsentedAtDesc(any(), eq(tenantId)))
                    .thenReturn(Optional.of(UsuarioLgpdConsent.builder()
                            .tenantId(tenantId)
                            .policyVersion("2020-01-01")
                            .termsVersion("2020-01-01")
                            .consentedAt(Instant.parse("2020-01-01T00:00:00Z"))
                            .build()));

            usuarioService.getCurrentUser();

            LgpdConsentStatus lgpd = capturarLgpd();
            assertThat(lgpd.granted()).isFalse();
            assertThat(lgpd.acceptedPolicyVersion()).isEqualTo("2020-01-01");
            assertThat(lgpd.currentPolicyVersion()).isEqualTo(POLICY_VIGENTE);
        }

        private void prepararUsuario() {
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario(UserRole.TECNICO)));
            when(usuarioMapper.toMeOutputDto(any(), any(), any(LgpdConsentStatus.class)))
                    .thenReturn(dtoStub());
        }
    }

    @Nested
    @DisplayName("registerConsent")
    class RegisterConsent {

        @Test
        @DisplayName("primeiro aceite grava linha com versões vigentes e tenant do contexto")
        void primeiroAceite() {
            Usuario usuario = usuario(UserRole.TECNICO);
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));

            usuarioService.registerConsent(input(POLICY_VIGENTE, TERMS_VIGENTE));

            ArgumentCaptor<UsuarioLgpdConsent> captor =
                    ArgumentCaptor.forClass(UsuarioLgpdConsent.class);
            verify(consentRepository).saveAndFlush(captor.capture());
            UsuarioLgpdConsent gravado = captor.getValue();
            assertThat(gravado.getUsuario()).isEqualTo(usuario);
            assertThat(gravado.getTenantId()).isEqualTo(tenantId);
            assertThat(gravado.getPolicyVersion()).isEqualTo(POLICY_VIGENTE);
            assertThat(gravado.getTermsVersion()).isEqualTo(TERMS_VIGENTE);
        }

        @Test
        @DisplayName("versão de política defasada é recusada sem gravar")
        void politicaDefasada() {
            assertThatThrownBy(() ->
                    usuarioService.registerConsent(input("2020-01-01", TERMS_VIGENTE)))
                    .isInstanceOf(ConsentVersionStaleException.class);

            verifyNoInteractions(consentRepository);
            verifyNoInteractions(usuarioRepository);
        }

        @Test
        @DisplayName("versão de termos defasada é recusada sem gravar")
        void termosDefasados() {
            assertThatThrownBy(() ->
                    usuarioService.registerConsent(input(POLICY_VIGENTE, "2020-01-01")))
                    .isInstanceOf(ConsentVersionStaleException.class);

            verifyNoInteractions(consentRepository);
        }

        @Test
        @DisplayName("violação da constraint de versões é no-op idempotente")
        void corridaResolvidaPelaConstraint() {
            Usuario usuario = usuario(UserRole.TECNICO);
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));
            when(consentRepository.saveAndFlush(any(UsuarioLgpdConsent.class)))
                    .thenThrow(new DataIntegrityViolationException(
                            "erro", new RuntimeException(
                                    "duplicate key value violates unique constraint "
                                            + "\"uk_usuario_lgpd_consent_versoes\"")));

            usuarioService.registerConsent(input(POLICY_VIGENTE, TERMS_VIGENTE));
            // sem exceção: reenviar o mesmo aceite não é erro
        }

        @Test
        @DisplayName("violação de OUTRA constraint propaga — não vira falso sucesso")
        void outraViolacaoPropaga() {
            Usuario usuario = usuario(UserRole.TECNICO);
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));
            when(consentRepository.saveAndFlush(any(UsuarioLgpdConsent.class)))
                    .thenThrow(new DataIntegrityViolationException(
                            "erro", new RuntimeException("null value in column \"tenant_id\"")));

            assertThatThrownBy(() ->
                    usuarioService.registerConsent(input(POLICY_VIGENTE, TERMS_VIGENTE)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("usuário inexistente no tenant não grava")
        void usuarioInexistente() {
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    usuarioService.registerConsent(input(POLICY_VIGENTE, TERMS_VIGENTE)))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(consentRepository);
        }

        @Test
        @DisplayName("Usuario de tenant divergente não é aceito para gravação")
        void tenantDivergente() {
            Usuario usuario = usuario(UserRole.TECNICO);
            usuario.setAssessoria(Assessoria.builder().id(UUID.randomUUID()).nome("Outra").build());
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));

            assertThatThrownBy(() ->
                    usuarioService.registerConsent(input(POLICY_VIGENTE, TERMS_VIGENTE)))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(consentRepository);
        }

        private ConsentInputDto input(String policyVersion, String termsVersion) {
            return new ConsentInputDto(true, true, policyVersion, termsVersion);
        }
    }

    /** Captura o LgpdConsentStatus que o service montou e entregou ao mapper. */
    private LgpdConsentStatus capturarLgpd() {
        ArgumentCaptor<LgpdConsentStatus> captor = ArgumentCaptor.forClass(LgpdConsentStatus.class);
        verify(usuarioMapper).toMeOutputDto(any(), any(), captor.capture());
        return captor.getValue();
    }

    private Usuario usuario(UserRole role) {
        return Usuario.builder()
                .id(UUID.fromString(sub))
                .keycloakId(sub)
                .nome("Usuário")
                .email("usuario@exemplo.com")
                .role(role)
                .assessoria(Assessoria.builder().id(tenantId).nome("Assessoria").dominio("dom").build())
                .build();
    }

    private UsuarioMeOutputDto dtoStub() {
        return new UsuarioMeOutputDto(UUID.fromString(sub), "Usuário", "usuario@exemplo.com", null,
                UserRole.TECNICO, null, null, false, POLICY_VIGENTE, TERMS_VIGENTE, null, null, null);
    }
}
