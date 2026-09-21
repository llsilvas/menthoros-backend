package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.entity.AthleteContract;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AthleteContractRepository;
import br.com.menthoros.backend.services.AthleteContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Renovação diária das mensalidades (design D3): para todo contrato ativo, garante uma
 * mensalidade com vencimento maior ou igual a hoje. Roda FORA de request — itera os tenants com
 * contrato ativo populando/limpando o {@link TenantContext} por iteração, como
 * {@code EncerramentoSemanaScheduler}; falha isolada por tenant e por contrato.
 *
 * <p>Concorrência: depende do pool single-thread do {@code @Scheduled}; o lock pessimista no
 * contrato serializa com o proprietário. Recupera dias perdidos até o teto por contrato por
 * execução — a execução seguinte continua.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AthleteInvoiceRenewalScheduler {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final AthleteContractRepository contractRepository;
    private final AthleteContractService contractService;
    private final Clock clock;

    @Value("${menthoros.athlete-invoice.renewal.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${menthoros.athlete-invoice.renewal.cron:0 0 4 * * *}", zone = "America/Sao_Paulo")
    public void renew() {
        if (!enabled) {
            log.debug("Renovação de mensalidades desabilitada");
            return;
        }
        renew(LocalDate.now(clock.withZone(ZONE)));
    }

    /** @return total de mensalidades geradas em todos os tenants */
    public int renew(LocalDate today) {
        int total = 0;
        for (UUID tenantId : contractRepository.findTenantIdsWithActiveContract()) {
            try {
                TenantContext.setTenantId(tenantId);
                total += renewTenant(tenantId, today);
            } catch (Exception e) {
                log.warn("Falha na renovação de mensalidades do tenant {}: {}", tenantId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        if (total > 0) {
            log.info("Renovação de mensalidades: {} gerada(s) em {}", total, today);
        }
        return total;
    }

    private int renewTenant(UUID tenantId, LocalDate today) {
        int generated = 0;
        for (AthleteContract contract : contractRepository.findActiveByTenantId(tenantId)) {
            try {
                generated += contractService.ensureNextInvoice(contract.getId(), tenantId, today);
            } catch (Exception e) {
                log.warn("Falha na renovação do contrato {} (tenant {}): {}", contract.getId(), tenantId, e.getMessage());
            }
        }
        return generated;
    }
}
