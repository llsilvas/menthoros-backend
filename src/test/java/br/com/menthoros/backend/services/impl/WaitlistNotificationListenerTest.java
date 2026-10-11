package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.FaixaAtletas;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.events.WaitlistLeadCreatedEvent;
import br.com.menthoros.backend.exception.EmailDeliveryException;
import br.com.menthoros.backend.repository.WaitlistRepository;
import br.com.menthoros.backend.services.email.EmailMessage;
import br.com.menthoros.backend.services.email.EmailTemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitlistNotificationListenerTest {

    private static final String FOUNDER_EMAIL = "founder@menthoros.com";

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private WaitlistEmailSender waitlistEmailSender;

    @Mock
    private EmailTemplateRenderer templates;

    private WaitlistNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new WaitlistNotificationListener(
                waitlistRepository, waitlistEmailSender, templates,
                "https://app.menthoros.com/", FOUNDER_EMAIL);
    }

    private void stubTemplates() {
        when(templates.render(anyString(), anyMap())).thenReturn("corpo");
    }

    private Waitlist lead(PerfilWaitlist perfil) {
        return Waitlist.builder()
                .id(UUID.randomUUID())
                .nome("Maria")
                .email("maria@exemplo.com")
                .emailNormalized("maria@exemplo.com")
                .telefone("+55 11 99999-9999")
                .perfil(perfil)
                .qtdAtletas(perfil == PerfilWaitlist.TREINADOR ? FaixaAtletas.DE_11_A_30 : null)
                .aceiteLgpd(true)
                .utmSource("instagram")
                .build();
    }

    @Nested
    @DisplayName("aoCriarLead")
    class AoCriarLead {

        @Test
        @DisplayName("treinador recebe confirmação de treinador e o founder é notificado")
        void treinadorNotificaFounder() {
            stubTemplates();
            Waitlist lead = lead(PerfilWaitlist.TREINADOR);
            when(waitlistRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

            listener.aoCriarLead(new WaitlistLeadCreatedEvent(lead.getId()));

            verify(templates).render(eq("waitlist-confirmation-treinador.html"), anyMap());
            verify(templates).render(eq("waitlist-confirmation-treinador.txt"), anyMap());
            verify(templates).render(eq("waitlist-founder-notification.html"), anyMap());
            verify(templates).render(eq("waitlist-founder-notification.txt"), anyMap());
            ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
            verify(waitlistEmailSender, times(2)).enviar(captor.capture());
            assertThat(captor.getAllValues()).extracting(EmailMessage::to)
                    .containsExactly("maria@exemplo.com", FOUNDER_EMAIL);
        }

        @Test
        @DisplayName("proprietário recebe confirmação de treinador e o founder é notificado (expand-waitlist-access-contract)")
        void proprietarioTratadoComoTreinador() {
            stubTemplates();
            Waitlist lead = lead(PerfilWaitlist.PROPRIETARIO);
            when(waitlistRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

            listener.aoCriarLead(new WaitlistLeadCreatedEvent(lead.getId()));

            verify(templates).render(eq("waitlist-confirmation-treinador.html"), anyMap());
            verify(templates).render(eq("waitlist-founder-notification.html"), anyMap());
            verify(waitlistEmailSender, times(2)).enviar(any());
        }

        @Test
        @DisplayName("atleta recebe confirmação de atleta e o founder NÃO é notificado")
        void atletaNaoNotificaFounder() {
            stubTemplates();
            Waitlist lead = lead(PerfilWaitlist.ATLETA);
            when(waitlistRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

            listener.aoCriarLead(new WaitlistLeadCreatedEvent(lead.getId()));

            verify(templates).render(eq("waitlist-confirmation-atleta.html"), anyMap());
            verify(templates).render(eq("waitlist-confirmation-atleta.txt"), anyMap());
            verify(templates, never()).render(eq("waitlist-founder-notification.html"), anyMap());
            verify(waitlistEmailSender, times(1)).enviar(any());
        }

        @Test
        @DisplayName("lead inexistente não tenta enviar nada")
        void leadInexistenteNaoEnvia() {
            UUID id = UUID.randomUUID();
            when(waitlistRepository.findById(id)).thenReturn(Optional.empty());

            listener.aoCriarLead(new WaitlistLeadCreatedEvent(id));

            verify(waitlistEmailSender, never()).enviar(any());
        }

        @Test
        @DisplayName("sem founder-notification-email configurado, não tenta notificar o founder")
        void semEmailDoFounderConfigurado() {
            stubTemplates();
            WaitlistNotificationListener semFounder = new WaitlistNotificationListener(
                    waitlistRepository, waitlistEmailSender, templates, "https://app.menthoros.com", "");
            Waitlist lead = lead(PerfilWaitlist.TREINADOR);
            when(waitlistRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

            semFounder.aoCriarLead(new WaitlistLeadCreatedEvent(lead.getId()));

            verify(waitlistEmailSender, times(1)).enviar(any());
        }

        @Test
        @DisplayName("falha ao enviar a confirmação não impede a notificação ao founder")
        void falhaNaConfirmacaoNaoBloqueiaFounder() {
            stubTemplates();
            Waitlist lead = lead(PerfilWaitlist.TREINADOR);
            when(waitlistRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
            doThrow(new EmailDeliveryException("falha", null)).when(waitlistEmailSender).enviar(any());

            listener.aoCriarLead(new WaitlistLeadCreatedEvent(lead.getId()));

            verify(waitlistEmailSender, times(2)).enviar(any());
        }
    }
}
