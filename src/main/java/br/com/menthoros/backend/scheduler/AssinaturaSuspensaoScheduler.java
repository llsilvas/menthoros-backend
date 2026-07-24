package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Assinatura;
import br.com.menthoros.backend.enums.StatusAssinatura;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AssinaturaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Job diário de carência (assessoria-billing-asaas, design.md Decisão 7 / CA5). Cross-tenant por
 * natureza (mesmo padrão do {@code DailyActivitySyncScheduler}): não usa {@code TenantContext},
 * itera todas as {@link Assinatura} {@code INADIMPLENTE} há mais de 5 dias corridos e as transiciona
 * para {@code SUSPENSA} + {@code Assessoria.ativo=false} (bloqueio total do tenant).
 *
 * <p>{@code overdueDesde} é gravado pelo webhook {@code PAYMENT_OVERDUE} (Decisão 2), não recalculado
 * aqui — o job só compara com o corte de 5 dias.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssinaturaSuspensaoScheduler {

    private static final int DIAS_CARENCIA = 5;

    private final AssinaturaRepository assinaturaRepository;
    private final AssessoriaRepository assessoriaRepository;

    /**
     * Roda diariamente (3h, horário do servidor). Cada suspensão é isolada (falha em uma não impede
     * as outras); os dois {@code save} de uma suspensão não são atômicos entre si — aceitável para um
     * job de limpeza (o estado é auto-corrigível por um evento futuro).
     */
    @Scheduled(cron = "${asaas.suspensao.cron:0 0 3 * * *}")
    public void suspenderInadimplentes() {
        Instant corte = Instant.now().minus(DIAS_CARENCIA, ChronoUnit.DAYS);
        List<Assinatura> vencidas =
                assinaturaRepository.findByStatusAndOverdueDesdeBefore(StatusAssinatura.INADIMPLENTE, corte);

        if (vencidas.isEmpty()) {
            log.info("[carencia] nenhuma assinatura INADIMPLENTE além dos {} dias de carência", DIAS_CARENCIA);
            return;
        }
        log.info("[carencia] {} assinatura(s) a suspender (INADIMPLENTE > {} dias)", vencidas.size(), DIAS_CARENCIA);

        for (Assinatura assinatura : vencidas) {
            try {
                suspender(assinatura);
            } catch (Exception e) {
                log.error("[carencia] falha ao suspender assinatura {}: {}", assinatura.getId(), e.getMessage(), e);
            }
        }
    }

    private void suspender(Assinatura assinatura) {
        assinatura.setStatus(StatusAssinatura.SUSPENSA);
        assinaturaRepository.save(assinatura);

        UUID assessoriaId = assinatura.getAssessoriaId();
        Assessoria assessoria = assessoriaRepository.findById(assessoriaId).orElse(null);
        if (assessoria == null) {
            log.warn("[carencia] assessoria {} não encontrada ao suspender assinatura {}",
                    assessoriaId, assinatura.getId());
            return;
        }
        assessoria.setAtivo(false);
        assessoriaRepository.save(assessoria);
        log.info("[carencia] assinatura {} -> SUSPENSA, assessoria {} desativada", assinatura.getId(), assessoriaId);
    }
}
