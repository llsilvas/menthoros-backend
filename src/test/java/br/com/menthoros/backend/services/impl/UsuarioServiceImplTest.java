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
            when(usuarioMapper.toMeOutputDto(eq(usuario), eq(null), anyBoolean(), anyString(), anyString()))
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
            when(usuarioMapper.toMeOutputDto(eq(usuario), eq(atleta), anyBoolean(), anyString(), anyString()))
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
            when(usuarioMapper.toMeOutputDto(eq(usuario), eq(null), anyBoolean(), anyString(), anyString()))
                    .thenReturn(dtoStub());

            usuarioService.getCurrentUser();

            verify(usuarioMapper).toMeOutputDto(eq(usuario), eq(null), anyBoolean(), anyString(), anyString());
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
            when(usuarioMapper.toMeOutputDto(eq(usuario), eq(null), anyBoolean(), anyString(), anyString()))
                    .thenReturn(dtoStub());

            usuarioService.getCurrentUser();

            verify(usuarioMapper).toMeOutputDto(eq(usuario), eq(null), anyBoolean(), anyString(), anyString());
            verify(usuarioMapper, never())
                    .toMeOutputDto(any(), any(Atleta.class), anyBoolean(), anyString(), anyString());
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

            verify(usuarioMapper).toMeOutputDto(any(), any(), eq(false),
                    eq(POLICY_VIGENTE), eq(TERMS_VIGENTE));
        }

        @Test
        @DisplayName("com registro das versões vigentes → granted true")
        void comRegistroVigente() {
            prepararUsuario();
            when(consentRepository.existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                    any(), eq(tenantId), eq(POLICY_VIGENTE), eq(TERMS_VIGENTE))).thenReturn(true);

            usuarioService.getCurrentUser();

            verify(usuarioMapper).toMeOutputDto(any(), any(), eq(true),
                    eq(POLICY_VIGENTE), eq(TERMS_VIGENTE));
        }

        @Test
        @DisplayName("consentimento só de versão antiga → granted false (bump reabre o gate)")
        void apenasVersaoAntiga() {
            prepararUsuario();
            // O repositório é consultado com as versões VIGENTES; um aceite antigo não casa.
            when(consentRepository.existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
                    any(), eq(tenantId), eq(POLICY_VIGENTE), eq(TERMS_VIGENTE))).thenReturn(false);

            usuarioService.getCurrentUser();

            verify(usuarioMapper).toMeOutputDto(any(), any(), eq(false), anyString(), anyString());
        }

        private void prepararUsuario() {
            Usuario usuario = usuario(UserRole.TECNICO);
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId))
                    .thenReturn(Optional.of(usuario));
            when(usuarioMapper.toMeOutputDto(any(), any(), anyBoolean(), anyString(), anyString()))
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
        return new UsuarioMeOutputDto(UUID.fromString(sub), "Usuário", "usuario@exemplo.com",
                UserRole.TECNICO, null, null, false, POLICY_VIGENTE, TERMS_VIGENTE);
    }
}
