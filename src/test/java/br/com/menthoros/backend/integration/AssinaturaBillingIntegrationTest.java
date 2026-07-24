package br.com.menthoros.backend.integration;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.asaas.AsaasWebhookEventDto;
import br.com.menthoros.backend.dto.asaas.AsaasWebhookEventDto.Payment;
import br.com.menthoros.backend.dto.input.AssinaturaInputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Assinatura;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.StatusAssinatura;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AssinaturaRepository;
import br.com.menthoros.backend.scheduler.AssinaturaSuspensaoScheduler;
import br.com.menthoros.backend.services.AsaasWebhookEventService;
import br.com.menthoros.backend.services.AssinaturaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ciclo completo de cobrança ponta a ponta (assessoria-billing-asaas, tasks.md 7.1) com o
 * {@code AsaasGatewayMock} ativo (asaas.mock=true por default — sem ligação real com o provider):
 * criar (CA1) → PAYMENT_OVERDUE (CA3) → job de carência (CA5) → PAYMENT_CONFIRMED (CA6).
 */
@Transactional
class AssinaturaBillingIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AssinaturaService assinaturaService;
    @Autowired private AsaasWebhookEventService webhookService;
    @Autowired private AssinaturaSuspensaoScheduler suspensaoScheduler;
    @Autowired private AssinaturaRepository assinaturaRepository;
    @Autowired private AssessoriaRepository assessoriaRepository;

    @PersistenceContext private EntityManager entityManager;

    @Test
    @DisplayName("criar → overdue → job → suspensa → pagamento → ativa (CA1/CA3/CA5/CA6)")
    void cicloCompleto() {
        Assessoria assessoria = seedAssessoria();
        UUID assessoriaId = assessoria.getId();

        // 1. Criar assinatura — mock confirma ATIVA (CA1)
        assinaturaService.criar(assessoriaId, new AssinaturaInputDto(
                "tok_cartao", LocalDate.now().plusDays(30), new BigDecimal("199.90"), PlanoAssessoria.PRO));
        flushClear();

        Assinatura assinatura = recarregar(assessoriaId);
        String subscriptionId = assinatura.getAsaasSubscriptionId();
        assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(subscriptionId).isEqualTo("sub_mock_" + assessoriaId);
        assertThat(recarregarAssessoria(assessoriaId).getPlano()).isEqualTo(PlanoAssessoria.PRO);

        // 2. Webhook PAYMENT_OVERDUE → INADIMPLENTE, assessoria ainda ativa (CA3)
        webhookService.processar(paymentEvent("evt-overdue", "PAYMENT_OVERDUE", subscriptionId));
        flushClear();
        assinatura = recarregar(assessoriaId);
        assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.INADIMPLENTE);
        assertThat(assinatura.getOverdueDesde()).isNotNull();
        assertThat(recarregarAssessoria(assessoriaId).getAtivo()).isTrue();

        // 3. Passados >5 dias (back-date do overdueDesde) → job suspende (CA5)
        assinatura.setOverdueDesde(Instant.now().minus(6, ChronoUnit.DAYS));
        assinaturaRepository.save(assinatura);
        flushClear();
        suspensaoScheduler.suspenderInadimplentes();
        flushClear();
        assinatura = recarregar(assessoriaId);
        assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.SUSPENSA);
        assertThat(recarregarAssessoria(assessoriaId).getAtivo()).isFalse();

        // 4. Webhook PAYMENT_CONFIRMED → ATIVA + assessoria reativada (CA6)
        webhookService.processar(paymentEvent("evt-confirmed", "PAYMENT_CONFIRMED", subscriptionId));
        flushClear();
        assinatura = recarregar(assessoriaId);
        assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(assinatura.getOverdueDesde()).isNull();
        assertThat(recarregarAssessoria(assessoriaId).getAtivo()).isTrue();
    }

    private void flushClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Assinatura recarregar(UUID assessoriaId) {
        return assinaturaRepository.findByAssessoriaId(assessoriaId).orElseThrow();
    }

    private Assessoria recarregarAssessoria(UUID assessoriaId) {
        return assessoriaRepository.findById(assessoriaId).orElseThrow();
    }

    private AsaasWebhookEventDto paymentEvent(String id, String tipo, String subscriptionId) {
        return new AsaasWebhookEventDto(id, tipo, new Payment("pay-" + id, "cus1", subscriptionId), null);
    }

    private Assessoria seedAssessoria() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Billing E2E");
        assessoria.setDominio("billing-e2e-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria.setAtivo(true);
        return assessoriaRepository.save(assessoria);
    }
}
