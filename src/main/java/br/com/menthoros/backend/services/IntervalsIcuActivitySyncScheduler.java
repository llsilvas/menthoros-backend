package br.com.menthoros.backend.services;

import br.com.menthoros.backend.config.external.IntervalsIcuProperties;
import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.IntervalsIcuRateLimitException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pull automático de atividades do intervals.icu — espelha {@link StravaActivitySyncScheduler},
 * reaproveitando o pipeline individual de {@link IntervalsIcuActivityIngestionService} como um
 * caller a mais. Permanece necessário depois do webhook: o provedor não entrega webhooks para
 * atividades que entram nele via Strava, então este é o único caminho com cobertura completa.
 *
 * <p>O provedor limita a 100 requisições por usuário por dia e não expõe a folga em header. Cada
 * ciclo custa 1 listagem + 1 busca por atividade nova, então o lote por atleta é limitado por
 * contagem ({@code syncMaxActivitiesPerCycle}) e o cursor avança até a última atividade processada —
 * nunca além. A carga inicial de 90 dias vira progresso incremental em vez de tudo-ou-nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntervalsIcuActivitySyncScheduler {

    private final IntegracaoExternaRepository integracaoExternaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final IntervalsIcuClient intervalsIcuClient;
    private final IntervalsIcuActivityIngestionService ingestionService;
    private final IntervalsIcuProperties props;

    /**
     * Um ciclo de sync para todos os atletas com intervals.icu ativo e não pausado, em todos os
     * tenants.
     *
     * Idempotent: YES — reexecutar relista a mesma janela e o dedup por externalId absorve o que já
     *   foi importado; o cursor só anda para frente.
     * Side Effects: External API call (1 listagem + até N buscas por atleta) + Database update
     *   (ultimaSincronizacao, syncActivityCount, lastSyncError) + tudo que importarAtividade persiste.
     * Tenant-aware: YES — TenantContext setado por atleta e limpo no finally; sem @RequireTenant
     *   porque não há request HTTP.
     */
    @Scheduled(fixedDelayString = "PT2H", initialDelayString = "PT1M")
    public void runDailyIncrementalSync() {
        List<IntegracaoExterna> integracoes =
                integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU);
        for (IntegracaoExterna integracao : integracoes) {
            UUID tenantId = integracao.getTenantId();
            UUID atletaId = integracao.getAtleta().getId();
            try {
                TenantContext.setTenantId(tenantId);

                // Late-check TOCTOU: revalida ativo E autoSyncPausado com query fresca — cobre o
                // coach desconectando ou pausando ENTRE a listagem do ciclo e a vez deste atleta.
                Optional<IntegracaoExterna> fresca = integracaoExternaRepository
                        .findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId);
                if (fresca.isEmpty() || !fresca.get().isAtivo() || fresca.get().isAutoSyncPausado()) {
                    log.info("intervals.icu sync pulado (inativa/pausada no late-check): tenant={} atleta={}",
                            tenantId, atletaId);
                    continue;
                }

                syncAtleta(fresca.get());
            } catch (Exception ex) {
                // Falha de ATLETA (listagem 401/429/timeout, ou erro inesperado): o ciclo segue
                // para os demais e o motivo fica visível em lastSyncError — sem isso, credencial
                // revogada e rate limit viram só uma linha de log que ninguém lê.
                log.warn("Falha no intervals.icu sync tenant={} atleta={} erro={}", tenantId, atletaId, ex.getMessage());
                registrarErro(atletaId, tenantId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void syncAtleta(IntegracaoExterna integracao) {
        UUID atletaId = integracao.getAtleta().getId();
        UUID tenantId = integracao.getTenantId();
        LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
        LocalDate oldest = integracao.getUltimaSincronizacao() != null
                ? integracao.getUltimaSincronizacao().atZone(ZoneOffset.UTC).toLocalDate()
                        .minusDays(props.getSyncOverlapDays())
                : hoje.minusDays(props.getSyncDaysBack());

        List<IcuActivityDto> atividades = intervalsIcuClient.listarAtividades(
                integracao.getAccessToken(), integracao.getExternalAthleteId(), oldest, hoje);

        long antesDoLote = treinoRealizadoRepository
                .countByTenantIdAndAtletaIdAndFonteDados(tenantId, atletaId, FonteDados.INTERVALS_ICU);

        // Da mais antiga para a mais nova: a API devolve decrescente, e a carga inicial precisa
        // construir o PMC em ordem cronológica para o cursor poder apontar "até onde cheguei".
        // Já importada custa zero requisição e não conta no teto.
        List<IcuActivityDto> pendentes = atividades.stream()
                .filter(a -> treinoRealizadoRepository
                        .findByTenantIdAndFonteDadosAndExternalId(tenantId, FonteDados.INTERVALS_ICU, a.id())
                        .isEmpty())
                .sorted(Comparator.comparing(IcuActivityDto::startDate))
                .toList();
        int maxPorCiclo = props.getSyncMaxActivitiesPerCycle();
        boolean esgotouJanela = pendentes.size() <= maxPorCiclo;
        List<IcuActivityDto> lote = pendentes.subList(0, Math.min(maxPorCiclo, pendentes.size()));

        boolean falhaTransitoria = false;
        IcuActivityDto ultimaProcessada = null;
        for (IcuActivityDto atividade : lote) {
            try {
                ingestionService.importarAtividade(atletaId, atividade.id(), tenantId);
                ultimaProcessada = atividade;
            } catch (IntervalsIcuRateLimitException | DomainConflictException ex) {
                // Transitória ou de estado do atleta (429, credencial revogada, Strava ainda ativo):
                // insistir nas próximas só gasta cota. O cursor fica onde chegou e o próximo ciclo
                // relista a partir daí — nada que ficou sem tentativa sai da janela.
                falhaTransitoria = true;
                log.warn("Lote intervals.icu abortado em activity {} do atleta {}: {}",
                        atividade.id(), atletaId, ex.getMessage());
                break;
            } catch (DomainNotFoundException | DomainRuleViolationException ex) {
                // Permanente desta atividade (modalidade não suportada, 404): não é retryable,
                // então o cursor pode passar por ela.
                ultimaProcessada = atividade;
                log.warn("Activity {} do atleta {} ignorada (permanente): {}",
                        atividade.id(), atletaId, ex.getMessage());
            }
        }

        long depoisDoLote = treinoRealizadoRepository
                .countByTenantIdAndAtletaIdAndFonteDados(tenantId, atletaId, FonteDados.INTERVALS_ICU);
        int novasImportadas = (int) (depoisDoLote - antesDoLote);

        // Reload antes do save: a instância do início do ciclo pode estar stale se o coach
        // desconectou enquanto o provedor respondia — salvar ela ressuscitaria a conexão.
        Optional<IntegracaoExterna> paraAtualizar = integracaoExternaRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId);
        if (paraAtualizar.isEmpty() || !paraAtualizar.get().isAtivo()) {
            log.info("Atleta {} desconectou o intervals.icu durante o ciclo — nada persistido", atletaId);
            return;
        }
        IntegracaoExterna atual = paraAtualizar.get();
        atual.setSyncActivityCount(
                (atual.getSyncActivityCount() == null ? 0 : atual.getSyncActivityCount()) + novasImportadas);

        // O cursor avança até onde o ciclo chegou, nunca além:
        //   janela esgotada sem falha transitória  -> agora (regime de cruzeiro)
        //   lote parcial (teto) ou falha transitória -> instante da última processada
        //   falha logo na primeira                   -> intocado
        if (!falhaTransitoria && esgotouJanela) {
            atual.setUltimaSincronizacao(Instant.now());
            atual.setLastSyncError(null);
        } else if (ultimaProcessada != null) {
            atual.setUltimaSincronizacao(Instant.parse(ultimaProcessada.startDate()));
            atual.setLastSyncError(falhaTransitoria
                    ? "Ciclo interrompido por falha transitória — cursor em " + ultimaProcessada.startDate()
                    : null);
        } else if (falhaTransitoria) {
            atual.setLastSyncError("Ciclo interrompido por falha transitória — cursor mantido para retry");
        }
        integracaoExternaRepository.save(atual);
        log.info("intervals.icu sync concluído tenant={} atleta={} novas={} pendentesRestantes={}",
                tenantId, atletaId, novasImportadas, Math.max(0, pendentes.size() - lote.size()));
    }

    private void registrarErro(UUID atletaId, UUID tenantId, String mensagem) {
        integracaoExternaRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId)
                .filter(IntegracaoExterna::isAtivo)
                .ifPresent(i -> {
                    i.setLastSyncError(mensagem);
                    integracaoExternaRepository.save(i);
                });
    }
}
