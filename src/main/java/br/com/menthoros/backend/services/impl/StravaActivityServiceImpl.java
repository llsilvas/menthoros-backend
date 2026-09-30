package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.StravaSyncResponseDto;
import br.com.menthoros.backend.dto.output.StravaSyncStatusDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.dto.strava.StravaActivityDto;
import br.com.menthoros.backend.dto.strava.StravaSplitDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.exception.StravaRateLimitException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import br.com.menthoros.backend.services.StravaActivityService;
import br.com.menthoros.backend.services.StravaOAuthService;
import br.com.menthoros.backend.config.external.StravaProperties;
import br.com.menthoros.backend.enums.ErroCategoriaPull;
import br.com.menthoros.backend.enums.ResultadoPull;
import br.com.menthoros.backend.services.helper.PullAcumulador;
import br.com.menthoros.backend.services.helper.PullResultado;
import br.com.menthoros.backend.services.helper.SyncDescarteWriter;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
public class StravaActivityServiceImpl implements StravaActivityService {

    private static final FonteDados STRAVA = FonteDados.STRAVA;
    private static final Set<String> RUN_SPORT_TYPES = Set.of("Run", "TrailRun", "VirtualRun");
    private static final int POR_PAGINA = 30;
    /** Tamanho da fatia de tempo do pull (D3.2): pequena o bastante para uma fatia caber na cota. */
    private static final Duration FATIA = Duration.ofDays(14);
    /**
     * {@code after} e {@code before} são exclusivos na API (task 0.1): sem sobreposição, uma atividade
     * no segundo exato da fronteira não viria em nenhuma das duas fatias.
     */
    private static final Duration SOBREPOSICAO_FATIAS = Duration.ofSeconds(60);

    private final AtletaRepository atletaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final IntegracaoExternaRepository integracaoExternaRepository;
    private final StravaOAuthService stravaOAuthService;
    private final TreinoMapper treinoMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WebClient stravaWebClient;
    private final IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;
    private final TransactionOperations transactionOperations;
    private final SyncDescarteWriter descarteWriter;
    private final StravaProperties stravaProperties;

    public StravaActivityServiceImpl(AtletaRepository atletaRepository, TreinoRealizadoRepository treinoRealizadoRepository, IntegracaoExternaRepository integracaoExternaRepository, StravaOAuthService stravaOAuthService, TreinoMapper treinoMapper, ApplicationEventPublisher eventPublisher, @Qualifier("stravaWebClient")WebClient stravaWebClient, IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService,
                                     TransactionOperations transactionOperations, SyncDescarteWriter descarteWriter, StravaProperties stravaProperties) {
        this.atletaRepository = atletaRepository;
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.integracaoExternaRepository = integracaoExternaRepository;
        this.stravaOAuthService = stravaOAuthService;
        this.treinoMapper = treinoMapper;
        this.eventPublisher = eventPublisher;
        this.stravaWebClient = stravaWebClient;
        this.ingestaoTreinoRealizadoService = ingestaoTreinoRealizadoService;
        this.transactionOperations = transactionOperations;
        this.descarteWriter = descarteWriter;
        this.stravaProperties = stravaProperties;
    }

    @Transactional(readOnly = true)
    public List<StravaActivityDto> fetchActivities(String accessToken, Instant after, int page) {
        return fetchActivitiesWithHeaders(accessToken, after, null, page).corridas();
    }

    @Transactional(readOnly = true)
    public List<StravaSplitDto> fetchActivityLaps(String accessToken, Long activityId) {
        Laps laps = buscarLaps(accessToken, activityId);
        if (laps.cotaEsgotada()) {
            throw new StravaRateLimitException("Limite de requisições Strava atingido");
        }
        return laps.splits();
    }

    /**
     * Laps e o estado da cota, sem lançar pela cota: o pull precisa gravar a atividade cujos laps já
     * pagou antes de parar pelo header (QA de fix-sync-cursor-data-loss).
     */
    private record Laps(List<StravaSplitDto> splits, boolean cotaEsgotada) {
    }

    private Laps buscarLaps(String accessToken, Long activityId) {
        ResponseEntity<List<StravaSplitDto>> response = stravaWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/activities/{id}/laps").build(activityId))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.value() == 429, StravaActivityServiceImpl::rateLimit)
                .toEntityList(StravaSplitDto.class)
                .block();

        if (response == null) {
            return new Laps(Collections.emptyList(), false);
        }
        boolean cotaEsgotada = cotaEsgotada(response.getHeaders());
        return new Laps(response.getBody() == null ? Collections.emptyList() : response.getBody(), cotaEsgotada);
    }

    public TreinoRealizado mapToTreinoRealizado(StravaActivityDto activity, Atleta atleta) {
        TreinoRealizado treino = new TreinoRealizado();
        mergeActivityIntoTreino(treino, activity, atleta);
        return treino;
    }

    public EtapaRealizada mapToEtapaRealizada(StravaSplitDto split) {
        EtapaRealizada etapa = new EtapaRealizada();
        etapa.setSplitIndex(split.lapIndex());
        etapa.setOrdem(split.lapIndex() != null ? split.lapIndex() : 1);
        etapa.setDescricao("Lap " + (split.lapIndex() != null ? split.lapIndex() : 1));
        etapa.setDuracao(Duration.ofSeconds(defaultInt(split.movingTime())));
        etapa.setDistanciaKm(toKm(split.distance(), 3));
        etapa.setFcMedia(toNullableInt(split.averageHeartrate()));
        etapa.setFcMax(toNullableInt(split.maxHeartrate()));
        etapa.setVelocidadeMedia(toKmh(split.averageSpeed(), 2));
        etapa.setCadenciaMedia(sanitizeCadence(convertCadence(split.averageCadence())));
        etapa.setPotenciaMedia(toNullableInt(split.averageWatts()));

        double elevationDiff = split.elevationDifference() == null ? 0.0 : split.elevationDifference();
        if (elevationDiff >= 0) {
            etapa.setElevacaoGanhoMetros((int) Math.round(elevationDiff));
            etapa.setElevacaoPerdaMetros(0);
        } else {
            etapa.setElevacaoGanhoMetros(0);
            etapa.setElevacaoPerdaMetros((int) Math.round(Math.abs(elevationDiff)));
        }
        return etapa;
    }

    /**
     * Pull agendado (fix-sync-cursor-data-loss D3). Lê e avança {@code pull_cursor}, exclusivo deste
     * caminho: push, webhook e sync manual gravam em {@code ultimaSincronizacao}, e usar aquele campo
     * como cursor fazia o pull pular janelas inteiras.
     *
     * <p>Não é {@code @Transactional}: cada atividade nova commita na própria transação, e o cursor
     * avança por fatia varrida inteira, então uma falha no meio não desfaz o que já entrou. Não lança
     * depois de carregar a integração: o acumulador devolve o que foi commitado mesmo quando a falha
     * é tardia (D5).</p>
     *
     * Idempotent: YES — reexecutar relista a janela; já importada custa zero requisição.
     * Side Effects: External API calls (listagem + laps por corrida nova), inserts de TreinoRealizado,
     *   pull_cursor e status da integração, descarte de atividades.
     * Tenant-aware: YES — TenantContext obrigatório (o scheduler seta).
     */
    @Override
    public PullResultado pullAgendado(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        IntegracaoExterna integracao = integracaoExternaRepository
                .findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, STRAVA, tenantId)
                .orElseThrow(() -> new IllegalStateException("Atleta sem integração Strava ativa"));
        PullAcumulador acc = new PullAcumulador();
        Instant fim = Instant.now();
        String erro = null;

        try {
            Instant cursor = integracao.getPullCursor();
            if (cursor == null) {
                // gravado ANTES de buscar: falhas seguidas não recalculam o horizonte (D1, CA4)
                cursor = fim.minus(Duration.ofDays(stravaProperties.getSyncDaysBack()));
                gravarCursor(integracao.getId(), tenantId, cursor);
            }
            String token = tokenOuNulo(atletaId);
            if (token == null) {
                acc.interrompido(ErroCategoriaPull.CREDENCIAL);
                erro = mensagemDoPull(ErroCategoriaPull.CREDENCIAL, null);
            } else {
                // mutável: o que for descartado nesta varredura não é tentado de novo na sobreposição
                // com a fatia seguinte
                Varredura varredura = new Varredura(integracao.getId(), atletaId, tenantId, token,
                        new HashSet<>(descarteWriter.descartadas(tenantId, atletaId, STRAVA)), true);
                varrer(varredura, cursor.minus(overlap()), fim, acc);
            }
        } catch (RuntimeException ex) {
            ErroCategoriaPull categoria = categoria(ex);
            log.warn("Pull Strava interrompido tenant={} atleta={} categoria={}: {}",
                    tenantId, atletaId, categoria, ex.getMessage());
            acc.interrompido(categoria);
            erro = mensagemDoPull(categoria, ex);
        }

        try {
            finalizarPullAgendado(integracao.getId(), atletaId, tenantId, acc, erro);
        } catch (RuntimeException ex) {
            log.warn("Falha ao gravar status do pull Strava tenant={} atleta={}: {}", tenantId, atletaId, ex.getMessage());
            acc.interrompido(ErroCategoriaPull.INESPERADO);
        }
        return acc.resultado();
    }

    private @Nullable String tokenOuNulo(UUID atletaId) {
        try {
            return stravaOAuthService.getValidToken(atletaId);
        } catch (RuntimeException ex) {
            // getValidToken já desativa a integração quando o refresh falha; aqui só não seguimos
            log.warn("Token Strava indisponível para atleta {}: {}", atletaId, ex.getMessage());
            return null;
        }
    }

    private void finalizarPullAgendado(UUID integracaoId, UUID atletaId, UUID tenantId, PullAcumulador acc,
                                       @Nullable String erro) {
        // releitura: status de uma integração desligada no meio do ciclo não é gravado
        Optional<IntegracaoExterna> atual = integracaoExternaRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, STRAVA, tenantId);
        if (atual.isEmpty() || !atual.get().isAtivo()) {
            log.info("Atleta {} desconectou o Strava durante o ciclo — status não gravado", atletaId);
            return;
        }
        PullResultado resultado = acc.resultado();
        // FALHA não finge sincronização: o "último sync" que o coach vê fica onde estava
        Instant ultima = resultado.resultado() == ResultadoPull.FALHA ? atual.get().getUltimaSincronizacao() : Instant.now();
        int contagem = defaultInt(atual.get().getSyncActivityCount()) + resultado.insercoes();
        integracaoExternaRepository.atualizarStatusSync(integracaoId, tenantId, ultima, contagem,
                erro != null ? erro : resultado.avisoDeIgnoradas());
    }

    @Transactional
    public void syncSingleActivityById(Atleta atleta, IntegracaoExterna integracao, Long activityId) {
        String accessToken = stravaOAuthService.getValidToken(atleta.getId());
        StravaActivityDto activity = stravaWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/activities/{id}").build(activityId))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(StravaActivityDto.class)
                .block();

        if (activity == null) {
            return;
        }

        TreinoRealizado treino = treinoRealizadoRepository
                .findByExternalIdAndAtletaId(String.valueOf(activity.id()), atleta.getId())
                .orElseGet(TreinoRealizado::new);
        LocalDate dataAntiga = treino.getDataTreino();

        mergeActivityIntoTreino(treino, activity, atleta);
        attachLaps(treino, accessToken, activity.id());
        // Dedup, tssCalculado, evento (só na primeira inserção — D4: `treino` já vem de um
        // find-or-new, então um re-sync é UPDATE e não publica) e carga do dia são
        // responsabilidade do seam único de ingestão (ingestao-treino-realizado).
        TreinoDedupHelper.SaveResult resultado =
                ingestaoTreinoRealizadoService.registrar(treino, String.valueOf(activity.id()));
        recalcularSeDataMudou(resultado.treino(), dataAntiga);
        integracao.setUltimaSincronizacao(Instant.now());
        integracaoExternaRepository.save(integracao);
    }

    /**
     * Achado do pre-mortem Codex (2026-08-22): quando o Strava reporta uma data diferente para
     * uma atividade já sincronizada (usuário editou o horário no Strava), {@code mergeActivityIntoTreino}
     * sobrescreve {@code dataTreino} antes de {@code registrar} rodar — e {@code registrar} só
     * recalcula a partir da data NOVA (CA6 exige o menor das duas, como {@code reprocessar} já
     * garante). Sem isto, o dia antigo ficaria com a carga do treino que já saiu de lá, sem nunca
     * ser recalculado. Reaproveita {@code reprocessar} (idempotente) em vez de reimplementar o
     * recálculo aqui.
     */
    private void recalcularSeDataMudou(TreinoRealizado treino, LocalDate dataAntiga) {
        if (dataAntiga != null && !dataAntiga.equals(treino.getDataTreino())) {
            ingestaoTreinoRealizadoService.reprocessar(treino.getId(), dataAntiga);
        }
    }

    /**
     * Sync manual (fix-sync-cursor-data-loss D3.1/D3.7): varre a partir de {@code pull_cursor − overlap}
     * mas NUNCA grava o cursor — só o pull agendado avança o que conta como "já confirmado". Também
     * não usa o descarte: uma tentativa manual pode recuperar uma atividade descartada.
     *
     * <p>Sem {@code @Transactional}: {@code getValidToken} renova e commita os tokens na própria
     * transação, e salvar a instância carregada aqui desfaria a renovação. Por isso o status é gravado
     * por {@code UPDATE} pontual (D0).</p>
     */
    @Override
    public StravaSyncResponseDto syncActivitiesForAtleta(UUID atletaId, UUID tenantId) {
        IntegracaoExterna integracao = integracaoExternaRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta sem integração Strava ativa"));

        if (isRecentlySynced(integracao)) {
            throw new DuplicateResourceException("Sincronização já em progresso. Aguarde conclusão.");
        }
        if (!integracao.isAtivo()) {
            throw new IllegalStateException("Atleta sem integração Strava ativa");
        }

        Instant fim = Instant.now();
        Instant cursor = integracao.getPullCursor() != null
                ? integracao.getPullCursor()
                : fim.minus(Duration.ofDays(stravaProperties.getSyncDaysBack()));
        PullAcumulador acc = new PullAcumulador();
        try {
            String token = stravaOAuthService.getValidToken(atletaId);
            varrer(new Varredura(integracao.getId(), atletaId, tenantId, token, Set.of(), false),
                    cursor.minus(overlap()), fim, acc);
        } catch (StravaRateLimitException e) {
            // hoje o rate limit era engolido e reportado como sucesso; agora vira resposta parcial
            acc.interrompido(ErroCategoriaPull.RATE_LIMIT);
        } catch (RuntimeException e) {
            // O setAtivo(false) que existia aqui era desfeito pelo rollback da própria transação ao
            // relançar — a falha manual nunca desativou de fato. Mantido esse efeito: registra e relança.
            // A mensagem é a segura (lastSyncError é exibido ao coach; a da exceção pode trazer URL, SQL
            // ou corpo de resposta), e o que já commitou antes da falha entra na contagem.
            boolean houveProgresso = acc.insercoes() > 0;
            integracaoExternaRepository.atualizarStatusSync(integracao.getId(), tenantId,
                    houveProgresso ? Instant.now() : integracao.getUltimaSincronizacao(),
                    houveProgresso ? acc.insercoes() : integracao.getSyncActivityCount(),
                    mensagemDoPull(categoria(e), e));
            throw e;
        }

        int imported = acc.insercoes();
        boolean completo = acc.resultado().resultado() == ResultadoPull.COMPLETO;
        String mensagem = completo
                ? imported + " atividades importadas com sucesso"
                : imported + " atividades importadas; sincronização parcial (limite do Strava) — o restante entra no próximo ciclo";
        integracaoExternaRepository.atualizarStatusSync(integracao.getId(), tenantId, Instant.now(), imported,
                completo ? null : mensagem);
        return new StravaSyncResponseDto(imported, mensagem);
    }

    @Transactional(readOnly = true)
    @Override
    public StravaSyncStatusDto getSyncStatus(UUID atletaId, UUID tenantId) {
        IntegracaoExterna integracao = integracaoExternaRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Integração Strava não encontrada"));

        boolean syncing = isSyncing(integracao);
        int imported = integracao.getSyncActivityCount() != null ? integracao.getSyncActivityCount() : 0;

        return new StravaSyncStatusDto(
                integracao.isAtivo(),
                syncing,
                imported,
                integracao.getLastSyncError(),
                integracao.getUltimaSincronizacao(),
                integracao.getExternalAthleteId()
        );
    }

    @Override
    @Transactional
    public TreinoRealizadoOutputDto enriquecerTreinoComStrava(UUID treinoRealizadoId, UUID tenantId) {
        TreinoRealizado treino = treinoRealizadoRepository
                .findByIdAndTenantId(treinoRealizadoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Treino não encontrado: " + treinoRealizadoId));

        if (treino.getFonteDados() != STRAVA || treino.getExternalId() == null) {
            throw new DomainRuleViolationException("Treino não é proveniente do Strava ou não tem ID externo");
        }

        Long activityId;
        try {
            activityId = Long.parseLong(treino.getExternalId());
        } catch (NumberFormatException e) {
            throw new DomainRuleViolationException("ID externo inválido: " + treino.getExternalId());
        }

        String accessToken = stravaOAuthService.getValidToken(treino.getAtleta().getId());

        log.info("Enriquecendo treino {} com detalhes da atividade Strava {}", treinoRealizadoId, activityId);

        StravaActivityDto detail = stravaWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/activities/{id}").build(activityId))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(StravaActivityDto.class)
                .block();

        if (detail == null) {
            throw new DomainRuleViolationException("Strava não retornou dados para a atividade " + activityId);
        }

        boolean rpePreenchido = false;
        if (detail.perceivedExertion() != null && treino.getPercepcaoEsforco() == null) {
            treino.setPercepcaoEsforco((int) Math.round(detail.perceivedExertion()));
            rpePreenchido = true;
            log.info("RPE preenchido automaticamente: {} para treino {}", treino.getPercepcaoEsforco(), treinoRealizadoId);
        }

        TreinoRealizado salvo = treinoRealizadoRepository.save(treino);

        if (rpePreenchido) {
            eventPublisher.publishEvent(new TreinoRegistradoEvent(salvo.getId(), salvo.getTenantId()));
        }

        return treinoMapper.toOutputDto(salvo);
    }

    /** O que difere entre o pull agendado e o manual numa varredura (D3.1). */
    private record Varredura(UUID integracaoId, UUID atletaId, UUID tenantId, String token,
                             Set<String> descartadas, boolean agendado) {
    }

    /**
     * Varre {@code [inicio, fim]} em fatias de {@link #FATIA} (D3.2). O cursor do pull agendado só
     * avança para o fim de uma fatia varrida inteira — nunca por instante de atividade, porque a API
     * não garante ordem (com {@code before} ela vem decrescente; só com {@code after}, crescente —
     * task 0.1). Uma interrupção deixa o cursor no fim da última fatia completa, e o ciclo seguinte
     * recomeça dali, com o que já entrou custando zero requisição.
     */
    private void varrer(Varredura v, Instant inicio, Instant fim, PullAcumulador acc) {
        Instant after = inicio;
        while (after.isBefore(fim)) {
            Instant before = after.plus(FATIA).isBefore(fim) ? after.plus(FATIA) : fim;
            varrerFatia(v, after, before, acc);
            if (v.agendado()) {
                gravarCursor(v.integracaoId(), v.tenantId(), before);
            }
            acc.avancou();
            if (!before.isBefore(fim)) {
                return;
            }
            after = before.minus(SOBREPOSICAO_FATIAS);
        }
    }

    /**
     * Pagina até a página ORIGINAL vir vazia. Parar na página filtrada (só corridas) encerrava a
     * varredura depois de 30 atividades seguidas de outra modalidade.
     */
    private void varrerFatia(Varredura v, Instant after, Instant before, PullAcumulador acc) {
        for (int page = 1; ; page++) {
            ActivitiesPage pagina = fetchActivitiesWithHeaders(v.token(), after, before, page);
            if (pagina.originais().isEmpty()) {
                return;
            }
            for (StravaActivityDto activity : pagina.corridas()) {
                if (activity.id() != null) {
                    importarSeNova(v, activity, acc);
                }
            }
            // página curta é a última (task 0.1): pedir a próxima só para ouvir "vazia" dobraria o custo
            // de cada fatia numa cota compartilhada entre todos os atletas
            if (pagina.originais().size() < POR_PAGINA) {
                return;
            }
        }
    }

    /**
     * Já importada é pulada sem custo (sem laps, sem merge): relistar não pode apagar o que o atleta
     * registrou nem contar como inserção (D3.3, CA7). Edição posterior no Strava chega pelo webhook
     * {@code update}.
     */
    private void importarSeNova(Varredura v, StravaActivityDto activity, PullAcumulador acc) {
        String externalId = String.valueOf(activity.id());
        if (v.descartadas().contains(externalId)
                || treinoRealizadoRepository.findByExternalIdAndAtletaId(externalId, v.atletaId()).isPresent()) {
            return;
        }
        try {
            // laps ANTES da transação: a chamada HTTP (até 10 s) não segura conexão do pool
            Laps laps = buscarLaps(v.token(), activity.id());
            // transação por atividade (D3.5): uma falha desfaz só esta, nunca as anteriores
            Boolean inserida = transactionOperations.execute(
                    status -> persistirNova(v, activity, externalId, laps.splits()));
            if (Boolean.TRUE.equals(inserida)) {
                acc.inserida();
            } else {
                // vencedor de uma corrida com o webhook: já está lá, mas não é inserção deste pull
                acc.avancou();
            }
            if (laps.cotaEsgotada()) {
                // só depois do commit: a atividade (e os laps já pagos) não se perdem por causa do header
                throw new StravaRateLimitException("Limite de requisições Strava atingido");
            }
        } catch (RuntimeException ex) {
            if (!v.agendado() || categoria(ex) != ErroCategoriaPull.INESPERADO) {
                throw ex;
            }
            // erro determinístico na atividade: retentada até a 3ª vez; depois descartada com
            // registro, para não travar a fatia (e o atleta) para sempre (D7, CA12)
            if (descarteWriter.registrarFalha(v.tenantId(), v.atletaId(), STRAVA, externalId)) {
                log.warn("Atividade Strava {} do atleta {} descartada após falhas recorrentes: {}",
                        externalId, v.atletaId(), ex.getClass().getSimpleName());
                v.descartadas().add(externalId);
                acc.ignorada(ErroCategoriaPull.INESPERADO);
                return;
            }
            throw ex;
        }
    }

    private boolean persistirNova(Varredura v, StravaActivityDto activity, String externalId,
                                  List<StravaSplitDto> splits) {
        // recarregado dentro da transação: mergeActivityIntoTreino lê atleta.getAssessoria() (lazy),
        // e uma instância de fora dela não teria sessão para inicializar
        Atleta atleta = atletaRepository.findByIdAndTenantId(v.atletaId(), v.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado"));
        TreinoRealizado treino = new TreinoRealizado();
        mergeActivityIntoTreino(treino, activity, atleta);
        attachLaps(treino, splits);
        return ingestaoTreinoRealizadoService.registrar(treino, externalId).inserted();
    }

    private void gravarCursor(UUID integracaoId, UUID tenantId, Instant cursor) {
        if (integracaoExternaRepository.atualizarPullCursor(integracaoId, tenantId, cursor) == 0) {
            // tenant divergente ou integração removida no meio do ciclo: nada foi gravado (CA9)
            log.warn("pull_cursor Strava não atualizado: integracao={} tenant={}", integracaoId, tenantId);
        }
    }

    private Duration overlap() {
        return Duration.ofDays(stravaProperties.getSyncOverlapDays());
    }

    /** Classificação do erro para o registro do pull e para decidir se conta tentativa (D3.6). */
    static ErroCategoriaPull categoria(Throwable ex) {
        if (PullAcumulador.falhaDeInfraestrutura(ex)) {
            // banco/transação: não é defeito da atividade — como INESPERADO contaria tentativa de descarte
            return ErroCategoriaPull.TRANSITORIO;
        }
        if (ex instanceof StravaRateLimitException) {
            return ErroCategoriaPull.RATE_LIMIT;
        }
        if (ex instanceof WebClientResponseException resposta) {
            int code = resposta.getStatusCode().value();
            if (code == 401 || code == 403) {
                return ErroCategoriaPull.CREDENCIAL;
            }
            if (code == 429) {
                return ErroCategoriaPull.RATE_LIMIT;
            }
            return code >= 500 ? ErroCategoriaPull.TRANSITORIO : ErroCategoriaPull.INESPERADO;
        }
        return ex instanceof WebClientRequestException ? ErroCategoriaPull.TRANSITORIO : ErroCategoriaPull.INESPERADO;
    }

    private String mensagemDoPull(ErroCategoriaPull categoria, @Nullable Exception ex) {
        // sem default: uma categoria nova tem de ganhar texto aqui em tempo de compilação
        return switch (categoria) {
            case RATE_LIMIT -> "Sincronização parcial (limite do Strava) — o restante entra no próximo ciclo";
            case CREDENCIAL -> "Credencial Strava inválida ou revogada — reconecte a integração";
            case TRANSITORIO -> "Falha temporária na sincronização — nova tentativa no próximo ciclo";
            case DADOS_INVALIDOS, CONFLITO, INESPERADO ->
                    "Falha inesperada no sync (" + (ex == null ? "desconhecida" : ex.getClass().getSimpleName()) + ")";
        };
    }

    /**
     * 429 vira {@link StravaRateLimitException} mesmo sem os headers de cota: a checagem por
     * {@code X-RateLimit-Remaining} só vê a cota zerada numa resposta de sucesso.
     */
    private static Mono<? extends Throwable> rateLimit(ClientResponse response) {
        return Mono.error(new StravaRateLimitException("Limite de requisições Strava atingido (HTTP 429)"));
    }

    private void attachLaps(TreinoRealizado treino, String accessToken, Long activityId) {
        attachLaps(treino, fetchActivityLaps(accessToken, activityId));
    }

    private void attachLaps(TreinoRealizado treino, List<StravaSplitDto> splits) {
        List<EtapaRealizada> etapas = new ArrayList<>();
        for (int i = 0; i < splits.size(); i++) {
            EtapaRealizada etapa = mapToEtapaRealizada(splits.get(i));
            if (etapa.getOrdem() == null) {
                etapa.setOrdem(i + 1);
            }
            etapa.setTreinoRealizado(treino);
            etapas.add(etapa);
        }
        treino.getEtapasRealizadas().clear();
        treino.getEtapasRealizadas().addAll(etapas);
    }

    private ActivitiesPage fetchActivitiesWithHeaders(String accessToken, Instant after, @Nullable Instant before,
                                                      int page) {
        ResponseEntity<List<StravaActivityDto>> response = stravaWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/athlete/activities")
                        .queryParam("after", after.getEpochSecond())
                        .queryParamIfPresent("before", Optional.ofNullable(before).map(Instant::getEpochSecond))
                        .queryParam("page", page)
                        .queryParam("per_page", POR_PAGINA)
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.value() == 429, StravaActivityServiceImpl::rateLimit)
                .toEntityList(StravaActivityDto.class)
                .block();

        if (response == null || response.getBody() == null) {
            return new ActivitiesPage(Collections.emptyList(), Collections.emptyList(), HttpHeaders.EMPTY);
        }
        checkRateLimit(response.getHeaders());

        List<StravaActivityDto> originais = response.getBody();
        List<StravaActivityDto> runs = originais.stream()
                .filter(activity -> RUN_SPORT_TYPES.contains(activity.sportType()))
                .toList();

        return new ActivitiesPage(originais, runs, response.getHeaders());
    }

    private void mergeActivityIntoTreino(TreinoRealizado treino, StravaActivityDto activity, Atleta atleta) {
        LocalDate treinoDate = parseActivityDate(activity.startDateLocal());
        if (treinoDate == null) {
            treinoDate = LocalDate.now();
        }

        treino.setAtleta(atleta);
        treino.setTenantId(atleta.getAssessoria().getId());
        treino.setDataTreino(treinoDate);
        treino.setDiaSemana(mapDiaSemana(treinoDate));
        treino.setTipoTreino(inferTipoTreino(activity, atleta));
        treino.setDescricao(activity.name());
        treino.setDuracaoMin(Duration.ofSeconds(defaultInt(activity.movingTime())));
        treino.setDistanciaKm(toKm(activity.distance(), 2));
        treino.setRitmoAlvo(null);
        treino.setFonteDados(STRAVA);
        treino.setStatus(TreinoExecucaoStatus.REALIZADO);
        treino.setExternalId(activity.id() != null ? String.valueOf(activity.id()) : null);
        treino.setStatusSincronizacao(StatusSincronizacao.SINCRONIZADO);
        treino.setSincronizadoEm(Instant.now());
        treino.setUrlExterno(activity.id() != null ? "https://www.strava.com/activities/" + activity.id() : null);
        treino.setMetadadosSincronizacao("{\"manual\":" + Boolean.TRUE.equals(activity.manual()) + "}");
        treino.setElapsedTimeSeg(activity.elapsedTime());
        treino.setSufferScore(activity.sufferScore());
        treino.setDeviceName(activity.deviceName());
        treino.setStatusSincronizacao(StatusSincronizacao.PENDENTE);
        treino.setGearName(activity.gear() != null ? activity.gear().name() : null);

        treino.setFcMedia(sanitizeHeartRate(toNullableInt(activity.averageHeartrate()), 40, 250));
        treino.setFcMax(sanitizeHeartRate(toNullableInt(activity.maxHeartrate()), 80, 250));
        treino.setVelocidadeMedia(toKmh(activity.averageSpeed(), 2) != null ? toKmh(activity.averageSpeed(), 2).doubleValue() : 0d);
        treino.setCadenciaMedia(sanitizeCadence(convertCadence(activity.averageCadence())));
        // O RPE registrado pelo atleta não é sobrescrito: o Strava quase sempre manda nulo, e o webhook
        // update passava a apagá-lo. Mesma regra de enriquecerTreinoComStrava (D3.4, CA7).
        if (treino.getPercepcaoEsforco() == null && activity.perceivedExertion() != null) {
            treino.setPercepcaoEsforco((int) Math.round(activity.perceivedExertion()));
        }
        treino.setPaceMedia(calculatePace(activity.movingTime(), activity.distance()));
        treino.setElevacaoGanhoMetros(activity.totalElevationGain() != null ? (int) Math.round(activity.totalElevationGain()) : null);
        treino.setElevacaoPerdaMetros(null);
        treino.setCriadoPor("STRAVA");
    }

    private void checkRateLimit(HttpHeaders headers) {
        if (cotaEsgotada(headers)) {
            throw new StravaRateLimitException("Limite de requisições Strava atingido");
        }
    }

    /** Lê a cota dos headers de uma resposta de sucesso; {@code true} se alguma janela zerou. */
    private boolean cotaEsgotada(HttpHeaders headers) {
        String remaining = headers.getFirst("X-RateLimit-Remaining");
        String usage    = headers.getFirst("X-RateLimit-Usage");
        String limit    = headers.getFirst("X-RateLimit-Limit");

        // Loga consumo real para monitoramento antes de habilitar detail fetch por atividade.
        // Formato Strava: "15min,daily"  ex.: Usage=12,345  Limit=100,1000  Remaining=88,655
        if (usage != null && limit != null) {
            log.info("strava_rate_limit usage={} limit={} remaining={}", usage, limit,
                    remaining != null ? remaining : "n/a");
        }

        if (remaining == null || remaining.isBlank()) {
            return false;
        }

        int minRemaining = Integer.MAX_VALUE;
        String[] chunks = remaining.split(",");
        for (String chunk : chunks) {
            try {
                minRemaining = Math.min(minRemaining, Integer.parseInt(chunk.trim()));
            } catch (NumberFormatException ignored) {
                // ignora partes não parseáveis
            }
        }

        return minRemaining == 0;
    }

    private LocalDate parseActivityDate(String startDateLocal) {
        Instant instant = parseActivityInstant(startDateLocal);
        return instant != null ? instant.atZone(ZoneId.systemDefault()).toLocalDate() : null;
    }

    private Instant parseActivityInstant(String startDateLocal) {
        if (startDateLocal == null || startDateLocal.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(startDateLocal);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(startDateLocal).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDateTime.parse(startDateLocal).atZone(ZoneId.systemDefault()).toInstant();
                } catch (DateTimeParseException ignoredThird) {
                    return null;
                }
            }
        }
    }

    private TipoTreino inferTipoTreino(StravaActivityDto activity, Atleta atleta) {
        String sportType = activity.sportType();
        Integer workoutType = activity.workoutType();
        Integer fcMedia = toNullableInt(activity.averageHeartrate());

        if ("Run".equalsIgnoreCase(sportType) && Integer.valueOf(1).equals(workoutType)) {
            return TipoTreino.PROVA;
        }
        if ("Run".equalsIgnoreCase(sportType) && Integer.valueOf(2).equals(workoutType)) {
            return TipoTreino.LONGO;
        }
        if ("Run".equalsIgnoreCase(sportType) && Integer.valueOf(3).equals(workoutType)) {
            Integer limiar = atleta.getFcLimiarCalculada();
            if (limiar != null && fcMedia != null && fcMedia >= limiar) {
                return TipoTreino.TEMPO_RUN;
            }
            return TipoTreino.INTERVALADO;
        }
        if ("TrailRun".equalsIgnoreCase(sportType)) {
            if (activity.totalElevationGain() != null && activity.totalElevationGain() > 100d) {
                return TipoTreino.SUBIDA;
            }
            return TipoTreino.LONGO;
        }
        if ("VirtualRun".equalsIgnoreCase(sportType)) {
            return TipoTreino.CONTINUO;
        }

        int movingTime = defaultInt(activity.movingTime());
        if (movingTime >= 5400) {
            return TipoTreino.LONGO;
        }
        if (fcMedia != null && atleta.getFcLimiarCalculada() != null && fcMedia >= atleta.getFcLimiarCalculada()) {
            return TipoTreino.TEMPO_RUN;
        }
        if (movingTime <= 1800) {
            return TipoTreino.FACIL;
        }
        return TipoTreino.CONTINUO;
    }

    private DiaSemana mapDiaSemana(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> DiaSemana.SEGUNDA;
            case TUESDAY -> DiaSemana.TERCA;
            case WEDNESDAY -> DiaSemana.QUARTA;
            case THURSDAY -> DiaSemana.QUINTA;
            case FRIDAY -> DiaSemana.SEXTA;
            case SATURDAY -> DiaSemana.SABADO;
            case SUNDAY -> DiaSemana.DOMINGO;
        };
    }

    private Duration calculatePace(Integer movingTime, Double distanceMeters) {
        if (movingTime == null || movingTime <= 0 || distanceMeters == null || distanceMeters <= 0d) {
            return Duration.ZERO;
        }
        double distanceKm = distanceMeters / 1000d;
        long secondsPerKm = Math.round(movingTime / distanceKm);
        return Duration.ofSeconds(Math.max(secondsPerKm, 0));
    }

    private BigDecimal toKm(Double meters, int scale) {
        if (meters == null) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(meters / 1000d).setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal toKmh(Double metersPerSecond, int scale) {
        if (metersPerSecond == null) {
            return null;
        }
        return BigDecimal.valueOf(metersPerSecond * 3.6d).setScale(scale, RoundingMode.HALF_UP);
    }

    private Integer toNullableInt(Double value) {
        if (value == null) {
            return null;
        }
        return (int) Math.round(value);
    }

    private int defaultInt(Double value) {
        return value == null ? 0 : (int) Math.round(value);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int convertCadence(Double averageCadence) {
        if (averageCadence == null) {
            return 0;
        }
        return (int) Math.round(averageCadence * 2d);
    }

    private Integer sanitizeCadence(int cadence) {
        if (cadence < 60 || cadence > 200) {
            return null;
        }
        return cadence;
    }

    private Integer sanitizeHeartRate(Integer hr, int min, int max) {
        if (hr == null || hr < min || hr > max) {
            return null;
        }
        return hr;
    }

    private boolean isSyncing(IntegracaoExterna integracao) {
        if (integracao.getUltimaSincronizacao() == null) {
            return false;
        }

        Instant oneMinuteAgo = Instant.now().minus(Duration.ofMinutes(1));
        return oneMinuteAgo.isBefore(integracao.getUltimaSincronizacao()) && !integracao.isAtivo();
    }

    private boolean isRecentlySynced(IntegracaoExterna integracao) {
        if (integracao.getUltimaSincronizacao() == null) {
            return false;
        }

        Instant thirtySecondsAgo = Instant.now().minus(Duration.ofSeconds(30));
        return thirtySecondsAgo.isBefore(integracao.getUltimaSincronizacao());
    }


    /**
     * {@code originais} decide o fim da paginação; {@code corridas} é o que se importa. Misturar os
     * dois encerrava a varredura numa página só de outras modalidades.
     */
    private record ActivitiesPage(List<StravaActivityDto> originais, List<StravaActivityDto> corridas,
                                  HttpHeaders headers) {
    }

}
