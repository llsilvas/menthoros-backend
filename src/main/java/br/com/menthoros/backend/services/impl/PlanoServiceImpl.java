package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.planner.InjuryRiskLevel;
import br.com.menthoros.backend.domain.planner.OnboardingContext;
import br.com.menthoros.backend.domain.planner.ReviewMode;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.dto.input.DadosPlanoDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.output.PadroesTreino;
import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.*;
import br.com.menthoros.backend.enums.*;
import br.com.menthoros.backend.events.PlanoDeletadoEvent;
import br.com.menthoros.backend.events.RevisaoConsumidaEvent;
import br.com.menthoros.backend.services.prompt.WeeklyReviewPromptProvider;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.PlanoJaExistenteException;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.mapper.AtletaMapper;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.repository.*;
import br.com.menthoros.backend.services.*;
import br.com.menthoros.backend.services.helper.PlannerShadowService;
import br.com.menthoros.backend.services.helper.RedistribuicaoTreinoHelper;
import br.com.menthoros.backend.services.helper.RegraGeracaoTreino;
import br.com.menthoros.backend.services.onboarding.OnboardingService;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.util.Utils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class PlanoServiceImpl implements PlanoService {

    // Constantes de configuração de volume e progressão
    private static final BigDecimal FATOR_INCREMENTO_VOLUME_PLANEJADO = BigDecimal.valueOf(1.10); // 10% de incremento
    private static final BigDecimal PESO_VOLUME_HISTORICO = BigDecimal.valueOf(0.7); // 70% peso histórico
    private static final BigDecimal PESO_VOLUME_ATUAL = BigDecimal.valueOf(0.3); // 30% peso atual
    private static final int DIAS_POR_SEMANA = 6;
    private static final int JANELA_HISTORICO_DIAS = 42;

    private final IaService iaService;
    private final ProgressaoTreinoService progressaoTreinoService;
    private final AtletaRepository atletaRepository;
    private final AtletaMapper atletaMapper;
    private final TreinoMapper treinoMapper;
    private final PlanoSemanalMapper planoSemanalMapper;
    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final PlanoMetadadosService planoMetadadosService;
    private final TsbService tsbService;

    private final PlanoSemanalRepository planoSemanalRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final RedistribuicaoTreinoHelper redistribuicaoHelper;
    private final RegraGeracaoTreino regraGeracaoTreino;
    private final MetricasAlertaService metricasAlertaService;
    private final MetricasAgregadasService metricasAgregadasService;

    private final ProvaRepository provaRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final WeeklyReviewPromptProvider weeklyReviewPromptProvider;
    private final PlannerShadowService plannerShadowService;
    private final OnboardingService onboardingService;
    private final PlanoReviewService planoReviewService;

    @Value("${onboarding.auto-approve.enabled:true}")
    private boolean autoApproveEnabled;

    @Value("${onboarding.migrate-existing.enabled:true}")
    private boolean migrateExistingEnabled;

    /**
     * Gera um plano de treino semanal personalizado para um atleta usando IA.
     *
     * <p>Este método coordena todo o processo de geração de plano, incluindo:
     * <ul>
     *   <li>Preparação dos dados do atleta e histórico de treinos</li>
     *   <li>Geração do plano através do serviço de IA</li>
     *   <li>Redistribuição de treinos conforme o modo de geração</li>
     *   <li>Persistência do plano completo no banco de dados</li>
     * </ul>
     *
     * <p>O modo de geração determina o comportamento da redistribuição:
     * <ul>
     *   <li>{@link ModoGeracaoPlano#SEMANA_ATUAL}: Redistribui treinos considerando dias já passados</li>
     *   <li>{@link ModoGeracaoPlano#PROXIMA_SEMANA}: Usa os treinos gerados pela LLM diretamente</li>
     * </ul>
     *
     * @param atletaId ID do atleta para o qual o plano será gerado
     * @param modoGeracao modo que determina como os treinos serão distribuídos na semana
     * @return o plano semanal gerado e persistido
     * @throws DomainNotFoundException se o atleta não for encontrado
     * @throws LLMException se houver falha na geração do plano pela IA ou se o plano retornado for inválido
     * @throws DomainRuleViolationException se não for possível gerar treinos (ex: sem dias disponíveis)
     * @see ModoGeracaoPlano
     * @see PlanoSemanal
     */
    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId().
     */
    @Override
    @Transactional
    public boolean existePlanoParaSemana(UUID atletaId, ModoGeracaoPlano modoGeracao) {
        LocalDate semanaAlvo = calcularSemanaInicio(atletaId, LocalDate.now(), modoGeracao);
        return planoSemanalRepository.existePlanoAtivoNaSemana(
                atletaId, semanaAlvo, TenantContext.getRequiredTenantId());
    }

    @Transactional
    @Override
    public PlanoSemanal gerarPlanoTreino(UUID atletaId, ModoGeracaoPlano modoGeracao) {
        // Validação de entrada
        validarParametrosEntrada(atletaId, modoGeracao);

        DadosPlanoDto dadosPlano = getPreparaDadosPlano(atletaId);
        Hibernate.initialize(dadosPlano.atleta().getProvas()); // evita LazyInitializationException

        DecisaoProgressao decisaoProgressao = calcularDecisaoProgressao(atletaId);

        try {
            // Revisao da semana anterior resolvida UMA vez por geracao (add-weekly-review-llm-focus,
            // D9/D11): o mesmo objeto alimenta o prompt e o vinculo gravado no plano. Resolver nos dois
            // pontos abriria uma janela em que uma revisao nova, persistida entre as duas leituras, faria
            // o LLM ver uma revisao e o plano registrar outra.
            // A semana do plano tambem e calculada uma unica vez e repassada: recalcula-la depois da
            // chamada ao LLM (que pode levar segundos e, em lote, esperar no semaforo) usaria um
            // LocalDate.now() diferente, fazendo a janela D11 da revisao divergir da semana persistida.
            // Dentro do try: uma falha de repositorio aqui deve virar LLMException como qualquer
            // outra falha de geracao, e nao propagar crua para o controller.
            LocalDate semanaInicio = calcularSemanaInicio(atletaId, LocalDate.now(), modoGeracao);
            Optional<RevisaoSemanal> revisaoConsumida = weeklyReviewPromptProvider.resolverParaGeracao(
                    atletaId, TenantContext.getRequiredTenantId(), semanaInicio);

            PlanoSemanalLlmDto planoDto = gerarPlanoSemanal(dadosPlano, modoGeracao, decisaoProgressao,
                    revisaoConsumida.orElse(null), semanaInicio);

            if (planoDto == null) {
                throw new LLMException("Falha ao gerar plano: IA retornou resposta nula. Tente novamente.");
            }

            return persistirPlanoCompleto(planoDto, dadosPlano, modoGeracao, decisaoProgressao,
                    revisaoConsumida, semanaInicio);
        } catch (LLMException | DomainRuleViolationException e) {
            // Re-lança exceções de domínio sem modificar
            log.error("Erro de domínio ao gerar plano para atleta {}: {}", atletaId, e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            // Converte exceções de validação em exceções de domínio
            log.error("Erro de validação ao gerar plano para atleta {}: {}", atletaId, e.getMessage());
            throw new LLMException("Erro ao gerar plano semanal: " + e.getMessage(), e);
        } catch (Exception e) {
            // Captura exceções inesperadas
            log.error("Erro inesperado ao gerar plano para atleta {}", atletaId, e);
            throw new LLMException("Erro inesperado ao gerar plano. Por favor, tente novamente.", e);
        }
    }

    /**
     * Persiste um plano completo gerado pela LLM aplicando o princípio SRP.
     *
     * <p>Este método orquestra a persistência do plano delegando cada responsabilidade
     * a métodos especializados, seguindo o Single Responsibility Principle:
     * <ol>
     *   <li><strong>Cálculo de período:</strong> Define início e fim da semana do plano</li>
     *   <li><strong>Obtenção de treinos:</strong> Redistribui conforme o modo de geração</li>
     *   <li><strong>Preparação de metadados:</strong> Atualiza TSB, ramp rate e progressão</li>
     *   <li><strong>Criação de entidades:</strong> Monta estrutura completa do plano</li>
     *   <li><strong>Persistência:</strong> Salva plano e metadados no banco de dados</li>
     * </ol>
     *
     * @param planoDto DTO gerado pela LLM contendo treinos e métricas planejadas
     * @param dadosPlano dados consolidados do atleta, histórico e metadados
     * @param modoGeracao modo que determina a estratégia de redistribuição de treinos
     * @param decisaoProgressao decisão de progressão já calculada — repassada ao shadow do
     *        {@code PlannerEngine} (deterministic-planner-engine), que não recalcula a direção
     * @param revisaoConsumida revisão da semana anterior resolvida em {@code gerarPlanoTreino} — a
     *        MESMA que alimentou o prompt, para o vínculo gravado nunca divergir do que o LLM viu
     * @param semanaInicio início da semana do plano, também resolvido em {@code gerarPlanoTreino}
     * @return plano semanal persistido com todas as associações carregadas
     * @throws DomainRuleViolationException se não for possível gerar treinos após redistribuição
     */
    private PlanoSemanal persistirPlanoCompleto(PlanoSemanalLlmDto planoDto, DadosPlanoDto dadosPlano,
                                                 ModoGeracaoPlano modoGeracao, DecisaoProgressao decisaoProgressao,
                                                 Optional<RevisaoSemanal> revisaoConsumida,
                                                 LocalDate semanaInicio) {
        Atleta atleta = dadosPlano.atleta();

        log.info("Iniciando persistência de plano completo para atleta {}", atleta.getId());

        // 1. Período do plano — a semana de início vem resolvida do chamador (ver gerarPlanoTreino)
        PeriodoPlano periodo = new PeriodoPlano(semanaInicio);

        // ** Adição da verificação de duplicidade **
        // Duplicidade: um plano REJEITADO não bloqueia (casa com o índice único parcial da V52).
        if (planoSemanalRepository.existePlanoAtivoNaSemana(atleta.getId(), semanaInicio, TenantContext.getRequiredTenantId())) {
            log.debug("Tentativa de gerar plano duplicado para atleta {} na semana de início {}.", atleta.getId(), semanaInicio);
            throw new PlanoJaExistenteException(
                    "Já existe um plano semanal ativo para o atleta " + atleta.getId() +
                            " iniciando em " + semanaInicio + ". Não é possível gerar planos duplicados."
            );
        }
        // ** Fim da adição **

        log.info("Período calculado: {} a {} (Modo: {}, {} treinos no plano LLM)",
                periodo.inicio(), periodo.fim(), modoGeracao, planoDto.treinosPlanejados().size());

        // 2. Obter treinos (com ou sem redistribuição conforme o modo)
        DiaSemana diaPrioritarioLongo = inferirDiaPrioritarioLongo(dadosPlano);
        List<TreinoPlanejadoLlmDto> treinos = obterTreinosParaPlano(
                planoDto.treinosPlanejados(),
                atleta,
                periodo,
                modoGeracao,
                diaPrioritarioLongo
        );

        // 3. Preparar metadados
        PlanoMetaDados metaDados = prepararMetadados(planoDto, dadosPlano);

        // 4. Criar plano completo com treinos
        PlanoSemanal plano = criarPlanoComTreinos(planoDto, atleta, periodo, metaDados, treinos);

        // 4.5. OnboardingContext (athlete-onboarding-baseline) + Shadow do PlannerEngine
        // (deterministic-planner-engine, design.md Decisao 10): roda apos a geracao legada,
        // nunca altera plano/prompt/persistencia (CA12); qualquer falha e isolada internamente
        // pelo proprio PlannerShadowService (CA11). Mutacao in-memory dos campos planner_* em
        // `plano`, persistidos junto no passo 5.
        // batch=false: este call site nao distingue chamadas interativas de lote (BatchPlanProcessor
        // chama o mesmo gerarPlanoTreino publico) — tag de metrica aproximada, nao uma limitacao funcional.
        UUID tenantId = TenantContext.getRequiredTenantId();
        Optional<OnboardingContext> onboardingContext = resolverOnboardingContext(atleta.getId(), tenantId);
        Optional<WeekPlanSkeleton> weekPlanSkeleton = plannerShadowService.aplicarShadow(
                plano, planoDto, dadosPlano, decisaoProgressao, periodo.inicio(), false, onboardingContext);

        // 4.6. Auto-approve Cenario A (CA5, design.md Decisao 7): so dispara com EXCEPTION_ONLY
        // E !requiresCoachReview E risco != HIGH_RISK — a dupla checagem com o risco e defesa em
        // profundidade, redundante por design (HIGH_RISK ja deveria forcar requiresCoachReview).
        onboardingContext.ifPresent(context -> aplicarAutoApproveSeElegivel(plano, context, weekPlanSkeleton, tenantId));

        // 4.7. Avaliacao semanal de calibracao (retrofit 10.4, sessao de grilling 2026-07-21):
        // so avalia quando o atleta esta de fato em TrainingPhase.CALIBRATION (AthleteBaselineState.
        // calibracaoIniciadaEm preenchido, setado em montarContexto na 1a vez que confidenceTier != A).
        // Precisa do InjuryRiskLevel calculado pelo shadow (4.5) — sem skeleton, nao ha risco para
        // avaliar nesta semana; tenta de novo no proximo ciclo.
        weekPlanSkeleton.ifPresent(skeleton -> onboardingService.avaliarCalibracaoSeAplicavel(
                atleta.getId(), tenantId, semanaInicio.minusDays(1), skeleton.injuryRisk().level()));

        // 4.8. Revisao da semana anterior consumida como insumo (add-weekly-review-llm-focus,
        // D9/D11): resolvida UMA vez em gerarPlanoTreino e recebida aqui por parametro, para o
        // vinculo gravado nunca divergir da revisao que o LLM realmente viu. Sem revisao consumivel
        // (ausente, fora da janela ou injecao desligada) o plano nasce NOT_CONSUMED — ausencia de
        // dado, nao julgamento negativo do coach. Mutacao in-memory, persistida no passo 5.
        registrarRevisaoConsumida(plano, revisaoConsumida);

        // 5. Persistir e retornar
        PlanoSemanal salvo = salvarPlanoCompleto(plano, metaDados);
        publicarRevisaoConsumida(salvo, tenantId);
        return salvo;
    }

    /**
     * Grava no plano o vinculo com a revisao consumida e o desfecho inicial, e publica o
     * {@link RevisaoConsumidaEvent} (proxy de adocao).
     *
     * Idempotent: YES — mutacao in-memory deterministica sobre o mesmo plano.
     * Side Effects: publicacao de evento (a persistencia acontece no salvarPlanoCompleto).
     * Tenant-aware: YES
     */
    private void registrarRevisaoConsumida(PlanoSemanal plano, Optional<RevisaoSemanal> revisao) {
        if (revisao.isEmpty()) {
            plano.setConsumedReviewOutcome(ConsumedReviewOutcome.NOT_CONSUMED);
            return;
        }
        plano.setConsumedReview(revisao.get());
        plano.setConsumedReviewOutcome(ConsumedReviewOutcome.PENDING);
    }

    /**
     * Publica o {@link RevisaoConsumidaEvent} DEPOIS da persistencia — o {@code id} do plano e
     * {@code @GeneratedValue}, entao publicar antes do save mandaria {@code planoId} sempre nulo e
     * o consumidor do proxy de adocao receberia o vinculo vazio.
     *
     * Idempotent: NAO — publica um evento por chamada.
     * Side Effects: publicacao de evento.
     * Tenant-aware: YES
     */
    private void publicarRevisaoConsumida(PlanoSemanal planoSalvo, UUID tenantId) {
        if (planoSalvo.getConsumedReview() == null) {
            return;
        }
        eventPublisher.publishEvent(new RevisaoConsumidaEvent(
                tenantId,
                planoSalvo.getAtleta().getId(),
                planoSalvo.getSemanaInicio(),
                planoSalvo.getConsumedReview().getId(),
                planoSalvo.getId()));
    }

    /**
     * Resolve o {@code OnboardingContext} do atleta, respeitando o kill-switch
     * {@code onboarding.migrate-existing.enabled} (tasks.md 5.7, design.md Decisao 6): atletas
     * que JA possuem um {@code AthleteBaselineState} (ja onboarded ou ja migrados) sempre tem
     * o contexto recalculado — necessario para o re-baseline da calibracao (CA3). Atletas
     * legados SEM snapshot ainda so sao migrados automaticamente quando a flag esta habilitada;
     * desabilitada, o atleta legado continua sem {@code OnboardingContext} (comportamento
     * identico ao anterior a esta change) ate que a flag volte a ligar ou o atleta conclua o
     * onboarding manualmente.
     *
     * Idempotent: NAO — pode disparar o calculo/persistencia do baseline (ver
     * {@code OnboardingService.montarContexto}).
     * Side Effects: ver montarContexto quando o contexto e calculado; NENHUM caso contrario.
     * Tenant-aware: SIM — tenantId explicito.
     */
    private Optional<OnboardingContext> resolverOnboardingContext(UUID atletaId, UUID tenantId) {
        boolean atletaLegado = !onboardingService.possuiBaseline(atletaId, tenantId);
        if (atletaLegado && !migrateExistingEnabled) {
            return Optional.empty();
        }
        return Optional.of(onboardingService.montarContexto(atletaId, tenantId));
    }

    /**
     * Auto-aprova o plano quando o atleta esta em Cenario A (alta confianca, design.md
     * Decisao 7) e o ciclo corrente nao apresenta risco. Kill-switch via
     * {@code onboarding.auto-approve.enabled} (default true); se qualquer condicao falhar,
     * o plano permanece {@code AGUARDANDO_REVISAO} (comportamento padrao).
     *
     * Idempotent: NAO — pode transicionar o plano para APROVADO e publicar evento.
     * Side Effects: Database update (via aprovarTransicao) + PlanoAprovadoEvent, quando elegivel.
     * Tenant-aware: SIM — tenantId explicito.
     */
    private void aplicarAutoApproveSeElegivel(PlanoSemanal plano, OnboardingContext onboardingContext,
                                               Optional<WeekPlanSkeleton> weekPlanSkeleton, UUID tenantId) {
        if (!autoApproveEnabled) {
            return;
        }
        if (onboardingContext.planningPolicy().reviewMode() != ReviewMode.EXCEPTION_ONLY) {
            return;
        }
        if (weekPlanSkeleton.isEmpty()) {
            return;
        }
        WeekPlanSkeleton skeleton = weekPlanSkeleton.get();
        if (skeleton.requiresCoachReview() || skeleton.injuryRisk().level() == InjuryRiskLevel.HIGH_RISK) {
            return;
        }
        planoReviewService.aprovarTransicao(plano, tenantId, OrigemAprovacao.AUTO_CONFIANCA_ALTA);
    }

    /**
     * Record que encapsula o período (início e fim) de um plano semanal.
     *
     * <p>Facilita a passagem desses dados entre métodos e melhora a legibilidade do código.
     * O construtor secundário calcula automaticamente a data fim somando 6 dias à data de início.
     *
     * @param inicio data de início do plano semanal (tipicamente uma segunda-feira)
     * @param fim data de fim do plano semanal (tipicamente um domingo)
     */
    private record PeriodoPlano(LocalDate inicio, LocalDate fim) {

        /**
         * Construtor de conveniência que calcula automaticamente a data fim.
         *
         * @param inicio data de início do plano semanal
         */
        PeriodoPlano(LocalDate inicio) {
            this(inicio, inicio.plusDays(DIAS_POR_SEMANA));
        }
    }
    /**
     * Calcula a data de início da próxima semana de treino para um atleta.
     *
     * <p>Se o atleta já possui planos anteriores, a data de início será uma semana após
     * o início do último plano. Caso contrário, será a segunda-feira da semana atual ou anterior.
     *
     * @param atletaId identificador único do atleta
     * @param hoje data atual para referência de cálculo
     * @param modoGeracao modo de geração do plano (não utilizado atualmente)
     * @return data de início calculada para o novo plano
     */
    private LocalDate calcularSemanaInicio(UUID atletaId, LocalDate hoje, ModoGeracaoPlano modoGeracao) {
        LocalDate segundaFeiraSemanaAtual = hoje.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate segundaFeiraSemanaProxima = hoje.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));

        if (ModoGeracaoPlano.SEMANA_ATUAL.equals(modoGeracao)) {
            return segundaFeiraSemanaAtual;
        }

        return planoSemanalRepository
                .findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)
                .map(p -> {
                    LocalDate proximaDataPlano = p.getSemanaInicio().plusWeeks(1);
                    // Se o último plano já passou, usa a próxima segunda-feira
                    return proximaDataPlano.isBefore(segundaFeiraSemanaProxima)
                            ? segundaFeiraSemanaProxima
                            : proximaDataPlano;
                })
                .orElse(segundaFeiraSemanaProxima);
    }

    /**
     * Obtém a lista de treinos para o plano, aplicando redistribuição se necessário.
     * Para SEMANA_ATUAL, redistribui treinos considerando dias já passados.
     * Para outros modos, usa os treinos gerados pela LLM diretamente.
     */
    private List<TreinoPlanejadoLlmDto> obterTreinosParaPlano(
            List<TreinoPlanejadoLlmDto> treinosLlm,
            Atleta atleta,
            PeriodoPlano periodo,
            ModoGeracaoPlano modoGeracao,
            DiaSemana diaPrioritarioLongo) {

        List<TreinoPlanejadoLlmDto> treinos = ModoGeracaoPlano.SEMANA_ATUAL.equals(modoGeracao)
                ? redistribuicaoHelper.redistribuirTreinos(
                        treinosLlm,
                        atleta.getDiasDisponiveis(),
                        LocalDate.now(),
                        periodo.inicio(),
                        periodo.fim(),
                        modoGeracao,
                        diaPrioritarioLongo
                )
                : treinosLlm;

        validarTreinosGerados(treinos);
        return treinos;
    }

    private DiaSemana inferirDiaPrioritarioLongo(DadosPlanoDto dadosPlano) {
        List<TreinoRealizadoOutputDto> ultimosTreinos = dadosPlano.ultimosTreinos();
        if (ultimosTreinos != null && !ultimosTreinos.isEmpty()) {
            Map<DiaSemana, Long> frequenciaPorDia = ultimosTreinos.stream()
                    .filter(t -> br.com.menthoros.backend.enums.TipoTreino.LONGO.equals(t.tipoTreino()))
                    .map(TreinoRealizadoOutputDto::diaSemana)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.groupingBy(
                            dia -> dia,
                            java.util.stream.Collectors.counting()
                    ));

            if (!frequenciaPorDia.isEmpty()) {
                List<Map.Entry<DiaSemana, Long>> ranking = frequenciaPorDia.entrySet().stream()
                        .sorted(Map.Entry.<DiaSemana, Long>comparingByValue().reversed())
                        .toList();

                Map.Entry<DiaSemana, Long> primeiro = ranking.getFirst();
                long segundoLugar = ranking.size() > 1 ? ranking.get(1).getValue() : 0;

                if (primeiro.getValue() >= 2 && primeiro.getValue() > segundoLugar) {
                    log.info("Dia prioritário do LONGO inferido pelo histórico: {} ({} ocorrências recentes)",
                            primeiro.getKey(), primeiro.getValue());
                    return primeiro.getKey();
                }
            }
        }

        DiaSemana fallback = dadosPlano.atleta().getDiaPreferidoLongo();
        if (fallback != null) {
            log.info("Usando diaPreferidoLongo configurado como fallback para LONGO: {}", fallback);
        }
        return fallback;
    }

    /**
     * Valida se há treinos após redistribuição ou geração.
     * Lança exceção se a lista estiver vazia.
     */
    private void validarTreinosGerados(List<TreinoPlanejadoLlmDto> treinos) {
        if (treinos.isEmpty()) {
            throw new DomainRuleViolationException(
                    """
                    Não foi possível gerar treinos para a semana selecionada.
                        Motivos possíveis:
                        - Geração no meio da semana sem dias disponíveis
                        - Todos os treinos da LLM são incompatíveis (LONGO/INTERVALADO)
                        Sugestão: Gere para a próxima semana.
                    """
            );
        }
    }

    /**
     * Cria a entidade PlanoSemanal completa com treinos planejados e volumes calculados.
     */
    private PlanoSemanal criarPlanoComTreinos(
            PlanoSemanalLlmDto planoDto,
            Atleta atleta,
            PeriodoPlano periodo,
            PlanoMetaDados metaDados,
            List<TreinoPlanejadoLlmDto> treinosDto) {

        PlanoSemanal plano = criarPlanoEntity(planoDto, atleta, periodo.inicio(), periodo.fim(), metaDados);

        List<TreinoPlanejado> treinosPlanejados = converterTreinos(treinosDto, plano, periodo.inicio());

        BigDecimal volumePlanejado = calcularVolumeTotalPlanejado(treinosPlanejados);

        plano.setVolumePlanejadoKm(volumePlanejado);
        plano.setVolumeAlvoKm(volumePlanejado);
        plano.setTreinosPlanejados(treinosPlanejados);

        return plano;
    }

    /**
     * Converte lista de DTOs de treinos em entidades TreinoPlanejado.
     */
    private List<TreinoPlanejado> converterTreinos(
            List<TreinoPlanejadoLlmDto> treinosDto,
            PlanoSemanal plano,
            LocalDate semanaInicio) {

        return treinosDto.stream()
                .map(dto -> converterTreino(dto, plano, semanaInicio))
                .toList();
    }

    /**
     * Converte um DTO de treino em entidade TreinoPlanejado,
     * calculando a data do treino com base no dia da semana.
     */
    private TreinoPlanejado converterTreino(
            TreinoPlanejadoLlmDto dto,
            PlanoSemanal plano,
            LocalDate semanaInicio) {

        TreinoPlanejado treino = treinoMapper.toEntity(dto);
        treino.setPlanoSemanal(plano);

        DiaSemana diaSemana = DiaSemana.valueOf(dto.diaSemana());
        LocalDate dataTreino = calcularDataTreino(semanaInicio, diaSemana);
        treino.setDataTreino(dataTreino);

        return treino;
    }

    /**
     * Calcula o volume total planejado somando as distâncias de todos os treinos.
     */
    private BigDecimal calcularVolumeTotalPlanejado(List<TreinoPlanejado> treinos) {
        double volume = treinos.stream()
                .mapToDouble(this::distanciaTreinoPlanejado)
                .sum();
        return BigDecimal.valueOf(volume);
    }

    /**
     * Persiste o plano completo e atualiza os metadados.
     */
    private PlanoSemanal salvarPlanoCompleto(PlanoSemanal plano, PlanoMetaDados metaDados) {
        // PlanoMetaDados is already persisted in prepararMetadados(), no need to save again
        PlanoSemanal planoSalvo = planoSemanalRepository.save(plano);

        log.info("✅ Plano salvo - {} treinos, volume: {}km",
                plano.getTreinosPlanejados().size(),
                plano.getVolumePlanejadoKm());

        return planoSalvo;
    }

    private PlanoSemanal criarPlanoEntity(PlanoSemanalLlmDto planoDto, Atleta atleta, LocalDate semanaInicio, LocalDate semanaFim, PlanoMetaDados metaDados) {
        // Busca o último plano para pegar a média histórica
        PlanoSemanal ultimoPlano = planoSemanalRepository
                .findTopByAtletaIdOrderBySemanaInicioDesc(atleta.getId())
                .orElse(null);

        PlanoSemanal plano = planoSemanalMapper.toEntity(planoDto);
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setReviewStatus(PlanoReviewStatus.AGUARDANDO_REVISAO);

        plano.setSemanaInicio(semanaInicio);
        plano.setSemanaFim(semanaFim);

        if (ultimoPlano != null && ultimoPlano.getPlanoMetaDados() != null) {
            var mediaSemanalHistorica = ultimoPlano.getPlanoMetaDados().getVolumeSemanalMedio();

            var volumePlanejado = mediaSemanalHistorica.multiply(FATOR_INCREMENTO_VOLUME_PLANEJADO);

            metaDados.setVolumePlanejado(volumePlanejado);
            metaDados.setVolumeSemanalMedio(mediaSemanalHistorica);

        }

        plano.setPlanoMetaDados(metaDados);

        return plano;
    }

    /**
     * Prepara e atualiza metadados do plano baseado em treinos realizados e plano gerado.
     *
     * <p>Este método orquestra a atualização de todas as métricas do atleta:
     * <ul>
     *   <li>Métricas de carga (CTL, ATL, TSB) - calculadas pelo {@link TsbService}</li>
     *   <li>Métricas semanais médias (volume, TSS, frequência)</li>
     *   <li>Padrões de treino (dias consecutivos, desde último descanso)</li>
     *   <li>Progressão de volume</li>
     * </ul>
     *
     * <p><b>IMPORTANTE:</b> As métricas CTL/ATL/TSB são calculadas baseadas em treinos
     * REALIZADOS (não planejados), garantindo precisão e consistência.
     *
     * @param planoDto Plano gerado pela IA (contém volume planejado)
     * @param dadosPlano Dados do plano incluindo metadados anteriores
     * @return Metadados atualizados e persistidos
     */
    private PlanoMetaDados prepararMetadados(PlanoSemanalLlmDto planoDto, DadosPlanoDto dadosPlano) {
        PlanoMetaDados metaDadosCached = dadosPlano.metaDados();

        if (metaDadosCached.getId() == null) {
            log.debug("Metadados sem ID - retornando sem atualizar");
            return metaDadosCached;
        }

        // Re-busca do banco para obter entidade managed no contexto de persistência atual.
        // O @Cacheable em buscarOuCriarMetadados pode retornar entidade detached,
        // causando StaleObjectStateException no merge.
        // tenant-aware: garante que os metadados pertencem ao tenant do contexto.
        UUID tenantId = TenantContext.getRequiredTenantId();
        PlanoMetaDados metaDados = planoMetadadosRepository.findByIdAndTenantId(metaDadosCached.getId(), tenantId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "Metadados não encontrados: " + metaDadosCached.getId()));

        Atleta atleta = dadosPlano.atleta();
        UUID atletaId = atleta.getId();

        log.debug("Atualizando metadados para atleta {} com volume planejado: {}km",
            atletaId, planoDto.volumePlanejadoKm());

        // 1. Calcular métricas semanais médias baseadas em treinos realizados
        br.com.menthoros.backend.dto.output.MetricasSemanaisMedias metricas =
            metricasAgregadasService.calcularMetricasSemanais(atletaId, 6);

        metaDados.setVolumeSemanalMedio(metricas.volumeMedio());
        metaDados.setTssSemanalMedio(metricas.tssMedio());
        metaDados.setTreinosPorSemanaMedio(metricas.treinosPorSemanaMedio());

        log.debug("Métricas semanais atualizadas - Volume: {}km, TSS: {}, Frequência: {} treinos/semana",
            metricas.volumeMedio(), metricas.tssMedio(), metricas.treinosPorSemanaMedio());

        // 2. Calcular padrões de treino (dias consecutivos e desde último descanso)
        PadroesTreino padroes =
            metricasAgregadasService.calcularPadroesTreino(atletaId);

        metaDados.setDiasConsecutivosTreino(padroes.diasConsecutivos());
        metaDados.setDiasDesdeUltimoDescanso(padroes.diasDesdeDescanso());

        log.debug("Padrões de treino atualizados - Dias consecutivos: {}, Desde descanso: {}",
            padroes.diasConsecutivos(), padroes.diasDesdeDescanso());

        // 3. Atualizar volume planejado desta semana
        metaDados.setVolumePlanejado(BigDecimal.valueOf(planoDto.volumePlanejadoKm()));

        // 4. Atualizar progressão de volume
        atualizarProgressao(metaDados, planoDto.volumePlanejadoKm());

        // 5. Analisar métricas e aplicar alertas/status/recomendação
        metaDados.aplicarAnalise(metricasAlertaService.analisarMetricas(metaDados));

        // 6. Persistir metadados atualizados
        metaDados = planoMetadadosRepository.save(metaDados);

        log.info("Metadados atualizados com sucesso para atleta {}", atletaId);

        return metaDados;
    }

    private double distanciaTreinoPlanejado(TreinoPlanejado treinoPlanejado) {
        if (treinoPlanejado.getDistanciaKm() != null) return treinoPlanejado.getDistanciaKm().doubleValue();

        if (treinoPlanejado.getEtapas() != null) {
            return treinoPlanejado.getEtapas().stream()
                    .map(e -> e.getDistanciaKm() == null ? 0.0 : e.getDistanciaKm().doubleValue())
                    .mapToDouble(Double::doubleValue)
                    .sum();
        }

        return 0.0;

    }

    private DadosPlanoDto getPreparaDadosPlano(UUID atletaId) {
        // tenant-aware: garante que o atleta pertence ao tenant do contexto
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        // Validações de negócio do atleta
        validarEstadoAtleta(atleta);

        PlanoMetaDados metaDados = planoMetadadosService.buscarOuCriarMetadados(atleta);

        LocalDate hoje = LocalDate.now();
        LocalDate inicio42d = hoje.minusDays(JANELA_HISTORICO_DIAS);
        List<TreinoRealizado> realizados = treinoRealizadoRepository
                .findByAtletaIdAndDataTreinoBetween(atletaId, inicio42d, hoje);

        List<TreinoRealizadoOutputDto> ultimosTreinos = realizados.stream()
                .map(treinoMapper::toOutputDto)
                .toList();

        LocalDate dataInicio = planoSemanalRepository
                .findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)
                .map(p -> p.getSemanaInicio().plusWeeks(1))
                .orElse(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));

        PlanoSemanalOutputDto planoAnterior = planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(atletaId, dataInicio, PlanoStatus.CONCLUIDO).map(planoSemanalMapper::toOutputDto).orElse(null);

        return new DadosPlanoDto(atleta, dataInicio, planoAnterior, ultimosTreinos, metaDados);
    }

    /**
     * Retorna a próxima prova futura do atleta.
     * Prioriza a prova marcada como alvo; se não houver, usa a de data mais próxima.
     * Retorna empty quando não há provas com data futura cadastradas.
     */
    private Optional<Prova> buscarProximaProva(Atleta atleta) {
        LocalDate hoje = LocalDate.now();
        List<Prova> provasFuturas = atleta.getProvas() == null
                ? Collections.emptyList()
                : atleta.getProvas().stream()
                        .filter(p -> p.getDataProva() != null && !p.getDataProva().isBefore(hoje))
                  .filter(prova -> prova.getStatusProva() != ProvaStatus.CANCELADA)
                        .sorted(Comparator.comparing(Prova::getDataProva))
                        .toList();

        if (provasFuturas.isEmpty()) {
            return Optional.empty();
        }

        return provasFuturas.stream()
                .filter(Prova::isProvaAlvo)
                .findFirst()
                .or(() -> provasFuturas.stream().findFirst());
    }

    private PlanoSemanalLlmDto gerarPlanoSemanal(DadosPlanoDto dadosPlanoDto, ModoGeracaoPlano modoGeracao,
                                                 DecisaoProgressao decisaoProgressao,
                                                 @Nullable RevisaoSemanal revisaoConsumida,
                                                 LocalDate semanaInicio) {
        try {
            log.info("Iniciando geração de plano para atleta: {}", dadosPlanoDto.atleta().getId());

            Prova prova = buscarProximaProva(dadosPlanoDto.atleta()).orElse(null);
            if (prova == null) {
                log.warn("Atleta {} não possui provas futuras cadastradas — plano gerado sem prova alvo",
                        dadosPlanoDto.atleta().getId());
            }

            PlanoSemanalLlmDto planoDto = iaService.geraPlanoSemanalAvancado(dadosPlanoDto.atleta(), dadosPlanoDto.metaDados(), prova, modoGeracao, decisaoProgressao, revisaoConsumida, semanaInicio);

            validaPlanoGerado(planoDto);
            return planoDto;
        } catch (LLMException e) {
            log.error("Falha na IA ao gerar o plano para o atleta: {}", dadosPlanoDto.atleta().getId());
            throw e;
        } catch (Exception e) {

            log.error("Erro inesperado ao gerar o plano para o atleta: {}", dadosPlanoDto.atleta().getId(), e);
            throw new LLMException("Erro inesperado ao gerar plano", e);
        }
    }

    private void validaPlanoGerado(PlanoSemanalLlmDto planoDto) {

        if (planoDto == null) {
            throw new LLMException("IA retornou plano nulo");
        }

        if (planoDto.treinosPlanejados() == null || planoDto.treinosPlanejados().isEmpty()) {
            throw new LLMException("IA retornou plano sem treinos");
        }

    }

    private LocalDate calcularDataTreino(LocalDate semanaInicio, DiaSemana diaSemana) {
        DayOfWeek dayOfWeek = Utils.converterParaDayOfWeek(diaSemana);

        return semanaInicio.with(TemporalAdjusters.nextOrSame(dayOfWeek));
    }

    @Override
    @Transactional
    public void deletePlanoSemanal(UUID planoSemanalId) {
        // tenant-aware: garante que o plano pertence ao tenant do contexto
        UUID tenantId = TenantContext.getRequiredTenantId();
        PlanoSemanal plano = planoSemanalRepository.findByIdAndTenantId(planoSemanalId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado: " + planoSemanalId));

        if (plano.getStatus() != PlanoStatus.PLANEJADO) {
            throw new DomainRuleViolationException("Apenas planos ainda não iniciados podem ser excluídos");
        }

        // Plano com status PLANEJADO não tem TreinoRealizado vinculados,
        // mas os bulk updates garantem integridade caso haja dados inconsistentes.
        // clearAutomatically=true limpa o cache L1 do Hibernate evitando CHECK_ON_FLUSH.
        treinoRealizadoRepository.desvinculardeTreinosPlanejados(planoSemanalId);
        treinoRealizadoRepository.desvinculardePlanoSemanal(planoSemanalId);

        // Janela capturada ANTES do delete: o CascadeType.ALL apaga os TreinoPlanejado vinculados,
        // então esses dados não estariam mais disponíveis depois para o listener de limpeza.
        UUID atletaId = plano.getAtleta().getId();
        LocalDate semanaInicio = plano.getSemanaInicio();
        LocalDate semanaFim = plano.getSemanaFim();

        // CascadeType.ALL propaga a exclusão para os TreinoPlanejado vinculados.
        planoSemanalRepository.delete(plano);
        eventPublisher.publishEvent(
                new PlanoDeletadoEvent(planoSemanalId, atletaId, tenantId, semanaInicio, semanaFim));
        log.info("✅ Plano deletado com sucesso - ID: {}", planoSemanalId);
    }


    @Transactional
    @Override
    public PlanoSemanalOutputDto buscarPlanoPorAtleta(UUID atletaId, boolean apenasAprovados) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        PlanoSemanal planoSemanal;

        if (apenasAprovados) {
            planoSemanal = planoSemanalRepository
                    .findTopByAtletaIdAndAssessoriaIdAndReviewStatusOrderBySemanaInicioDesc(
                            atletaId, tenantId, PlanoReviewStatus.APROVADO)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Nenhum plano aprovado encontrado para o atleta: " + atletaId));
        } else {
            planoSemanal = planoSemanalRepository
                    .findAtivosPorAtleta(atletaId, tenantId)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Plano não encontrado para o atleta: " + atletaId));
        }

        Hibernate.initialize(planoSemanal.getTreinosPlanejados());
        double volumeRealizadoKm = calcularVolumeRealizadoKm(
                atletaId, tenantId, planoSemanal.getSemanaInicio(), planoSemanal.getSemanaFim());
        return planoSemanalMapper.toOutputDto(planoSemanal).toBuilder()
                .volumeRealizadoKm(volumeRealizadoKm)
                .build();
    }

    /**
     * Soma a distância dos treinos realizados pelo atleta dentro da janela da semana do plano.
     *
     * <p>Calculado dinamicamente por atletaId + janela de datas (não pela FK
     * {@code TreinoRealizado.planoSemanal}) porque nem todo fluxo de registro de treino
     * (sync Strava, upload .fit, lançamento do coach, reconciliação) vincula essa FK — somar
     * por data evita que esses treinos fiquem invisíveis ao volume realizado.
     *
     * Idempotent: YES — pure read, no side effects.
     * Side Effects: NONE
     * Tenant-aware: YES — query filtra por tenantId explicitamente.
     */
    private double calcularVolumeRealizadoKm(
            UUID atletaId, UUID tenantId, LocalDate semanaInicio, LocalDate semanaFim) {
        return treinoRealizadoRepository
                .findByAtletaIdAndTenantIdAndDataTreinoBetween(atletaId, tenantId, semanaInicio, semanaFim)
                .stream()
                .map(t -> Optional.ofNullable(t.getDistanciaKm()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<PlanoSemanal> findPlanoVigenteRelevante(UUID atletaId, UUID tenantId) {
        return planoSemanalRepository.findMostRecentRelevantPlano(atletaId, tenantId);
    }

    /**
     * Atualiza contador de semanas de progressão contínua.
     *
     * <p>Incrementa o contador quando o volume planejado excede a média semanal,
     * resetando quando há redução de volume. Importante para identificar
     * necessidade de semana de recuperação (após 3-4 semanas consecutivas de aumento).
     *
     * @param metaDados Metadados a serem atualizados
     * @param volumeNovo Volume do novo plano (km)
     */
    private void atualizarProgressao(PlanoMetaDados metaDados, double volumeNovo) {
        if (metaDados.getVolumeSemanalMedio() != null) {
            if (volumeNovo > metaDados.getVolumeSemanalMedio().doubleValue()) {
                Integer semanas = metaDados.getSemanasProgressaoContinua();
                Integer novaContagem = semanas != null ? semanas + 1 : 1;
                metaDados.setSemanasProgressaoContinua(novaContagem);

                log.debug("Progressão contínua: {} semanas seguidas de aumento de volume", novaContagem);

                if (novaContagem >= 3) {
                    log.info("Atleta em progressão contínua há {} semanas. Considere semana de recuperação em breve.",
                            novaContagem);
                }
            } else {
                metaDados.setSemanasProgressaoContinua(0);
                log.debug("Progressão resetada - volume não aumentou em relação à média");
            }
        }
    }

    private void validarParametrosEntrada(UUID atletaId, ModoGeracaoPlano modoGeracao) {
        Objects.requireNonNull(atletaId, "ID do atleta é obrigatório");
        Objects.requireNonNull(modoGeracao, "Modo de geração é obrigatório");
    }

    /**
     * Valida o estado do atleta antes de gerar um plano de treino.
     *
     * <p>Verifica se o atleta está em condições de receber um plano, incluindo:
     * <ul>
     *   <li>Se o atleta está ativo no sistema</li>
     *   <li>Se possui dias disponíveis para treinar</li>
     *   <li>Se possui dados essenciais configurados</li>
     * </ul>
     *
     * @param atleta o atleta a ser validado
     * @throws DomainRuleViolationException se o atleta não atender aos requisitos
     */
    private void validarEstadoAtleta(Atleta atleta) {
        // Valida se o atleta está ativo
        if (atleta.getAtivo() == null || !atleta.getAtivo().isActive()) {
            throw new DomainRuleViolationException(
                    "Não é possível gerar plano para atleta inativo. Status: " +
                    (atleta.getAtivo() != null ? atleta.getAtivo().getLabel() : "INDEFINIDO")
            );
        }

        // Valida se o atleta possui dias disponíveis
        if (atleta.getDiasDisponiveis() == null || atleta.getDiasDisponiveis().isEmpty()) {
            throw new DomainRuleViolationException(
                    "Não é possível gerar plano sem dias disponíveis. " +
                    "Por favor, configure os dias disponíveis para treino no perfil do atleta."
            );
        }

        // Valida se possui objetivo definido
        if (atleta.getObjetivo() == null || atleta.getObjetivo().isBlank()) {
            throw new DomainRuleViolationException(
                    "Não é possível gerar plano sem objetivo definido. " +
                    "Configure o objetivo no perfil do atleta."
            );
        }

        // Valida se possui nível de experiência definido
        if (atleta.getNivelExperiencia() == null) {
            throw new DomainRuleViolationException(
                    "Não é possível gerar plano sem nível de experiência definido. " +
                    "Configure o nível no perfil do atleta."
            );
        }

        log.debug("Atleta {} validado com sucesso: {} dias disponíveis, nível: {}",
                atleta.getId(),
                atleta.getDiasDisponiveis().size(),
                atleta.getNivelExperiencia());
    }

    private DecisaoProgressao calcularDecisaoProgressao(UUID atletaId) {
        try {
            ProgressaoHistoricoResumo historico = progressaoTreinoService.calcularHistorico(atletaId);
            return progressaoTreinoService.calcularDecisao(historico);
        } catch (DomainNotFoundException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Falha ao calcular decisão de progressão para atleta {} — plano será gerado sem contexto de progressão", atletaId, e);
            return null;
        }
    }
}
