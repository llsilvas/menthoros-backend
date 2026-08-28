package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.FoundingInviteLookupOutputDto;
import br.com.menthoros.backend.dto.output.FoundingInviteOutputDto;
import br.com.menthoros.backend.entity.FoundingInvite;
import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.EmailDeliveryException;
import br.com.menthoros.backend.repository.FoundingInviteRepository;
import br.com.menthoros.backend.repository.WaitlistRepository;
import br.com.menthoros.backend.security.InviteToken;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import br.com.menthoros.backend.services.email.EmailMessage;
import br.com.menthoros.backend.services.email.EmailSender;
import br.com.menthoros.backend.services.email.EmailTemplateRenderer;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FoundingInviteServiceImpl: emissão, reenvio e consulta do convite")
class FoundingInviteServiceImplTest {

    private static final Instant AGORA = Instant.parse("2026-08-28T12:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);
    private static final String FRONTEND = "https://app.menthoros.com/";
    private static final int VALIDADE_DIAS = 7;
    private static final String ADMIN = "admin-sub";
    private static final Pattern LINK = Pattern.compile("https://app\\.menthoros\\.com/#/cadastro\\?convite=([A-Za-z0-9_-]{43})");

    @Mock private WaitlistRepository waitlistRepository;
    @Mock private FoundingInviteRepository inviteRepository;
    @Mock private KeycloakOrganizationGateway keycloak;
    @Mock private EmailSender emailSender;

    private FoundingInviteServiceImpl service;
    private UUID waitlistId;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        waitlistId = UUID.randomUUID();
        service = new FoundingInviteServiceImpl(waitlistRepository, inviteRepository, keycloak, emailSender,
                new EmailTemplateRenderer(), RELOGIO, FRONTEND, VALIDADE_DIAS);

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(FoundingInviteServiceImpl.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(FoundingInviteServiceImpl.class)).detachAppender(logs);
    }

    @Nested
    @DisplayName("invite")
    class Invite {

        @Test
        @DisplayName("grava o hash (nunca o token), envia o e-mail com o link no fragmento e marca sentAt")
        void caminhoFeliz() {
            stubInscrito(treinadora());
            when(inviteRepository.save(any())).thenAnswer(i -> comId(i.getArgument(0)));

            FoundingInviteOutputDto saida = service.invite(waitlistId, ADMIN);

            String token = tokenDoEmail();
            FoundingInvite salvo = ultimoConviteSalvo();
            assertThat(salvo.getTokenHash()).isEqualTo(InviteToken.hashOf(token)).isNotEqualTo(token);
            assertThat(salvo.getEmail()).isEqualTo("maria@exemplo.com");
            assertThat(salvo.getInvitedBy()).isEqualTo(ADMIN);
            assertThat(salvo.getExpiresAt()).isEqualTo(OffsetDateTime.now(RELOGIO).plusDays(VALIDADE_DIAS));
            assertThat(salvo.getSentAt()).isEqualTo(OffsetDateTime.now(RELOGIO));
            assertThat(saida.waitlistId()).isEqualTo(waitlistId);
            assertThat(saida.expiresAt()).isEqualTo(salvo.getExpiresAt());
        }

        @Test
        @DisplayName("o e-mail vai para o inscrito, com assunto e as duas versões de corpo")
        void conteudoDoEmail() {
            stubInscrito(treinadora());
            when(inviteRepository.save(any())).thenAnswer(i -> comId(i.getArgument(0)));

            service.invite(waitlistId, ADMIN);

            EmailMessage email = emailEnviado();
            assertThat(email.to()).isEqualTo("maria@exemplo.com");
            assertThat(email.subject()).isEqualTo(FoundingInviteServiceImpl.SUBJECT);
            assertThat(email.html()).contains("Maria").contains("7 dias").doesNotContain("{{");
            assertThat(email.text()).contains("Maria").contains("7 dias").doesNotContain("{{");
        }

        @Test
        @DisplayName("o token nunca aparece no retorno nem no log")
        void tokenNaoVaza() {
            stubInscrito(treinadora());
            when(inviteRepository.save(any())).thenAnswer(i -> comId(i.getArgument(0)));

            FoundingInviteOutputDto saida = service.invite(waitlistId, ADMIN);

            String token = tokenDoEmail();
            assertThat(saida.toString()).doesNotContain(token);
            String tudo = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
            assertThat(tudo).isNotEmpty().doesNotContain(token);
        }

        @Test
        @DisplayName("reenvio invalida o convite aberto anterior ANTES de inserir o novo")
        void reenvioInvalidaAnterior() {
            stubInscrito(treinadora());
            FoundingInvite anterior = conviteAberto(OffsetDateTime.now(RELOGIO).plusDays(3));
            when(inviteRepository.findOpenByWaitlistId(waitlistId)).thenReturn(Optional.of(anterior));
            when(inviteRepository.save(any())).thenAnswer(i -> comId(i.getArgument(0)));

            service.invite(waitlistId, ADMIN);

            assertThat(anterior.getInvalidatedAt()).isEqualTo(OffsetDateTime.now(RELOGIO));
            InOrder ordem = inOrder(inviteRepository);
            ordem.verify(inviteRepository).save(anterior);
            ordem.verify(inviteRepository, org.mockito.Mockito.atLeastOnce()).save(any(FoundingInvite.class));
        }

        @Test
        @DisplayName("convite anterior EXPIRADO também é invalidado — o índice parcial não olha expires_at")
        void reenvioInvalidaExpirado() {
            stubInscrito(treinadora());
            FoundingInvite expirado = conviteAberto(OffsetDateTime.now(RELOGIO).minusDays(1));
            when(inviteRepository.findOpenByWaitlistId(waitlistId)).thenReturn(Optional.of(expirado));
            when(inviteRepository.save(any())).thenAnswer(i -> comId(i.getArgument(0)));

            service.invite(waitlistId, ADMIN);

            assertThat(expirado.getInvalidatedAt()).isNotNull();
        }

        @Test
        @DisplayName("convite anterior sem sentAt (SMTP falhou) também é invalidado")
        void reenvioInvalidaNaoEnviado() {
            stubInscrito(treinadora());
            FoundingInvite naoEnviado = conviteAberto(OffsetDateTime.now(RELOGIO).plusDays(6));
            naoEnviado.setSentAt(null);
            when(inviteRepository.findOpenByWaitlistId(waitlistId)).thenReturn(Optional.of(naoEnviado));
            when(inviteRepository.save(any())).thenAnswer(i -> comId(i.getArgument(0)));

            service.invite(waitlistId, ADMIN);

            assertThat(naoEnviado.getInvalidatedAt()).isNotNull();
        }

        @Test
        @DisplayName("inscrito inexistente → DomainNotFoundException, sem tocar Keycloak nem e-mail")
        void inscritoInexistente() {
            when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.invite(waitlistId, ADMIN))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(keycloak, emailSender, inviteRepository);
        }

        @Test
        @DisplayName("perfil ATLETA → DomainRuleViolationException (422), nada gravado nem enviado")
        void perfilAtleta() {
            when(waitlistRepository.findById(waitlistId))
                    .thenReturn(Optional.of(treinadora().toBuilder().perfil(PerfilWaitlist.ATLETA).build()));

            assertThatThrownBy(() -> service.invite(waitlistId, ADMIN))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("TREINADOR");

            verifyNoInteractions(keycloak, emailSender, inviteRepository);
        }

        @Test
        @DisplayName("e-mail com 101 caracteres → 422; com 100 passa")
        void limiteDoEmail() {
            String local = "a".repeat(101 - "@x.io".length());
            when(waitlistRepository.findById(waitlistId))
                    .thenReturn(Optional.of(treinadora().toBuilder().email(local + "@x.io").build()));

            assertThatThrownBy(() -> service.invite(waitlistId, ADMIN))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("100");
            verifyNoInteractions(keycloak, emailSender);

            String local100 = "a".repeat(100 - "@x.io".length());
            when(waitlistRepository.findById(waitlistId))
                    .thenReturn(Optional.of(treinadora().toBuilder().email(local100 + "@x.io").build()));
            when(inviteRepository.save(any())).thenAnswer(i -> comId(i.getArgument(0)));

            service.invite(waitlistId, ADMIN);

            verify(emailSender).send(any());
        }

        @Test
        @DisplayName("e-mail já tem conta no Keycloak → DomainConflictException (409), nada gravado nem enviado")
        void emailComConta() {
            when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(treinadora()));
            when(keycloak.buscarUsuarioIdPorEmail("maria@exemplo.com")).thenReturn(Optional.of("kc-1"));

            assertThatThrownBy(() -> service.invite(waitlistId, ADMIN))
                    .isInstanceOf(DomainConflictException.class);

            verify(inviteRepository, never()).save(any());
            verifyNoInteractions(emailSender);
        }

        @Test
        @DisplayName("inscrito já convertido → DomainConflictException (409)")
        void jaConvertido() {
            when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(treinadora()));
            when(inviteRepository.existsByWaitlistIdAndConvertedAtIsNotNull(waitlistId)).thenReturn(true);

            assertThatThrownBy(() -> service.invite(waitlistId, ADMIN))
                    .isInstanceOf(DomainConflictException.class);

            verify(inviteRepository, never()).save(any());
            verifyNoInteractions(emailSender);
        }

        @Test
        @DisplayName("dois convites simultâneos: a UNIQUE parcial decide e quem perdeu recebe 409, sem e-mail")
        void corridaNoInsert() {
            stubInscrito(treinadora());
            when(inviteRepository.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_founding_invite_open"));

            assertThatThrownBy(() -> service.invite(waitlistId, ADMIN))
                    .isInstanceOf(DomainConflictException.class);

            verifyNoInteractions(emailSender);
        }

        @Test
        @DisplayName("SMTP recusa → EmailDeliveryException sobe, e o convite fica gravado SEM sentAt")
        void smtpFalha() {
            stubInscrito(treinadora());
            when(inviteRepository.save(any())).thenAnswer(i -> comId(i.getArgument(0)));
            doThrow(new EmailDeliveryException("recusado", null)).when(emailSender).send(any());

            assertThatThrownBy(() -> service.invite(waitlistId, ADMIN))
                    .isInstanceOf(EmailDeliveryException.class);

            verify(inviteRepository, times(1)).save(any());
            assertThat(ultimoConviteSalvo().getSentAt()).isNull();
        }
    }

    @Nested
    @DisplayName("lookup")
    class Lookup {

        @Test
        @DisplayName("token ativo devolve nome da waitlist e e-mail do convite")
        void ativo() {
            InviteToken token = InviteToken.generate();
            FoundingInvite convite = conviteAberto(OffsetDateTime.now(RELOGIO).plusDays(2));
            convite.setTokenHash(token.hash());
            when(inviteRepository.findByTokenHash(token.hash())).thenReturn(Optional.of(convite));
            when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(treinadora()));

            FoundingInviteLookupOutputDto saida = service.lookup(token.value());

            assertThat(saida).isEqualTo(new FoundingInviteLookupOutputDto("Maria Treinadora", "maria@exemplo.com"));
        }

        @Test
        @DisplayName("token desconhecido → DomainNotFoundException")
        void desconhecido() {
            when(inviteRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.lookup("nao-existe"))
                    .isInstanceOf(DomainNotFoundException.class);
        }

        @Test
        @DisplayName("expirado, invalidado e convertido respondem igual ao desconhecido")
        void estadosInvalidos() {
            InviteToken token = InviteToken.generate();
            FoundingInvite expirado = conviteAberto(OffsetDateTime.now(RELOGIO).minusMinutes(1));
            FoundingInvite invalidado = conviteAberto(OffsetDateTime.now(RELOGIO).plusDays(2));
            invalidado.setInvalidatedAt(OffsetDateTime.now(RELOGIO));
            FoundingInvite convertido = conviteAberto(OffsetDateTime.now(RELOGIO).plusDays(2));
            convertido.setConvertedAt(OffsetDateTime.now(RELOGIO));

            for (FoundingInvite convite : new FoundingInvite[] {expirado, invalidado, convertido}) {
                when(inviteRepository.findByTokenHash(token.hash())).thenReturn(Optional.of(convite));

                assertThatThrownBy(() -> service.lookup(token.value()))
                        .isInstanceOf(DomainNotFoundException.class)
                        .hasMessage("Convite inválido ou expirado");
            }
            verifyNoInteractions(waitlistRepository);
        }

        @Test
        @DisplayName("token vazio ou em branco não consulta o banco")
        void vazio() {
            assertThat(service.findActive(null)).isEmpty();
            assertThat(service.findActive("  ")).isEmpty();

            verifyNoInteractions(inviteRepository);
        }
    }

    // ----- helpers -----

    private Waitlist treinadora() {
        return Waitlist.builder()
                .id(waitlistId)
                .nome("Maria Treinadora")
                .email("maria@exemplo.com")
                .emailNormalized("maria@exemplo.com")
                .perfil(PerfilWaitlist.TREINADOR)
                .aceiteLgpd(true)
                .build();
    }

    private void stubInscrito(Waitlist inscrito) {
        when(waitlistRepository.findById(waitlistId)).thenReturn(Optional.of(inscrito));
    }

    private FoundingInvite conviteAberto(OffsetDateTime expiresAt) {
        return FoundingInvite.builder()
                .id(UUID.randomUUID())
                .waitlistId(waitlistId)
                .tokenHash("hash-anterior")
                .email("maria@exemplo.com")
                .expiresAt(expiresAt)
                .sentAt(OffsetDateTime.now(RELOGIO).minusDays(1))
                .invitedBy(ADMIN)
                .build();
    }

    private static FoundingInvite comId(FoundingInvite convite) {
        if (convite.getId() == null) {
            convite.setId(UUID.randomUUID());
        }
        return convite;
    }

    private EmailMessage emailEnviado() {
        var captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        return captor.getValue();
    }

    private String tokenDoEmail() {
        Matcher m = LINK.matcher(emailEnviado().text());
        assertThat(m.find()).as("link do convite no e-mail").isTrue();
        return m.group(1);
    }

    private FoundingInvite ultimoConviteSalvo() {
        var captor = ArgumentCaptor.forClass(FoundingInvite.class);
        verify(inviteRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(c -> !"hash-anterior".equals(c.getTokenHash()))
                .reduce((a, b) -> b)
                .orElseThrow();
    }
}
