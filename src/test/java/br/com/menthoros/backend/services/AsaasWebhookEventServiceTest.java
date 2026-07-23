package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.asaas.AsaasWebhookEventDto;
import br.com.menthoros.backend.dto.asaas.AsaasWebhookEventDto.Payment;
import br.com.menthoros.backend.dto.asaas.AsaasWebhookEventDto.Subscription;
import br.com.menthoros.backend.entity.AsaasWebhookEventoProcessado;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Assinatura;
import br.com.menthoros.backend.enums.StatusAssinatura;
import br.com.menthoros.backend.repository.AsaasWebhookEventoProcessadoRepository;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AssinaturaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsaasWebhookEventServiceTest {

    @Mock private AssinaturaRepository assinaturaRepository;
    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private AsaasWebhookEventoProcessadoRepository eventoProcessadoRepository;

    private AsaasWebhookEventService service;

    private UUID assessoriaId;

    @BeforeEach
    void setUp() {
        service = new AsaasWebhookEventService(assinaturaRepository, assessoriaRepository, eventoProcessadoRepository);
        assessoriaId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("PAYMENT_OVERDUE")
    class PaymentOverdue {

        @Test
        @DisplayName("ATIVA -> INADIMPLENTE, marca overdueDesde, sem tocar a assessoria (CA3)")
        void ativaVaiParaInadimplente() {
            Assinatura assinatura = assinatura(StatusAssinatura.ATIVA, null);
            when(eventoProcessadoRepository.existsById("evt1")).thenReturn(false);
            when(assinaturaRepository.findByAsaasSubscriptionId("sub1")).thenReturn(Optional.of(assinatura));

            service.processar(paymentEvent("evt1", "PAYMENT_OVERDUE", "sub1"));

            assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.INADIMPLENTE);
            assertThat(assinatura.getOverdueDesde()).isNotNull();
            verify(assinaturaRepository).save(assinatura);
            verifyNoInteractions(assessoriaRepository);
            verify(eventoProcessadoRepository).save(any(AsaasWebhookEventoProcessado.class));
        }
    }

    @Nested
    @DisplayName("PAYMENT_CONFIRMED / PAYMENT_RECEIVED")
    class PaymentConfirmed {

        @Test
        @DisplayName("INADIMPLENTE dentro da carência -> ATIVA, limpa overdueDesde, assessoria intacta (CA4)")
        void inadimplenteVoltaParaAtiva() {
            Assinatura assinatura = assinatura(StatusAssinatura.INADIMPLENTE, Instant.now().minus(2, ChronoUnit.DAYS));
            when(eventoProcessadoRepository.existsById("evt2")).thenReturn(false);
            when(assinaturaRepository.findByAsaasSubscriptionId("sub1")).thenReturn(Optional.of(assinatura));

            service.processar(paymentEvent("evt2", "PAYMENT_CONFIRMED", "sub1"));

            assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
            assertThat(assinatura.getOverdueDesde()).isNull();
            verifyNoInteractions(assessoriaRepository);
        }

        @Test
        @DisplayName("SUSPENSA -> ATIVA e reativa a assessoria (CA6)")
        void suspensaVoltaParaAtivaEReativa() {
            Assinatura assinatura = assinatura(StatusAssinatura.SUSPENSA, Instant.now().minus(10, ChronoUnit.DAYS));
            Assessoria assessoria = assessoria(false);
            when(eventoProcessadoRepository.existsById("evt3")).thenReturn(false);
            when(assinaturaRepository.findByAsaasSubscriptionId("sub1")).thenReturn(Optional.of(assinatura));
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));

            service.processar(paymentEvent("evt3", "PAYMENT_RECEIVED", "sub1"));

            assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
            assertThat(assessoria.getAtivo()).isTrue();
            verify(assessoriaRepository).save(assessoria);
        }
    }

    @Nested
    @DisplayName("SUBSCRIPTION_DELETED / SUBSCRIPTION_INACTIVATED")
    class SubscriptionCancelada {

        @Test
        @DisplayName("qualquer estado -> CANCELADA e desativa a assessoria (CA8)")
        void reconciliaCancelamento() {
            Assinatura assinatura = assinatura(StatusAssinatura.ATIVA, null);
            Assessoria assessoria = assessoria(true);
            when(eventoProcessadoRepository.existsById("evt4")).thenReturn(false);
            when(assinaturaRepository.findByAsaasSubscriptionId("sub1")).thenReturn(Optional.of(assinatura));
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));

            service.processar(subscriptionEvent("evt4", "SUBSCRIPTION_DELETED", "sub1"));

            assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.CANCELADA);
            assertThat(assessoria.getAtivo()).isFalse();
            verify(assessoriaRepository).save(assessoria);
        }
    }

    @Nested
    @DisplayName("idempotência")
    class Idempotencia {

        @Test
        @DisplayName("evento já processado (mesmo id) não reaplica transição (CA10)")
        void eventoRepetidoNaoReaplica() {
            when(eventoProcessadoRepository.existsById("evt1")).thenReturn(true);

            service.processar(paymentEvent("evt1", "PAYMENT_OVERDUE", "sub1"));

            verifyNoInteractions(assinaturaRepository);
            verifyNoInteractions(assessoriaRepository);
            verify(eventoProcessadoRepository, never()).save(any());
        }
    }

    private AsaasWebhookEventDto paymentEvent(String id, String tipo, String subscriptionId) {
        return new AsaasWebhookEventDto(id, tipo, new Payment("pay1", "cus1", subscriptionId), null);
    }

    private AsaasWebhookEventDto subscriptionEvent(String id, String tipo, String subscriptionId) {
        return new AsaasWebhookEventDto(id, tipo, null, new Subscription(subscriptionId));
    }

    private Assinatura assinatura(StatusAssinatura status, Instant overdueDesde) {
        Assinatura a = new Assinatura();
        a.setId(UUID.randomUUID());
        a.setAssessoriaId(assessoriaId);
        a.setStatus(status);
        a.setOverdueDesde(overdueDesde);
        a.setAsaasSubscriptionId("sub1");
        return a;
    }

    private Assessoria assessoria(boolean ativo) {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(assessoriaId);
        assessoria.setAtivo(ativo);
        return assessoria;
    }
}
