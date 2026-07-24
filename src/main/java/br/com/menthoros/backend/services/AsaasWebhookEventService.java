package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.asaas.AsaasWebhookEventDto;
import br.com.menthoros.backend.entity.AsaasWebhookEventoProcessado;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Assinatura;
import br.com.menthoros.backend.enums.StatusAssinatura;
import br.com.menthoros.backend.repository.AsaasWebhookEventoProcessadoRepository;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AssinaturaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Processa eventos do webhook do Asaas: idempotência (CA10) + transições de estado da
 * {@link Assinatura} (design.md Decisão 2/4). Cross-tenant — resolve pela {@code asaasSubscriptionId},
 * sem {@code TenantContext} (o payload do Asaas não traz {@code tenant_id}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasWebhookEventService {

    private final AssinaturaRepository assinaturaRepository;
    private final AssessoriaRepository assessoriaRepository;
    private final AsaasWebhookEventoProcessadoRepository eventoProcessadoRepository;

    /**
     * Processa um evento do webhook, uma única vez (idempotente por {@code event.id}).
     *
     * <p><strong>Idempotent:</strong> YES — reenvio do mesmo evento não reaplica a transição (CA10).
     * <p><strong>Side Effects:</strong> DB (Assinatura.status/overdueDesde + Assessoria.ativo + registro de idempotência).
     * <p><strong>Tenant-aware:</strong> NO — cross-tenant, resolve por {@code asaasSubscriptionId}.
     */
    @Transactional
    public void processar(AsaasWebhookEventDto event) {
        String eventoId = event.id();
        if (eventoId != null && eventoProcessadoRepository.existsById(eventoId)) {
            log.info("[asaas-webhook] evento já processado (idempotência): id={}, tipo={}", eventoId, event.event());
            return;
        }

        aplicarTransicao(event);

        if (eventoId != null) {
            eventoProcessadoRepository.save(new AsaasWebhookEventoProcessado(eventoId, event.event()));
        }
    }

    private void aplicarTransicao(AsaasWebhookEventDto event) {
        String tipo = event.event();
        if (tipo == null) {
            log.warn("[asaas-webhook] evento sem tipo — ignorado (id={})", event.id());
            return;
        }
        String subscriptionId = event.resolverSubscriptionId();
        if (subscriptionId == null) {
            log.warn("[asaas-webhook] evento {} sem subscriptionId — ignorado", tipo);
            return;
        }
        Assinatura assinatura = assinaturaRepository.findByAsaasSubscriptionId(subscriptionId).orElse(null);
        if (assinatura == null) {
            log.warn("[asaas-webhook] assinatura não encontrada para subscriptionId={} (evento {})", subscriptionId, tipo);
            return;
        }

        switch (tipo) {
            case "PAYMENT_OVERDUE" -> aoVencer(assinatura);
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> aoPagar(assinatura);
            case "SUBSCRIPTION_DELETED", "SUBSCRIPTION_INACTIVATED" -> aoCancelarExterno(assinatura);
            default -> log.info("[asaas-webhook] evento {} recebido, sem transição (subscriptionId={})", tipo, subscriptionId);
        }
    }

    /** CA3: ATIVA → INADIMPLENTE, marca overdueDesde. Assessoria.ativo inalterado (carência). */
    private void aoVencer(Assinatura assinatura) {
        if (assinatura.getStatus() == StatusAssinatura.ATIVA) {
            assinatura.setStatus(StatusAssinatura.INADIMPLENTE);
            assinatura.setOverdueDesde(Instant.now());
            assinaturaRepository.save(assinatura);
            log.info("[asaas-webhook] assinatura {} -> INADIMPLENTE (carência iniciada)", assinatura.getId());
        }
    }

    /** CA4/CA6: INADIMPLENTE/SUSPENSA → ATIVA; se estava SUSPENSA, reativa a assessoria. */
    private void aoPagar(Assinatura assinatura) {
        StatusAssinatura atual = assinatura.getStatus();
        if (atual == StatusAssinatura.INADIMPLENTE || atual == StatusAssinatura.SUSPENSA) {
            assinatura.setStatus(StatusAssinatura.ATIVA);
            assinatura.setOverdueDesde(null);
            assinaturaRepository.save(assinatura);
            if (atual == StatusAssinatura.SUSPENSA) {
                setAssessoriaAtiva(assinatura.getAssessoriaId(), true);
            }
            log.info("[asaas-webhook] assinatura {} -> ATIVA (pagamento resolvido)", assinatura.getId());
        }
    }

    /** CA8: reconciliação de segurança — qualquer estado → CANCELADA + Assessoria.ativo=false. */
    private void aoCancelarExterno(Assinatura assinatura) {
        if (assinatura.getStatus() != StatusAssinatura.CANCELADA) {
            assinatura.setStatus(StatusAssinatura.CANCELADA);
            assinaturaRepository.save(assinatura);
            setAssessoriaAtiva(assinatura.getAssessoriaId(), false);
            log.info("[asaas-webhook] assinatura {} -> CANCELADA (reconciliação do Asaas)", assinatura.getId());
        }
    }

    private void setAssessoriaAtiva(UUID assessoriaId, boolean ativo) {
        Assessoria assessoria = assessoriaRepository.findById(assessoriaId).orElse(null);
        if (assessoria == null) {
            log.warn("[asaas-webhook] assessoria {} não encontrada ao sincronizar ativo={}", assessoriaId, ativo);
            return;
        }
        assessoria.setAtivo(ativo);
        assessoriaRepository.save(assessoria);
    }
}
