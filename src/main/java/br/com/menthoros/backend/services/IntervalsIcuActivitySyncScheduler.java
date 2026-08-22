package br.com.menthoros.backend.services;

import br.com.menthoros.backend.config.external.IntervalsIcuProperties;
import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import br.com.menthoros.backend.exception.IntervalsIcuRateLimitException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.helper.IntervalsIcuActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
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

    /** Tamanho de {@code tb_integracao_externa.last_sync_error} (V16). Acima disso o save falha. */
    private static final int LAST_SYNC_ERROR_MAX = 500;

    private final IntegracaoExternaRepository integracaoExternaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final IntervalsIcuClient intervalsIcuClient;
    private final IntervalsIcuActivityIngestionService ingestionService;
    private final IntervalsIcuActivityMapper activityMapper;
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
                registrarErro(atletaId, tenantId, mensagemSegura(ex));
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

        // Delta antes/depois: importarAtividade é idempotente e devolve sucesso também para o que já
        // existia, então "chamadas bem-sucedidas" contaria reprocessamento como importação nova.
        long antesDoLote = treinoRealizadoRepository
                .countByTenantIdAndAtletaIdAndFonteDados(tenantId, atletaId, FonteDados.INTERVALS_ICU);

        // Da mais antiga para a mais nova: a API devolve decrescente, e a carga inicial precisa
        // construir o PMC em ordem cronológica para o cursor poder apontar "até onde cheguei".
        // Já importada custa zero requisição e não conta no teto.
        //
        // Modalidade vem na listagem, então o filtro é gratuito — e necessário: o smoke de
        // 2026-08-22 mostrou 6 atividades de natação/bike/musculação sendo buscadas (1 req cada) só
        // para serem rejeitadas pela ingestão, e com o overlap de 7 dias elas seriam rebuscadas em
        // TODO ciclo por uma semana: 72 das 100 req/dia de um triatleta, em nada.
        List<Pendente> pendentes = atividades.stream()
                .filter(a -> {
                    boolean suportada = activityMapper.isModalidadeSuportada(a.type());
                    if (!suportada) {
                        log.debug("Activity {} do atleta {} ignorada sem buscar: modalidade {}", a.id(), atletaId, a.type());
                    }
                    return suportada;
                })
                .map(a -> Pendente.de(a, atletaId))
                .filter(Optional::isPresent).map(Optional::get)
                .filter(p -> treinoRealizadoRepository
                        .findByTenantIdAndFonteDadosAndExternalId(tenantId, FonteDados.INTERVALS_ICU, p.id())
                        .isEmpty())
                .sorted(Comparator.comparing(Pendente::inicio))
                .toList();
        int maxPorCiclo = props.getSyncMaxActivitiesPerCycle();
        boolean esgotouJanela = pendentes.size() <= maxPorCiclo;
        List<Pendente> lote = pendentes.subList(0, Math.min(maxPorCiclo, pendentes.size()));

        boolean falhaTransitoria = false;
        Instant ultimaProcessada = null;
        for (Pendente pendente : lote) {
            try {
                ingestionService.importarAtividade(atletaId, pendente.id(), tenantId);
                ultimaProcessada = pendente.inicio();
            } catch (IntervalsIcuRateLimitException | DomainConflictException ex) {
                // Transitória ou de estado do atleta (429, credencial revogada, Strava ainda ativo):
                // insistir nas próximas só gasta cota. O cursor fica onde chegou e o próximo ciclo
                // relista a partir daí — nada que ficou sem tentativa sai da janela.
                falhaTransitoria = true;
                log.warn("Lote intervals.icu abortado em activity {} do atleta {}: {}",
                        pendente.id(), atletaId, ex.getMessage());
                break;
            } catch (DomainNotFoundException | DomainRuleViolationException ex) {
                // Permanente desta atividade (modalidade não suportada, 404): não é retryable,
                // então o cursor pode passar por ela.
                ultimaProcessada = pendente.inicio();
                log.warn("Activity {} do atleta {} ignorada (permanente): {}",
                        pendente.id(), atletaId, ex.getMessage());
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

        EstadoCursor estado = calcularCursor(falhaTransitoria, esgotouJanela, ultimaProcessada,
                atual.getUltimaSincronizacao());
        atual.setUltimaSincronizacao(estado.cursor());
        atual.setLastSyncError(estado.erro());
        integracaoExternaRepository.save(atual);
        log.info("intervals.icu sync concluído tenant={} atleta={} novas={} pendentesRestantes={}",
                tenantId, atletaId, novasImportadas, Math.max(0, pendentes.size() - lote.size()));
    }

    /** Para onde o cursor vai depois do lote, e o que fica em {@code lastSyncError}. */
    record EstadoCursor(@Nullable Instant cursor, @Nullable String erro) {}

    /**
     * A regra do cursor, isolada para ser lida (e testada) sem o resto do ciclo:
     * <ul>
     *   <li>janela esgotada sem falha transitória → agora (regime de cruzeiro);</li>
     *   <li>lote parcial (teto) ou falha transitória depois de algum progresso → instante da última
     *       processada, nunca além;</li>
     *   <li>falha logo na primeira → cursor intocado, erro registrado.</li>
     * </ul>
     */
    static EstadoCursor calcularCursor(boolean falhaTransitoria, boolean esgotouJanela,
                                       @Nullable Instant ultimaProcessada, @Nullable Instant cursorAnterior) {
        if (!falhaTransitoria && esgotouJanela) {
            return new EstadoCursor(Instant.now(), null);
        }
        if (ultimaProcessada != null) {
            return new EstadoCursor(ultimaProcessada, falhaTransitoria
                    ? "Ciclo interrompido por falha transitória — cursor em " + ultimaProcessada
                    : null);
        }
        return new EstadoCursor(cursorAnterior, falhaTransitoria
                ? "Ciclo interrompido por falha transitória — cursor mantido para retry"
                : null);
    }

    /**
     * Atividade da listagem com o instante já resolvido. {@code start_date} vem em UTC na listagem
     * (gate 0.2), mas uma atividade sem ele ou com formato estranho não pode derrubar o atleta
     * inteiro nem virar cursor: é pulada com log e fica para o import manual.
     */
    private record Pendente(String id, Instant inicio) {
        static Optional<Pendente> de(IcuActivityDto dto, UUID atletaId) {
            if (dto.startDate() == null) {
                log.warn("Activity {} do atleta {} sem start_date — ignorada neste ciclo", dto.id(), atletaId);
                return Optional.empty();
            }
            try {
                return Optional.of(new Pendente(dto.id(), Instant.parse(dto.startDate())));
            } catch (DateTimeParseException ex) {
                log.warn("Activity {} do atleta {} com start_date ilegível ({}) — ignorada neste ciclo",
                        dto.id(), atletaId, dto.startDate());
                return Optional.empty();
            }
        }
    }

    private void registrarErro(UUID atletaId, UUID tenantId, String mensagem) {
        // Mesmo reload de syncAtleta: se o coach desconectou enquanto o provedor respondia, a
        // instância da listagem está stale e salvá-la ressuscitaria a conexão.
        integracaoExternaRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId)
                .filter(IntegracaoExterna::isAtivo)
                .ifPresent(i -> {
                    i.setLastSyncError(mensagem);
                    integracaoExternaRepository.save(i);
                });
    }

    /**
     * Só exceções do domínio e do client têm mensagem pensada para o coach. O resto (SQL, NPE,
     * transporte) leva detalhe interno e pode passar dos 500 caracteres da coluna — o que faria o
     * próprio registro do erro falhar.
     */
    static String mensagemSegura(Exception ex) {
        boolean conhecida = ex instanceof IntervalsIcuApiException
                || ex instanceof IntervalsIcuRateLimitException
                || ex instanceof DomainConflictException
                || ex instanceof DomainNotFoundException
                || ex instanceof DomainRuleViolationException;
        String mensagem = conhecida && ex.getMessage() != null
                ? ex.getMessage()
                : "Falha inesperada no sync (" + ex.getClass().getSimpleName() + ")";
        return mensagem.length() <= LAST_SYNC_ERROR_MAX ? mensagem : mensagem.substring(0, LAST_SYNC_ERROR_MAX);
    }
}
