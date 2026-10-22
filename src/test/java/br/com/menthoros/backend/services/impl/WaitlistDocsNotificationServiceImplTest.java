package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.WaitlistDocsNotificationResultDto;
import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.exception.EmailDeliveryException;
import br.com.menthoros.backend.repository.WaitlistRepository;
import br.com.menthoros.backend.services.email.EmailMessage;
import br.com.menthoros.backend.services.email.EmailSender;
import br.com.menthoros.backend.services.email.EmailTemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WaitlistDocsNotificationServiceImpl: aviso em massa da central de ajuda")
class WaitlistDocsNotificationServiceImplTest {

    private static final Instant AGORA = Instant.parse("2026-10-05T12:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);
    private static final String DOCS_URL = "https://docs.menthoros.com";
    private static final String FRONTEND = "https://app.menthoros.com/";

    @Mock private WaitlistRepository waitlistRepository;
    @Mock private EmailSender emailSender;

    private WaitlistDocsNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WaitlistDocsNotificationServiceImpl(
                waitlistRepository, emailSender, new EmailTemplateRenderer(), RELOGIO, DOCS_URL, FRONTEND);
    }

    private Waitlist inscrito(String nome, String email) {
        return Waitlist.builder().id(UUID.randomUUID()).nome(nome).email(email).perfil(PerfilWaitlist.TREINADOR).build();
    }

    @Test
    @DisplayName("CA1 — inscrito elegível reivindicado com sucesso recebe o e-mail com o link da central de ajuda")
    void enviaParaElegivel() {
        var coach = inscrito("Ana", "ana@exemplo.com");
        when(waitlistRepository.findAllByPerfilInAndDocsNotifiedAtIsNull(List.of(PerfilWaitlist.TREINADOR, PerfilWaitlist.PROPRIETARIO)))
                .thenReturn(List.of(coach));
        when(waitlistRepository.reivindicarAvisoDocs(eq(coach.getId()), any())).thenReturn(1);

        var resultado = service.notificar();

        assertThat(resultado).isEqualTo(new WaitlistDocsNotificationResultDto(1, 1, 0));
        var captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        assertThat(captor.getValue().to()).isEqualTo("ana@exemplo.com");
        assertThat(captor.getValue().html()).contains(DOCS_URL).contains("Ana");
        assertThat(captor.getValue().text()).contains(DOCS_URL);
        verify(waitlistRepository, never()).liberarAvisoDocs(any());
    }

    @Test
    @DisplayName("CA2 — sem elegíveis, nenhum e-mail é (re)enviado — idempotência")
    void semElegiveisNaoReenvia() {
        when(waitlistRepository.findAllByPerfilInAndDocsNotifiedAtIsNull(List.of(PerfilWaitlist.TREINADOR, PerfilWaitlist.PROPRIETARIO)))
                .thenReturn(List.of());

        var resultado = service.notificar();

        assertThat(resultado).isEqualTo(new WaitlistDocsNotificationResultDto(0, 0, 0));
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("CA3 — só busca TREINADOR/PROPRIETARIO (ATLETA nunca entra na query)")
    void soConsultaTreinadorOuProprietario() {
        when(waitlistRepository.findAllByPerfilInAndDocsNotifiedAtIsNull(List.of(PerfilWaitlist.TREINADOR, PerfilWaitlist.PROPRIETARIO)))
                .thenReturn(List.of());

        service.notificar();

        verify(waitlistRepository).findAllByPerfilInAndDocsNotifiedAtIsNull(List.of(PerfilWaitlist.TREINADOR, PerfilWaitlist.PROPRIETARIO));
        verify(waitlistRepository, never()).findAllByPerfilInAndDocsNotifiedAtIsNull(List.of(PerfilWaitlist.ATLETA));
    }

    @Test
    @DisplayName("CA4 — falha do EmailSender num inscrito libera a reivindicação, conta como falha e não impede os demais")
    void falhaParcialNaoInterrompeOLote() {
        var falha = inscrito("Bruno", "bruno@exemplo.com");
        var sucesso = inscrito("Carla", "carla@exemplo.com");
        when(waitlistRepository.findAllByPerfilInAndDocsNotifiedAtIsNull(List.of(PerfilWaitlist.TREINADOR, PerfilWaitlist.PROPRIETARIO)))
                .thenReturn(List.of(falha, sucesso));
        when(waitlistRepository.reivindicarAvisoDocs(any(), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new EmailDeliveryException("SMTP recusou", new RuntimeException("535")))
                .when(emailSender)
                .send(org.mockito.ArgumentMatchers.argThat(m -> "bruno@exemplo.com".equals(m.to())));

        var resultado = service.notificar();

        assertThat(resultado).isEqualTo(new WaitlistDocsNotificationResultDto(2, 1, 1));
        verify(waitlistRepository).liberarAvisoDocs(falha.getId());
        verify(waitlistRepository, never()).liberarAvisoDocs(sucesso.getId());
    }

    @Test
    @DisplayName("CA5 — nenhum inscrito pendente: contagem zerada, sem efeitos colaterais")
    void nenhumPendente() {
        when(waitlistRepository.findAllByPerfilInAndDocsNotifiedAtIsNull(List.of(PerfilWaitlist.TREINADOR, PerfilWaitlist.PROPRIETARIO)))
                .thenReturn(List.of());

        var resultado = service.notificar();

        assertThat(resultado.elegiveis()).isZero();
        assertThat(resultado.enviados()).isZero();
        assertThat(resultado.falhas()).isZero();
        verify(waitlistRepository, never()).reivindicarAvisoDocs(any(), any());
    }

    @Test
    @DisplayName("CA8 — reivindicação perdida (corrida com outra chamada) é pulada, sem enviar e-mail")
    void reivindicacaoPerdidaEPulada() {
        var disputado = inscrito("Duda", "duda@exemplo.com");
        when(waitlistRepository.findAllByPerfilInAndDocsNotifiedAtIsNull(List.of(PerfilWaitlist.TREINADOR, PerfilWaitlist.PROPRIETARIO)))
                .thenReturn(List.of(disputado));
        when(waitlistRepository.reivindicarAvisoDocs(eq(disputado.getId()), any())).thenReturn(0);

        var resultado = service.notificar();

        assertThat(resultado).isEqualTo(new WaitlistDocsNotificationResultDto(1, 0, 0));
        verify(emailSender, never()).send(any());
        verify(waitlistRepository, never()).liberarAvisoDocs(any());
    }
}
