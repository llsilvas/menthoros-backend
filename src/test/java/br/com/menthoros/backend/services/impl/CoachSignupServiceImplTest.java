package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.CoachSignupInputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.SignupProvisioning;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.SignupProvisioningStatus;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.exception.KeycloakIntegrationException;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.SignupProvisioningRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import br.com.menthoros.backend.services.NovoUsuarioKeycloak;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CoachSignupServiceImpl: provisionamento e compensação")
class CoachSignupServiceImplTest {

    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private SignupProvisioningRepository provisioningRepository;
    @Mock private KeycloakOrganizationGateway keycloak;

    @InjectMocks private CoachSignupServiceImpl service;

    private static final String CHAVE = "idem-1";
    private static final String CORR = "corr-1";
    private static final String ORG_ID = "org-1";
    private UUID assessoriaId;
    private UUID usuarioKeycloakId;

    @BeforeEach
    void setUp() {
        assessoriaId = UUID.randomUUID();
        usuarioKeycloakId = UUID.randomUUID();
    }

    /**
     * Só o que o caminho realmente precisa. O default do Mockito já devolve {@code Optional.empty()}
     * e {@code false}, que é exatamente o "nada em uso" das pré-checagens — stub para isso seria
     * ruído, e o strict stubbing o denunciaria.
     */
    private void stubAteOrganizacao() {
        when(provisioningRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(assessoriaRepository.save(any())).thenAnswer(i -> {
            Assessoria a = i.getArgument(0);
            if (a.getId() == null) {
                a.setId(assessoriaId);
            }
            return a;
        });
        when(keycloak.criarOrganization(anyString(), anyString(), any())).thenReturn(ORG_ID);
    }

    private void stubProvisionamentoFeliz() {
        stubAteOrganizacao();
        when(keycloak.criarUsuario(any())).thenReturn(usuarioKeycloakId.toString());
        when(usuarioRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static CoachSignupInputDto entrada() {
        return new CoachSignupInputDto("Maria Treinadora", "maria@exemplo.com",
                "senha-forte-o-suficiente", "Assessoria Corrida na Serra", "corridasserra", true, null);
    }

    private SignupProvisioning ultimoRastro() {
        var captor = ArgumentCaptor.forClass(SignupProvisioning.class);
        verify(provisioningRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Caminho feliz")
    class CaminhoFeliz {

        @Test
        @DisplayName("provisiona na ordem: assessoria → organização → usuário → local → e-mail")
        void ordemDoProvisionamento() {
            stubProvisionamentoFeliz();
            service.cadastrar(entrada(), CHAVE, CORR);

            InOrder ordem = inOrder(assessoriaRepository, keycloak, usuarioRepository);
            ordem.verify(assessoriaRepository).save(any());
            ordem.verify(keycloak).criarOrganization(anyString(), anyString(), eq(assessoriaId));
            ordem.verify(keycloak).criarUsuario(any());
            ordem.verify(usuarioRepository).save(any());
            ordem.verify(keycloak).enviarVerificacaoDeEmail(usuarioKeycloakId.toString());
        }

        @Test
        @DisplayName("cria o usuário HABILITADO com VERIFY_EMAIL — desabilitado o Keycloak recusaria enviar o e-mail")
        void usuarioNasceHabilitadoComRequiredAction() {
            stubProvisionamentoFeliz();
            service.cadastrar(entrada(), CHAVE, CORR);

            var captor = ArgumentCaptor.forClass(NovoUsuarioKeycloak.class);
            verify(keycloak).criarUsuario(captor.capture());

            assertThat(captor.getValue().habilitado()).isTrue();
            assertThat(captor.getValue().acoesObrigatorias()).containsExactly("VERIFY_EMAIL");
        }

        @Test
        @DisplayName("a assessoria nasce no plano BASIC com os limites do plano")
        void planoBasic() {
            stubProvisionamentoFeliz();
            service.cadastrar(entrada(), CHAVE, CORR);

            var captor = ArgumentCaptor.forClass(Assessoria.class);
            verify(assessoriaRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
            Assessoria a = captor.getAllValues().getFirst();

            assertThat(a.getMaxAtletas()).isEqualTo(10);
            assertThat(a.getMaxTecnicos()).isEqualTo(1);
            assertThat(a.getDominio()).isEqualTo("corridasserra");
            assertThat(a.getAtivo()).isTrue();
        }

        @Test
        @DisplayName("o id do Usuario local é o sub do Keycloak — não há id local independente")
        void idDoUsuarioLocalEhOSub() {
            stubProvisionamentoFeliz();
            service.cadastrar(entrada(), CHAVE, CORR);

            var captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());

            assertThat(captor.getValue().getId()).isEqualTo(usuarioKeycloakId);
            assertThat(captor.getValue().getKeycloakId()).isEqualTo(usuarioKeycloakId.toString());
        }

        @Test
        @DisplayName("a resposta não carrega token nem senha")
        void respostaSemSegredo() {
            stubProvisionamentoFeliz();
            var saida = service.cadastrar(entrada(), CHAVE, CORR);

            assertThat(saida.toString())
                    .doesNotContain("senha-forte-o-suficiente")
                    .doesNotContain("token");
            assertThat(saida.slug()).isEqualTo("corridasserra");
        }

        @Test
        @DisplayName("termina em ACTIVE")
        void terminaAtivo() {
            stubProvisionamentoFeliz();
            service.cadastrar(entrada(), CHAVE, CORR);
            assertThat(ultimoRastro().getStatus()).isEqualTo(SignupProvisioningStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Compensação")
    class Compensacao {

        @Test
        @DisplayName("falha ao criar o usuário: remove a organização E apaga a assessoria")
        void falhaAoCriarUsuario() {
            stubAteOrganizacao();
            when(keycloak.criarUsuario(any())).thenThrow(new KeycloakIntegrationException("boom"));

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(KeycloakIntegrationException.class);

            verify(keycloak).removerOrganization(ORG_ID);
            verify(assessoriaRepository).deleteById(assessoriaId);
            verify(keycloak, never()).removerUsuario(anyString());
        }

        @Test
        @DisplayName("desfaz na ordem INVERSA: usuário → organização → assessoria")
        void ordemInversa() {
            stubProvisionamentoFeliz();
            doThrow(new KeycloakIntegrationException("smtp fora"))
                    .when(keycloak).enviarVerificacaoDeEmail(anyString());

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(KeycloakIntegrationException.class);

            InOrder ordem = inOrder(usuarioRepository, keycloak, assessoriaRepository);
            ordem.verify(usuarioRepository).deleteById(usuarioKeycloakId);
            ordem.verify(keycloak).removerUsuario(usuarioKeycloakId.toString());
            ordem.verify(keycloak).removerOrganization(ORG_ID);
            ordem.verify(assessoriaRepository).deleteById(assessoriaId);
        }

        @Test
        @DisplayName("falha no envio do e-mail NÃO deixa conta utilizável — o cadastro inteiro é desfeito")
        void falhaNoEmailDesfazTudo() {
            stubProvisionamentoFeliz();
            doThrow(new KeycloakIntegrationException("smtp fora"))
                    .when(keycloak).enviarVerificacaoDeEmail(anyString());

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR)).isInstanceOf(RuntimeException.class);

            verify(keycloak).removerUsuario(usuarioKeycloakId.toString());
            verify(assessoriaRepository).deleteById(assessoriaId);
            assertThat(ultimoRastro().getStatus()).isEqualTo(SignupProvisioningStatus.FAILED);
        }

        @Test
        @DisplayName("compensação bem-sucedida termina em FAILED, não em RECONCILIATION_REQUIRED")
        void compensacaoLimpaTerminaFailed() {
            stubAteOrganizacao();
            when(keycloak.criarUsuario(any())).thenThrow(new KeycloakIntegrationException("boom"));

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR)).isInstanceOf(RuntimeException.class);

            assertThat(ultimoRastro().getStatus()).isEqualTo(SignupProvisioningStatus.FAILED);
        }

        @Test
        @DisplayName("compensação que FALHA vira RECONCILIATION_REQUIRED e não é retentada")
        void compensacaoQueFalha() {
            stubAteOrganizacao();
            when(keycloak.criarUsuario(any())).thenThrow(new KeycloakIntegrationException("boom"));
            doThrow(new KeycloakIntegrationException("keycloak fora"))
                    .when(keycloak).removerOrganization(anyString());

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR)).isInstanceOf(RuntimeException.class);

            var rastro = ultimoRastro();
            assertThat(rastro.getStatus()).isEqualTo(SignupProvisioningStatus.RECONCILIATION_REQUIRED);
            assertThat(rastro.getErrorDetail()).contains("compensação");
            // Parou na falha: não seguiu apagando a assessoria, e o rastro registra o órfão.
            verify(assessoriaRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("o detalhe do erro registrado não contém a senha")
        void erroRegistradoSemSenha() {
            stubAteOrganizacao();
            when(keycloak.criarUsuario(any())).thenThrow(new KeycloakIntegrationException("boom"));

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR)).isInstanceOf(RuntimeException.class);

            assertThat(ultimoRastro().getErrorDetail()).doesNotContain("senha-forte-o-suficiente");
        }
    }

    @Nested
    @DisplayName("Conflitos e idempotência")
    class Conflitos {

        @Test
        @DisplayName("slug em uso: 409 sem tocar o Keycloak")
        void slugEmUso() {
            when(assessoriaRepository.findByDominio("corridasserra"))
                    .thenReturn(Optional.of(new Assessoria()));

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(DuplicateResourceException.class);

            verifyNoInteractions(keycloak);
        }

        @Test
        @DisplayName("e-mail já existente no Keycloak: 409 sem criar nada")
        void emailNoKeycloak() {
            when(keycloak.buscarUsuarioIdPorEmail("maria@exemplo.com")).thenReturn(Optional.of("u-9"));

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(assessoriaRepository, never()).save(any());
            verify(keycloak, never()).criarOrganization(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("reenvio com a MESMA chave e mesmo payload devolve o resultado sem recriar nada")
        void reenvioIdempotente() {
            stubProvisionamentoFeliz();
            var original = SignupProvisioning.builder()
                    .idempotencyKey(CHAVE).slug("corridasserra").email("maria@exemplo.com")
                    .status(SignupProvisioningStatus.ACTIVE).correlationId(CORR)
                    .build();
            // O hash tem de ser o mesmo que o serviço calcula para a mesma entrada.
            service.cadastrar(entrada(), "outra-chave", CORR);
            original.setRequestHash(ultimoRastro().getRequestHash());

            when(provisioningRepository.findByIdempotencyKey(CHAVE)).thenReturn(Optional.of(original));
            org.mockito.Mockito.clearInvocations(assessoriaRepository, keycloak, usuarioRepository);

            var saida = service.cadastrar(entrada(), CHAVE, CORR);

            assertThat(saida.slug()).isEqualTo("corridasserra");
            verifyNoInteractions(keycloak);
            verify(assessoriaRepository, never()).save(any());
        }

        @Test
        @DisplayName("mesma chave com payload DIFERENTE é conflito — senão devolveria o cadastro alheio")
        void chaveReusadaComOutroPayload() {
            var original = SignupProvisioning.builder()
                    .idempotencyKey(CHAVE).slug("outro-slug").email("outro@exemplo.com")
                    .requestHash("hash-de-outra-coisa")
                    .status(SignupProvisioningStatus.ACTIVE).correlationId(CORR)
                    .build();
            when(provisioningRepository.findByIdempotencyKey(CHAVE)).thenReturn(Optional.of(original));

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(DuplicateResourceException.class);

            verifyNoInteractions(keycloak);
        }
    }

    @Nested
    @DisplayName("Honeypot")
    class Honeypot {

        @Test
        @DisplayName("responde como sucesso mas NÃO cria nada — erro ensinaria o bot qual campo o denunciou")
        void honeypotNaoCriaNada() {
            var comIsca = new CoachSignupInputDto("Bot", "bot@exemplo.com", "senha-forte-o-suficiente",
                    "Assessoria", "corridasserra", true, "http://spam.example");

            var saida = service.cadastrar(comIsca, CHAVE, CORR);

            assertThat(saida.slug()).isEqualTo("corridasserra");
            verifyNoInteractions(keycloak, assessoriaRepository, usuarioRepository, provisioningRepository);
        }
    }
}
