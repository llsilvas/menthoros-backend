package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.CoachSignupInputDto;
import br.com.menthoros.backend.dto.output.CoachSignupOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.SignupProvisioning;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.SignupProvisioningStatus;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.exception.KeycloakIntegrationException;
import br.com.menthoros.backend.exception.SignupRateLimitException;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.SignupProvisioningRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.FoundingInviteService;
import br.com.menthoros.backend.repository.FoundingInviteRepository;
import br.com.menthoros.backend.entity.FoundingInvite;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.ProvisioningOrigin;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import java.time.OffsetDateTime;
import java.util.List;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import br.com.menthoros.backend.services.NovoUsuarioKeycloak;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

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
    @Mock private FoundingInviteService foundingInviteService;
    @Mock private FoundingInviteRepository foundingInviteRepository;

    private CoachSignupServiceImpl service;

    private static final int LIMITE_POR_EMAIL_DIA = 3;
    private static final int TETO_DIARIO = 20;

    private static final String CHAVE = "idem-1";
    private static final String CORR = "corr-1";
    private static final String ORG_ID = "org-1";
    private UUID assessoriaId;
    private UUID usuarioKeycloakId;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        assessoriaId = UUID.randomUUID();
        usuarioKeycloakId = UUID.randomUUID();
        meterRegistry = new SimpleMeterRegistry();
        service = new CoachSignupServiceImpl(assessoriaRepository, usuarioRepository,
                provisioningRepository, keycloak, foundingInviteService, foundingInviteRepository,
                meterRegistry, LIMITE_POR_EMAIL_DIA, TETO_DIARIO);
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
                "senha-forte-o-suficiente", "Assessoria Corrida na Serra", "corridasserra", null);
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

            assertThat(a.getMaxAtletas()).isEqualTo(20);
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
        @DisplayName("o fundador recebe a role PROPRIETARIO no Keycloak")
        void fundadorRecebeRoleProprietario() {
            stubProvisionamentoFeliz();
            service.cadastrar(entrada(), CHAVE, CORR);

            verify(keycloak).atribuirRoleDeRealm(usuarioKeycloakId.toString(), "PROPRIETARIO");
        }

        /**
         * O dono continua contando como técnico no banco: `role` é single-valued e alimenta
         * `countByTenantIdAndRoleAndAtivoTrue`, com `maxTecnicos = 1` no BASIC. A propriedade
         * vive na flag `owner`, que a role composite do Keycloak reespelha a cada sync.
         */
        @Test
        @DisplayName("o Usuario local nasce TECNICO e dono")
        void usuarioLocalNasceTecnicoEDono() {
            stubProvisionamentoFeliz();
            service.cadastrar(entrada(), CHAVE, CORR);

            var captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());

            assertThat(captor.getValue().getRole()).isEqualTo(UserRole.TECNICO);
            assertThat(captor.getValue().isOwner()).isTrue();
        }

        /**
         * O fundador é o único que nasce pendente — é para ele que o wizard existe. Todo o resto
         * herda o `DEFAULT true` do banco, inclusive o técnico convidado depois.
         */
        @Test
        @DisplayName("o fundador nasce com onboarding pendente")
        void fundadorNasceComOnboardingPendente() {
            stubProvisionamentoFeliz();
            service.cadastrar(entrada(), CHAVE, CORR);

            var captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());

            assertThat(captor.getValue().isOnboardingConcluido()).isFalse();
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
                    "Assessoria", "corridasserra", "http://spam.example");

            var saida = service.cadastrar(comIsca, CHAVE, CORR);

            assertThat(saida.slug()).isEqualTo("corridasserra");
            verifyNoInteractions(keycloak, assessoriaRepository, usuarioRepository, provisioningRepository);
        }
    }

    @Nested
    @DisplayName("Limites anti-abuso")
    class Limites {

        @Test
        @DisplayName("limite diário por e-mail: 429 sem tocar o Keycloak")
        void limitePorEmail() {
            when(provisioningRepository.countByEmailAndCreatedAtAfter(eq("maria@exemplo.com"), any()))
                    .thenReturn((long) LIMITE_POR_EMAIL_DIA);

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(SignupRateLimitException.class);

            verifyNoInteractions(keycloak);
            verify(assessoriaRepository, never()).save(any());
        }

        @Test
        @DisplayName("teto diário global: 429 mesmo com o e-mail dentro do limite")
        void tetoDiarioGlobal() {
            when(provisioningRepository.countByCreatedAtAfter(any())).thenReturn((long) TETO_DIARIO);

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(SignupRateLimitException.class);

            verifyNoInteractions(keycloak);
        }

        @Test
        @DisplayName("dentro dos limites o cadastro segue normalmente")
        void dentroDosLimites() {
            stubProvisionamentoFeliz();
            when(provisioningRepository.countByEmailAndCreatedAtAfter(anyString(), any())).thenReturn(1L);
            when(provisioningRepository.countByCreatedAtAfter(any())).thenReturn(5L);

            assertThat(service.cadastrar(entrada(), CHAVE, CORR).slug()).isEqualTo("corridasserra");
        }

        @Test
        @DisplayName("o limite é checado ANTES da disponibilidade — não vaza se o e-mail existe")
        void limiteAntesDaDisponibilidade() {
            when(provisioningRepository.countByCreatedAtAfter(any())).thenReturn((long) TETO_DIARIO);

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(SignupRateLimitException.class);

            verify(assessoriaRepository, never()).findByDominio(anyString());
        }
    }

    @Nested
    @DisplayName("Observabilidade")
    class Observabilidade {

        private double contagem(String desfecho) {
            var contador = meterRegistry.find("signup.coach").tag("desfecho", desfecho).counter();
            return contador == null ? 0 : contador.count();
        }

        @Test
        @DisplayName("sucesso e desfechos de falha são contados separadamente")
        void contaDesfechos() {
            stubProvisionamentoFeliz();
            service.cadastrar(entrada(), CHAVE, CORR);

            assertThat(contagem("sucesso")).isEqualTo(1);
            assertThat(contagem("falha_compensada")).isZero();
        }

        @Test
        @DisplayName("compensação bem-sucedida e reconciliação necessária são desfechos DISTINTOS")
        void distingueFalhaDeReconciliacao() {
            stubAteOrganizacao();
            when(keycloak.criarUsuario(any())).thenThrow(new KeycloakIntegrationException("boom"));

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(RuntimeException.class);

            // Um cadastro que falhou e limpou tudo é rotina; um que deixou órfão exige gente.
            assertThat(contagem("falha_compensada")).isEqualTo(1);
            assertThat(contagem("reconciliacao_necessaria")).isZero();
        }

        @Test
        @DisplayName("reconciliação necessária tem seu próprio contador — é o que dispara alerta")
        void contaReconciliacao() {
            stubAteOrganizacao();
            when(keycloak.criarUsuario(any())).thenThrow(new KeycloakIntegrationException("boom"));
            doThrow(new KeycloakIntegrationException("keycloak fora"))
                    .when(keycloak).removerOrganization(anyString());

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(RuntimeException.class);

            assertThat(contagem("reconciliacao_necessaria")).isEqualTo(1);
        }

        @Test
        @DisplayName("nenhuma tag carrega e-mail — cardinalidade alta derruba o Prometheus, e e-mail em métrica é dado pessoal exposto")
        void tagsSemDadoPessoal() {
            stubProvisionamentoFeliz();
            service.cadastrar(entrada(), CHAVE, CORR);

            assertThat(meterRegistry.getMeters())
                    .flatExtracting(m -> m.getId().getTags())
                    .allSatisfy(tag -> assertThat(tag.getValue())
                            .doesNotContain("@")
                            .doesNotContain("senha"));
        }

        @Test
        @DisplayName("o MDC não vaza tenantId para fora da requisição")
        void mdcLimpo() {
            stubProvisionamentoFeliz();
            service.cadastrar(entrada(), CHAVE, CORR);

            assertThat(org.slf4j.MDC.get("tenantId")).isNull();
        }
    }

    @Nested
    @DisplayName("Corrida de slug")
    class CorridaDeSlug {

        @Test
        @DisplayName("perder a UNIQUE vira 409 — e nada foi criado no Keycloak, porque a assessoria é o 1o passo")
        void corridaNaConstraint() {
            when(provisioningRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            // Passou na pre-checagem e perdeu a corrida no INSERT: a janela que nenhuma verificacao
            // previa fecha. Quem serializa e a constraint.
            when(assessoriaRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("tb_assessoria_dominio_key"));

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(DuplicateResourceException.class);

            // A pre-checagem CONSULTA o Keycloak (busca por e-mail), entao "nenhuma interacao" seria
            // forte demais. O que importa e que nada foi CRIADO la.
            verify(keycloak, never()).criarOrganization(anyString(), anyString(), any());
            verify(keycloak, never()).criarUsuario(any());
        }
    }

    @Nested
    @DisplayName("Compensacao em cada ponto de falha")
    class CadaPontoDeFalha {

        @Test
        @DisplayName("falha ao criar a organizacao: apaga so a assessoria")
        void falhaNaOrganizacao() {
            when(provisioningRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(assessoriaRepository.save(any())).thenAnswer(i -> {
                Assessoria a = i.getArgument(0);
                a.setId(assessoriaId);
                return a;
            });
            when(keycloak.criarOrganization(anyString(), anyString(), any()))
                    .thenThrow(new KeycloakIntegrationException("boom"));

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(KeycloakIntegrationException.class);

            verify(assessoriaRepository).deleteById(assessoriaId);
            verify(keycloak, never()).removerOrganization(anyString());
            verify(keycloak, never()).removerUsuario(anyString());
        }

        @Test
        @DisplayName("falha ao atribuir a role: desfaz usuario, organizacao e assessoria")
        void falhaNaRole() {
            // Sem stub do usuarioRepository.save: o fluxo falha antes de chegar la.
            stubAteOrganizacao();
            when(keycloak.criarUsuario(any())).thenReturn(usuarioKeycloakId.toString());
            doThrow(new KeycloakIntegrationException("role inexistente"))
                    .when(keycloak).atribuirRoleDeRealm(anyString(), anyString());

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(KeycloakIntegrationException.class);

            verify(keycloak).removerUsuario(usuarioKeycloakId.toString());
            verify(keycloak).removerOrganization(ORG_ID);
            verify(assessoriaRepository).deleteById(assessoriaId);
            // O Usuario local nao chegou a ser criado — nao pode haver tentativa de apaga-lo.
            verify(usuarioRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("falha ao vincular a organizacao: mesma limpeza")
        void falhaNoVinculo() {
            // Sem stub do usuarioRepository.save: o fluxo falha antes de chegar la.
            stubAteOrganizacao();
            when(keycloak.criarUsuario(any())).thenReturn(usuarioKeycloakId.toString());
            doThrow(new KeycloakIntegrationException("org sumiu"))
                    .when(keycloak).adicionarMembroNaOrganization(anyString(), anyString());

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(KeycloakIntegrationException.class);

            verify(keycloak).removerUsuario(usuarioKeycloakId.toString());
            verify(assessoriaRepository).deleteById(assessoriaId);
        }

        @Test
        @DisplayName("falha ao persistir o Usuario local: desfaz tudo no Keycloak")
        void falhaNoUsuarioLocal() {
            stubAteOrganizacao();
            when(keycloak.criarUsuario(any())).thenReturn(usuarioKeycloakId.toString());
            when(usuarioRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("tb_usuario_email_key"));

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(DataIntegrityViolationException.class);

            verify(keycloak).removerUsuario(usuarioKeycloakId.toString());
            verify(keycloak).removerOrganization(ORG_ID);
            verify(assessoriaRepository).deleteById(assessoriaId);
        }
    }

    @Nested
    @DisplayName("Segredos em log")
    class SegredosEmLog {

        private ListAppender<ILoggingEvent> appender;
        private Logger logger;

        @org.junit.jupiter.api.BeforeEach
        void capturarLogs() {
            logger = (Logger) org.slf4j.LoggerFactory.getLogger(CoachSignupServiceImpl.class);
            appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.DEBUG);
        }

        @org.junit.jupiter.api.AfterEach
        void soltarLogs() {
            logger.detachAppender(appender);
        }

        private void assertSemSegredo() {
            assertThat(appender.list)
                    .isNotEmpty()
                    .allSatisfy(evento -> assertThat(evento.getFormattedMessage())
                            .doesNotContain("senha-forte-o-suficiente")
                            .doesNotContain("Bearer "));
        }

        @Test
        @DisplayName("caminho de sucesso nao loga a senha")
        void sucessoSemSenha() {
            stubProvisionamentoFeliz();
            service.cadastrar(entrada(), CHAVE, CORR);
            assertSemSegredo();
        }

        @Test
        @DisplayName("caminho de falha e compensacao nao loga a senha")
        void falhaSemSenha() {
            stubAteOrganizacao();
            when(keycloak.criarUsuario(any())).thenThrow(new KeycloakIntegrationException("boom"));

            assertThatThrownBy(() -> service.cadastrar(entrada(), CHAVE, CORR))
                    .isInstanceOf(RuntimeException.class);

            assertSemSegredo();
        }
    }

    @Nested
    @DisplayName("Cadastro por convite de fundadora")
    class CadastrarPorConvite {

        private static final String TOKEN = "tok-convite";
        private FoundingInvite convite;

        private CoachSignupInputDto entradaComConvite() {
            return new CoachSignupInputDto("Maria Treinadora", "maria@exemplo.com",
                    "senha-forte-o-suficiente", "Assessoria Corrida na Serra", "corridasserra", null, TOKEN);
        }

        private FoundingInvite conviteAtivo() {
            return FoundingInvite.builder()
                    .id(UUID.randomUUID())
                    .waitlistId(UUID.randomUUID())
                    .tokenHash("hash-do-token")
                    .email("maria@exemplo.com")
                    .expiresAt(OffsetDateTime.now().plusDays(5))
                    .sentAt(OffsetDateTime.now().minusDays(1))
                    .invitedBy("admin")
                    .build();
        }

        /** Espelha o hash do serviço: nome, e-mail, assessoria e slug — sem senha e sem token. */
        private static String hashDoPayload(CoachSignupInputDto input) {
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                        String.join("\n", input.nome(), input.email(), input.nomeAssessoria(), input.slug())
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return java.util.HexFormat.of().formatHex(digest);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        private void stubConviteAtivo() {
            convite = conviteAtivo();
            when(foundingInviteService.findActive(TOKEN)).thenReturn(Optional.of(convite));
        }

        @Test
        @DisplayName("token inválido → DomainNotFoundException, nada é criado")
        void tokenInvalido() {
            when(foundingInviteService.findActive(TOKEN)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cadastrar(entradaComConvite(), CHAVE, CORR))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(assessoriaRepository, keycloak, usuarioRepository, provisioningRepository);
        }

        @Test
        @DisplayName("e-mail do formulário diferente do convidado → 422, nada é criado")
        void emailDivergente() {
            convite = conviteAtivo();
            convite.setEmail("outra@exemplo.com");
            when(foundingInviteService.findActive(TOKEN)).thenReturn(Optional.of(convite));

            assertThatThrownBy(() -> service.cadastrar(entradaComConvite(), CHAVE, CORR))
                    .isInstanceOf(DomainRuleViolationException.class);

            verifyNoInteractions(assessoriaRepository, keycloak, usuarioRepository, provisioningRepository);
        }

        @Test
        @DisplayName("assessoria nasce GRATUITO 10/1, fundadora, com data de conversão")
        void assessoriaFundadora() {
            stubConviteAtivo();
            stubProvisionamentoFeliz();

            service.cadastrar(entradaComConvite(), CHAVE, CORR);

            var captor = ArgumentCaptor.forClass(Assessoria.class);
            verify(assessoriaRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
            Assessoria criada = captor.getAllValues().get(0);
            assertThat(criada.getPlano()).isEqualTo(PlanoAssessoria.GRATUITO);
            assertThat(criada.getMaxAtletas()).isEqualTo(10);
            assertThat(criada.getMaxTecnicos()).isEqualTo(1);
            assertThat(criada.getFounding()).isTrue();
            assertThat(criada.getFoundingConvertedAt()).isNotNull();
        }

        @Test
        @DisplayName("usuário Keycloak nasce com emailVerified e SEM VERIFY_EMAIL; nenhum e-mail de verificação sai")
        void usuarioSemVerificacao() {
            stubConviteAtivo();
            stubProvisionamentoFeliz();

            service.cadastrar(entradaComConvite(), CHAVE, CORR);

            var captor = ArgumentCaptor.forClass(NovoUsuarioKeycloak.class);
            verify(keycloak).criarUsuario(captor.capture());
            assertThat(captor.getValue().emailVerificado()).isTrue();
            assertThat(captor.getValue().acoesObrigatorias()).isEmpty();
            assertThat(captor.getValue().habilitado()).isTrue();
            verify(keycloak, never()).enviarVerificacaoDeEmail(anyString());
        }

        @Test
        @DisplayName("usuário local nasce com e-mail verificado e a resposta diz que pode entrar")
        void usuarioLocalVerificado() {
            stubConviteAtivo();
            stubProvisionamentoFeliz();

            var resposta = service.cadastrar(entradaComConvite(), CHAVE, CORR);

            var captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());
            assertThat(captor.getValue().getEmailVerificado()).isTrue();
            assertThat(resposta.proximoPasso()).isEqualTo(CoachSignupOutputDto.PRONTO_PARA_ENTRAR);
        }

        @Test
        @DisplayName("rastro leva origin FOUNDING_INVITE, o id do convite e a chave por tentativa — o header é ignorado")
        void rastroDoConvite() {
            stubConviteAtivo();
            stubProvisionamentoFeliz();

            service.cadastrar(entradaComConvite(), "chave-do-header", CORR);

            var rastro = ultimoRastro();
            assertThat(rastro.getOrigin()).isEqualTo(ProvisioningOrigin.FOUNDING_INVITE);
            assertThat(rastro.getInviteId()).isEqualTo(convite.getId());
            assertThat(rastro.getIdempotencyKey()).isEqualTo("hash-do-token:1");
            assertThat(rastro.getStatus()).isEqualTo(SignupProvisioningStatus.ACTIVE);
            verify(provisioningRepository, never()).findByIdempotencyKey("chave-do-header");
        }

        @Test
        @DisplayName("no sucesso o convite recebe convertedAt e o id da assessoria")
        void consomeOConvite() {
            stubConviteAtivo();
            stubProvisionamentoFeliz();

            service.cadastrar(entradaComConvite(), CHAVE, CORR);

            assertThat(convite.getConvertedAt()).isNotNull();
            assertThat(convite.getAssessoriaId()).isEqualTo(assessoriaId);
            verify(foundingInviteRepository).save(convite);
        }

        @Test
        @DisplayName("os limites anti-abuso por e-mail e teto diário NÃO se aplicam — o token é o portão")
        void semLimites() {
            stubConviteAtivo();
            stubProvisionamentoFeliz();

            service.cadastrar(entradaComConvite(), CHAVE, CORR);

            verify(provisioningRepository, never()).countByEmailAndCreatedAtAfter(anyString(), any());
            verify(provisioningRepository, never()).countByCreatedAtAfter(any());
        }

        @Test
        @DisplayName("falha no Keycloak → compensa e o convite continua sem convertedAt")
        void falhaNaoConsomeOConvite() {
            stubConviteAtivo();
            stubAteOrganizacao();
            when(keycloak.criarUsuario(any())).thenThrow(new KeycloakIntegrationException("boom"));

            assertThatThrownBy(() -> service.cadastrar(entradaComConvite(), CHAVE, CORR))
                    .isInstanceOf(KeycloakIntegrationException.class);

            assertThat(convite.getConvertedAt()).isNull();
            verify(foundingInviteRepository, never()).save(any());
            verify(assessoriaRepository).deleteById(assessoriaId);
            assertThat(ultimoRastro().getStatus()).isEqualTo(SignupProvisioningStatus.FAILED);
        }

        @Test
        @DisplayName("depois de um FAILED compensado, a nova tentativa usa a chave :2 e conclui — a :1 não é reusada")
        void retentativaAposFailed() {
            stubConviteAtivo();
            var falhou = SignupProvisioning.builder().idempotencyKey("hash-do-token:1")
                    .status(SignupProvisioningStatus.FAILED).inviteId(convite.getId()).build();
            when(provisioningRepository.findByInviteIdOrderByCreatedAtAsc(convite.getId())).thenReturn(List.of(falhou));
            stubProvisionamentoFeliz();

            service.cadastrar(entradaComConvite(), CHAVE, CORR);

            assertThat(ultimoRastro().getIdempotencyKey()).isEqualTo("hash-do-token:2");
            assertThat(ultimoRastro().getStatus()).isEqualTo(SignupProvisioningStatus.ACTIVE);
            verify(provisioningRepository, never()).findByIdempotencyKey("hash-do-token:1");
        }

        @Test
        @DisplayName("rastro RECONCILIATION_REQUIRED bloqueia com 409 antes de tocar qualquer coisa")
        void reconciliacaoBloqueia() {
            stubConviteAtivo();
            var residuo = SignupProvisioning.builder().idempotencyKey("hash-do-token:1")
                    .status(SignupProvisioningStatus.RECONCILIATION_REQUIRED).inviteId(convite.getId()).build();
            when(provisioningRepository.findByInviteIdOrderByCreatedAtAsc(convite.getId())).thenReturn(List.of(residuo));

            assertThatThrownBy(() -> service.cadastrar(entradaComConvite(), CHAVE, CORR))
                    .isInstanceOf(DomainConflictException.class);

            verifyNoInteractions(assessoriaRepository, keycloak, usuarioRepository);
            verify(provisioningRepository, never()).save(any());
        }

        @Test
        @DisplayName("tentativa em estado intermediário (duplo clique) → 409")
        void tentativaEmCurso() {
            stubConviteAtivo();
            var emCurso = SignupProvisioning.builder().idempotencyKey("hash-do-token:1")
                    .status(SignupProvisioningStatus.ORGANIZATION_CREATED).inviteId(convite.getId()).build();
            when(provisioningRepository.findByInviteIdOrderByCreatedAtAsc(convite.getId())).thenReturn(List.of(emCurso));

            assertThatThrownBy(() -> service.cadastrar(entradaComConvite(), CHAVE, CORR))
                    .isInstanceOf(DomainConflictException.class);

            verifyNoInteractions(assessoriaRepository, keycloak, usuarioRepository);
        }

        @Test
        @DisplayName("rastro ACTIVE já existente → devolve o resultado original sem provisionar de novo")
        void reenvioAposSucesso() {
            stubConviteAtivo();
            var ativo = SignupProvisioning.builder().idempotencyKey("hash-do-token:1")
                    .status(SignupProvisioningStatus.ACTIVE).inviteId(convite.getId())
                    .slug("corridasserra").email("maria@exemplo.com")
                    .requestHash(hashDoPayload(entradaComConvite())).build();
            when(provisioningRepository.findByInviteIdOrderByCreatedAtAsc(convite.getId())).thenReturn(List.of(ativo));
            when(provisioningRepository.findByIdempotencyKey("hash-do-token:1")).thenReturn(Optional.of(ativo));

            service.cadastrar(entradaComConvite(), CHAVE, CORR);

            var hashCaptor = ArgumentCaptor.forClass(SignupProvisioning.class);
            verify(provisioningRepository, never()).save(hashCaptor.capture());
            verifyNoInteractions(assessoriaRepository, keycloak, usuarioRepository);
        }

        @Test
        @DisplayName("o token do convite nunca aparece no rastro nem no log")
        void tokenNaoVaza() {
            stubConviteAtivo();
            stubProvisionamentoFeliz();
            var appender = new ListAppender<ILoggingEvent>();
            appender.start();
            var logger = (Logger) org.slf4j.LoggerFactory.getLogger(CoachSignupServiceImpl.class);
            logger.addAppender(appender);
            try {
                service.cadastrar(entradaComConvite(), CHAVE, CORR);
            } finally {
                logger.detachAppender(appender);
            }

            var rastro = ultimoRastro();
            assertThat(rastro.getIdempotencyKey()).doesNotContain(TOKEN);
            assertThat(rastro.getRequestHash()).doesNotContain(TOKEN);
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
            assertThat(logs).isNotEmpty().doesNotContain(TOKEN).doesNotContain("senha-forte-o-suficiente");
        }
    }
}
