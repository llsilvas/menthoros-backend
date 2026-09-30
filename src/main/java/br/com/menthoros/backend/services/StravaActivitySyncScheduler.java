package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.ErroCategoriaPull;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.ResultadoPull;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.helper.PullResultado;
import br.com.menthoros.backend.services.helper.SyncPullLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StravaActivitySyncScheduler {

    private final IntegracaoExternaRepository integracaoExternaRepository;
    private final StravaActivityService stravaActivityService;
    private final SyncPullLogWriter pullLogWriter;

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

                // Late-check (D5.2, TOCTOU): revalida ativo E autoSyncPausado com query fresca — não
                // reusa o valor lido em findAllActiveByPlataforma na listagem inicial do ciclo. Cobre o
                // coach pausando ou desconectando o atleta ENTRE a listagem e o processamento dele;
                // sem o ativo, a desconexão virava FALHA no registro e poluía a métrica de saúde.
                Optional<IntegracaoExterna> fresca = integracaoExternaRepository
                        .findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId);
                if (fresca.isEmpty() || !fresca.get().isAtivo() || fresca.get().isAutoSyncPausado()) {
                    log.info("Strava daily sync pulado (inativa/pausada no late-check): tenant={} atleta={}",
                            tenantId, atletaId);
                    continue;
                }

                PullResultado resultado = stravaActivityService.pullAgendado(atletaId);
                log.info("Strava daily sync concluído tenant={} atleta={} resultado={}", tenantId, atletaId, resultado);
                registrarPull(tenantId, atletaId, resultado);
            } catch (Exception ex) {
                // pullAgendado não lança depois de carregar a integração (D5): chegar aqui é falha
                // antes de qualquer trabalho, então FALHA/0 é verdade
                log.warn("Falha no Strava daily sync tenant={} atleta={} erro={}", tenantId, atletaId, ex.getMessage());
                registrarPull(tenantId, atletaId,
                        new PullResultado(ResultadoPull.FALHA, ErroCategoriaPull.INESPERADO, 0, 0));
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** Best-effort: o registro é observabilidade e não pode derrubar o ciclo dos demais atletas. */
    private void registrarPull(UUID tenantId, UUID atletaId, PullResultado resultado) {
        try {
            pullLogWriter.registrar(tenantId, atletaId, FonteDados.STRAVA, resultado, Instant.now());
        } catch (RuntimeException ex) {
            log.warn("Falha ao registrar pull Strava tenant={} atleta={}: {}", tenantId, atletaId, ex.getMessage());
        }
    }
}
