package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StravaActivitySyncScheduler {

    private final IntegracaoExternaRepository integracaoExternaRepository;
    private final StravaActivityService stravaActivityService;

//    @Scheduled(fixedRate = 7200000, initialDelay = 60000)
    @Scheduled(fixedDelayString = "PT2H", initialDelayString = "PT1M")
//    @Scheduled(cron = "${menthoros.strava.sync.daily-cron:0 0 3 * * *}")
    public void runDailyIncrementalSync() {
        List<IntegracaoExterna> integracoes = integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.STRAVA);
        for (IntegracaoExterna integracao : integracoes) {
            UUID tenantId = integracao.getTenantId();
            UUID atletaId = integracao.getAtleta().getId();
            try {
                TenantContext.setTenantId(tenantId);
                int imported = stravaActivityService.syncActivities(atletaId);
                log.info("Strava daily sync concluído tenant={} atleta={} imported={}", tenantId, atletaId, imported);
            } catch (Exception ex) {
                log.warn("Falha no Strava daily sync tenant={} atleta={} erro={}", tenantId, atletaId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }
}
