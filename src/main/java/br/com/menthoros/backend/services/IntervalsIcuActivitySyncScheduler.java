package br.com.menthoros.backend.services;

import br.com.menthoros.backend.config.external.IntervalsIcuProperties;
import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.ErroCategoriaPull;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.ResultadoPull;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import br.com.menthoros.backend.exception.IntervalsIcuRateLimitException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.helper.IntervalsIcuActivityMapper;
import br.com.menthoros.backend.services.helper.PullAcumulador;
import br.com.menthoros.backend.services.helper.PullResultado;
import br.com.menthoros.backend.services.helper.SyncDescarteWriter;
import br.com.menthoros.backend.services.helper.SyncPullLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 *
 * <p>O cursor é {@code pull_cursor}, exclusivo deste scheduler (fix-sync-cursor-data-loss D1): o
 * {@code ultimaSincronizacao} que usava antes também era gravado pelo push de treinos com
 * {@code now()}, e um plano aprovado com o pull atrasado tirava da janela o que ficou para trás.
 * Cursor e status são gravados por {@code UPDATE} pontual, nunca por {@code save} da entidade.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntervalsIcuActivitySyncScheduler {

    /** Tamanho de {@code tb_integracao_externa.last_sync_error} (V16). Acima disso o save falha. */
    private static final int LAST_SYNC_ERROR_MAX = 500;
    private static final FonteDados PLATAFORMA = FonteDados.INTERVALS_ICU;

    private final IntegracaoExternaRepository integracaoExternaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final IntervalsIcuClient intervalsIcuClient;
    private final IntervalsIcuActivityIngestionService ingestionService;
    private final IntervalsIcuActivityMapper activityMapper;
    private final IntervalsIcuProperties props;
    private final SyncPullLogWriter pullLogWriter;
    private final SyncDescarteWriter descarteWriter;

    /**
     * Um ciclo de sync para todos os atletas com intervals.icu ativo e não pausado, em todos os
     * tenants.
     *
     * Idempotent: YES — reexecutar relista a mesma janela e o dedup por externalId absorve o que já
     *   foi importado; o cursor só anda para frente.
     * Side Effects: External API call (1 listagem + até N buscas por atleta) + Database update
     *   (pull_cursor, ultimaSincronizacao, syncActivityCount, lastSyncError) + registro do pull +
     *   descarte de atividades + tudo que a importação persiste.
     * Tenant-aware: YES — TenantContext setado por atleta e limpo no finally; sem @RequireTenant
     *   porque não há request HTTP.
     */
    @Scheduled(fixedDelayString = "PT2H", initialDelayString = "PT1M")
    public void runDailyIncrementalSync() {
        List<IntegracaoExterna> integracoes = integracaoExternaRepository.findAllActiveByPlataforma(PLATAFORMA);
        for (IntegracaoExterna integracao : integracoes) {
            UUID tenantId = integracao.getTenantId();
            UUID atletaId = integracao.getAtleta().getId();
            try {
                TenantContext.setTenantId(tenantId);

                // Late-check TOCTOU: revalida ativo E autoSyncPausado com query fresca — cobre o
                // coach desconectando ou pausando ENTRE a listagem do ciclo e a vez deste atleta.
                Optional<IntegracaoExterna> fresca = integracaoExternaRepository
                        .findByAtletaIdAndPlataformaAndTenantId(atletaId, PLATAFORMA, tenantId);
                if (fresca.isEmpty() || !fresca.get().isAtivo() || fresca.get().isAutoSyncPausado()) {
                    log.info("intervals.icu sync pulado (inativa/pausada no late-check): tenant={} atleta={}",
                            tenantId, atletaId);
                    continue;
                }

                registrarPull(tenantId, atletaId, syncAtleta(fresca.get()));
            } catch (Exception ex) {
                // syncAtleta não lança (D5): chegar aqui é bug ou falha antes de qualquer trabalho
                // (late-check). Nada foi inserido, então FALHA/0 é verdade.
                log.warn("Falha no intervals.icu sync tenant={} atleta={} erro={}", tenantId, atletaId, ex.getMessage());
                registrarErro(atletaId, tenantId, mensagemSegura(ex));
                registrarPull(tenantId, atletaId,
                        new PullResultado(ResultadoPull.FALHA, ErroCategoriaPull.INESPERADO, 0, 0));
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * O ciclo de um atleta. Não lança: o acumulador cobre listagem, lote e finalização, então uma
     * falha tardia (ex.: gravar o cursor) ainda devolve as inserções já commitadas (D5).
     */
    private PullResultado syncAtleta(IntegracaoExterna integracao) {
        UUID integracaoId = integracao.getId();
        UUID atletaId = integracao.getAtleta().getId();
        UUID tenantId = integracao.getTenantId();
        PullAcumulador acc = new PullAcumulador();
        Lote lote = new Lote();
        Instant cursorAtual = integracao.getPullCursor();
        String erroStatus = null;

        try {
            cursorAtual = horizonteSeNulo(integracaoId, tenantId, cursorAtual);
            LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
            LocalDate oldest = cursorAtual.atZone(ZoneOffset.UTC).toLocalDate().minusDays(props.getSyncOverlapDays());

            List<IcuActivityDto> atividades = intervalsIcuClient.listarAtividades(
                    integracao.getAccessToken(), integracao.getExternalAthleteId(), oldest, hoje);
            List<Pendente> pendentes = pendentes(atividades, atletaId, tenantId, acc);

            int maxPorCiclo = props.getSyncMaxActivitiesPerCycle();
            lote.esgotouJanela = pendentes.size() <= maxPorCiclo;
            processarLote(pendentes.subList(0, Math.min(maxPorCiclo, pendentes.size())),
                    atletaId, tenantId, acc, lote);
            if (!lote.esgotouJanela) {
                acc.backlogPendente();
            }
            log.info("intervals.icu lote tenant={} atleta={} novas={} pendentesRestantes={}",
                    tenantId, atletaId, acc.insercoes(), Math.max(0, pendentes.size() - maxPorCiclo));
        } catch (IntervalsIcuApiException ex) {
            log.warn("Listagem intervals.icu falhou tenant={} atleta={}: {}", tenantId, atletaId, ex.getMessage());
            acc.interrompido(categoria(ex.getStatus()));
            lote.falhaTransitoria = true;
            erroStatus = mensagemSegura(ex);
        } catch (RuntimeException ex) {
            log.warn("Falha inesperada no ciclo intervals.icu tenant={} atleta={}: {}", tenantId, atletaId, ex.getMessage());
            acc.interrompido(PullAcumulador.falhaDeInfraestrutura(ex)
                    ? ErroCategoriaPull.TRANSITORIO : ErroCategoriaPull.INESPERADO);
            lote.falhaTransitoria = true;
            erroStatus = mensagemSegura(ex);
        }

        try {
            finalizar(integracaoId, atletaId, tenantId, cursorAtual, acc, lote, erroStatus);
        } catch (RuntimeException ex) {
            log.warn("Falha ao gravar cursor/status intervals.icu tenant={} atleta={}: {}",
                    tenantId, atletaId, ex.getMessage());
            acc.interrompido(ErroCategoriaPull.INESPERADO);
        }
        return acc.resultado();
    }

    /**
     * Cursor nulo (conexão nova, ou integração que nunca sincronizou): grava o horizonte ANTES de
     * buscar. Sem isso, falhas seguidas recalculariam {@code now − syncDaysBack} a cada ciclo, e a
     * cobertura encolheria um pouco a cada falha (D1, CA4).
     */
    private Instant horizonteSeNulo(UUID integracaoId, UUID tenantId, @Nullable Instant cursorAtual) {
        if (cursorAtual != null) {
            return cursorAtual;
        }
        Instant horizonte = Instant.now().minus(Duration.ofDays(props.getSyncDaysBack()));
        gravarCursor(integracaoId, tenantId, horizonte);
        return horizonte;
    }

    private void gravarCursor(UUID integracaoId, UUID tenantId, Instant cursor) {
        if (integracaoExternaRepository.atualizarPullCursor(integracaoId, tenantId, cursor) == 0) {
            // tenant divergente ou integração removida no meio do ciclo: nada foi gravado (CA9)
            log.warn("pull_cursor intervals.icu não atualizado: integracao={} tenant={}", integracaoId, tenantId);
        }
    }

    /**
     * Da mais antiga para a mais nova: a API devolve decrescente, e a carga inicial precisa construir
     * o PMC em ordem cronológica para o cursor poder apontar "até onde cheguei". Já importada e
     * descartada custam zero requisição e não contam no teto (D7, CA11).
     *
     * <p>Modalidade vem na listagem, então o filtro é gratuito — e necessário: o smoke de 2026-08-22
     * mostrou 6 atividades de natação/bike/musculação sendo buscadas (1 req cada) só para serem
     * rejeitadas pela ingestão, e com o overlap de 7 dias elas seriam rebuscadas em TODO ciclo por
     * uma semana: 72 das 100 req/dia de um triatleta, em nada.</p>
     */
    private List<Pendente> pendentes(List<IcuActivityDto> atividades, UUID atletaId, UUID tenantId,
                                     PullAcumulador acc) {
        Set<String> descartadas = descarteWriter.descartadas(tenantId, atletaId, PLATAFORMA);
        List<Pendente> pendentes = new ArrayList<>();
        for (IcuActivityDto a : atividades) {
            if (!activityMapper.isModalidadeSuportada(a.type())) {
                log.debug("Activity {} do atleta {} ignorada sem buscar: modalidade {}", a.id(), atletaId, a.type());
                continue;
            }
            Optional<Pendente> pendente = Pendente.de(a, atletaId);
            if (pendente.isEmpty()) {
                // sem posição no tempo, não pode bloquear o cursor — mas também não some calado
                acc.ignorada(ErroCategoriaPull.DADOS_INVALIDOS);
                continue;
            }
            if (descartadas.contains(a.id())) {
                continue;
            }
            if (treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(tenantId, PLATAFORMA, a.id()).isPresent()) {
                continue;
            }
            pendentes.add(pendente.get());
        }
        pendentes.sort(Comparator.comparing(Pendente::inicio));
        return pendentes;
    }

    private void processarLote(List<Pendente> lote, UUID atletaId, UUID tenantId, PullAcumulador acc, Lote estado) {
        for (Pendente pendente : lote) {
            try {
                if (ingestionService.importarAtividadeAgendada(atletaId, pendente.id(), tenantId).inserida()) {
                    acc.inserida();
                } else {
                    acc.avancou();
                }
                estado.ultimaProcessada = pendente.inicio();
            } catch (IntervalsIcuRateLimitException | DomainConflictException ex) {
                // Transitória ou de estado do atleta (429, credencial revogada, Strava ainda ativo):
                // insistir nas próximas só gasta cota. O cursor fica onde chegou e o próximo ciclo
                // relista a partir daí — nada que ficou sem tentativa sai da janela.
                estado.falhaTransitoria = true;
                acc.interrompido(ex instanceof DomainConflictException
                        ? ErroCategoriaPull.CONFLITO : ErroCategoriaPull.TRANSITORIO);
                log.warn("Lote intervals.icu abortado em activity {} do atleta {}: {}",
                        pendente.id(), atletaId, ex.getMessage());
                return;
            } catch (DomainNotFoundException | DomainRuleViolationException ex) {
                // Permanente desta atividade (404, 422): registrada como descartada para não ser
                // rebuscada todo ciclo dentro do overlap, consumindo o teto (D7).
                descarteWriter.registrarPermanente(tenantId, atletaId, PLATAFORMA, pendente.id());
                acc.ignorada(ErroCategoriaPull.DADOS_INVALIDOS);
                estado.ultimaProcessada = pendente.inicio();
                log.warn("Activity {} do atleta {} descartada (permanente): {}",
                        pendente.id(), atletaId, ex.getMessage());
            } catch (RuntimeException ex) {
                if (PullAcumulador.falhaDeInfraestrutura(ex)) {
                    // banco/transação: não é defeito da atividade, então não conta tentativa (QA)
                    estado.falhaTransitoria = true;
                    acc.interrompido(ErroCategoriaPull.TRANSITORIO);
                    log.warn("Lote intervals.icu interrompido por falha de infraestrutura em activity {} do atleta {}: {}",
                            pendente.id(), atletaId, ex.getClass().getSimpleName());
                    return;
                }
                // Inesperada: retentada até a 3ª vez (cursor não passa); depois descartada com
                // registro, para uma atividade com erro determinístico não travar o atleta (D7, CA12).
                if (descarteWriter.registrarFalha(tenantId, atletaId, PLATAFORMA, pendente.id())) {
                    acc.ignorada(ErroCategoriaPull.INESPERADO);
                    estado.ultimaProcessada = pendente.inicio();
                    log.warn("Activity {} do atleta {} descartada após falhas recorrentes: {}",
                            pendente.id(), atletaId, ex.getMessage());
                    continue;
                }
                estado.falhaTransitoria = true;
                acc.interrompido(ErroCategoriaPull.INESPERADO);
                log.warn("Lote intervals.icu interrompido por falha inesperada em activity {} do atleta {}: {}",
                        pendente.id(), atletaId, ex.getMessage());
                return;
            }
        }
    }

    private void finalizar(UUID integracaoId, UUID atletaId, UUID tenantId, Instant cursorAtual,
                           PullAcumulador acc, Lote lote, @Nullable String erroStatus) {
        // Releitura antes de gravar: se o coach desconectou enquanto o provedor respondia, o ciclo não
        // deixa rastro na integração desligada.
        Optional<IntegracaoExterna> paraAtualizar = integracaoExternaRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, PLATAFORMA, tenantId);
        if (paraAtualizar.isEmpty() || !paraAtualizar.get().isAtivo()) {
            log.info("Atleta {} desconectou o intervals.icu durante o ciclo — nada persistido", atletaId);
            return;
        }
        IntegracaoExterna atual = paraAtualizar.get();

        EstadoCursor estado = calcularCursor(lote.falhaTransitoria, lote.esgotouJanela, lote.ultimaProcessada, cursorAtual);
        if (estado.cursor() != null && !estado.cursor().equals(cursorAtual)) {
            gravarCursor(integracaoId, tenantId, estado.cursor());
        }

        // FALHA não finge sincronização: o "último sync" que o coach vê fica onde estava.
        Instant ultima = acc.resultado().resultado() == ResultadoPull.FALHA
                ? atual.getUltimaSincronizacao()
                : Instant.now();
        int contagem = (atual.getSyncActivityCount() == null ? 0 : atual.getSyncActivityCount()) + acc.insercoes();
        String erro = erroStatus != null ? erroStatus : estado.erro();
        integracaoExternaRepository.atualizarStatusSync(integracaoId, tenantId, ultima, contagem,
                erro != null ? erro : acc.resultado().avisoDeIgnoradas());
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

    /** Estado mutável do lote de um ciclo — o que {@link #calcularCursor} precisa. */
    private static final class Lote {
        private boolean falhaTransitoria;
        private boolean esgotouJanela;
        private @Nullable Instant ultimaProcessada;
    }

    private static ErroCategoriaPull categoria(@Nullable HttpStatusCode status) {
        if (status == null) {
            return ErroCategoriaPull.TRANSITORIO;
        }
        int code = status.value();
        if (code == 401 || code == 403) {
            return ErroCategoriaPull.CREDENCIAL;
        }
        return code == 429 ? ErroCategoriaPull.RATE_LIMIT : ErroCategoriaPull.TRANSITORIO;
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

    /**
     * Best-effort: o registro é observabilidade, e falhar ao gravá-lo não pode derrubar o ciclo dos
     * demais atletas. Fica fora da transação do writer pelo mesmo motivo de {@code LlmCallLedger}.
     */
    private void registrarPull(UUID tenantId, UUID atletaId, PullResultado resultado) {
        try {
            pullLogWriter.registrar(tenantId, atletaId, PLATAFORMA, resultado, Instant.now());
        } catch (RuntimeException ex) {
            log.warn("Falha ao registrar pull intervals.icu tenant={} atleta={}: {}", tenantId, atletaId, ex.getMessage());
        }
    }

    private void registrarErro(UUID atletaId, UUID tenantId, String mensagem) {
        // UPDATE pontual, não save (D0): um save regravaria tokens e demais colunas com o que foi lido
        // aqui, desfazendo o que outro escritor gravou entre a leitura e o commit.
        integracaoExternaRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, PLATAFORMA, tenantId)
                .filter(IntegracaoExterna::isAtivo)
                .ifPresent(i -> integracaoExternaRepository.atualizarStatusSync(i.getId(), tenantId,
                        i.getUltimaSincronizacao(), i.getSyncActivityCount(), mensagem));
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
