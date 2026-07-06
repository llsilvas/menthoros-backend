package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
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
 * Fallback automático de encerramento de semanas: fecha diariamente os planos que o treinador não
 * encerrou manualmente, após a carência configurável. Roda FORA de request — itera as assessorias
 * ativas populando/limpando o {@link TenantContext} por iteração (padrão do StravaActivitySyncScheduler).
 *
 * <p><b>Concorrência:</b> depende do pool single-thread padrão do {@code @Scheduled} (uma execução por
 * vez). Cada iteração limpa o {@link TenantContext} no {@code finally}, então uma falha não vaza tenant
 * para a próxima. Se um {@code SchedulingConfigurer} com pool &gt; 1 for adicionado no futuro, garanta que
 * este job não rode de forma re-entrante sobre o mesmo tenant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EncerramentoSemanaScheduler {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final AssessoriaRepository assessoriaRepository;
    private final EncerramentoSemanaService encerramentoSemanaService;
    private final Clock clock;

    @Value("${menthoros.encerramento-semana.carencia-dias:3}")
    private int carenciaDias;

    @Value("${menthoros.encerramento-semana.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${menthoros.encerramento-semana.cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    public void encerrarSemanasVencidas() {
        if (!enabled) {
            log.debug("Encerramento automático de semanas desabilitado");
            return;
        }
        if (carenciaDias < 0) {
            log.error("carencia-dias inválida ({}); encerramento automático abortado", carenciaDias);
            return;
        }
        LocalDate hoje = LocalDate.now(clock.withZone(ZONA));
        for (Assessoria assessoria : assessoriaRepository.findByAtivoTrue()) {
            UUID tenantId = assessoria.getId();
            try {
                TenantContext.setTenantId(tenantId);
                int encerrados = encerramentoSemanaService.encerrarPlanosElegiveis(tenantId, hoje, carenciaDias);
                if (encerrados > 0) {
                    log.info("Fallback de encerramento: {} plano(s) fechado(s) no tenant {}", encerrados, tenantId);
                }
            } catch (Exception e) {
                log.warn("Falha no encerramento automático do tenant {}: {}", tenantId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }
}
