package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.dto.output.PlanoTreinoOutputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.ai.ledger.Violacao;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.exception.PlanoNaoConformeException;
import br.com.menthoros.backend.services.helper.PlanoLlmLedgerHook;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.services.IaService;
import br.com.menthoros.backend.services.helper.LlmUsageLogger;
import br.com.menthoros.backend.services.helper.PaceValidator;
import br.com.menthoros.backend.services.helper.RegraGeracaoTreino;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.helper.PlanoEstruturaReparador;
import br.com.menthoros.backend.services.helper.PlanoResilienceService;
import br.com.menthoros.backend.services.helper.PlannerShadowService;
import br.com.menthoros.backend.services.helper.TreinoNormalizador;
import br.com.menthoros.backend.services.helper.EtapaFcValidator;
import br.com.menthoros.backend.services.helper.PlanoLlmValidator;
import br.com.menthoros.backend.domain.compliance.PlannerViolation;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import io.micrometer.core.instrument.MeterRegistry;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import br.com.menthoros.backend.services.prompt.LlmJsonSchemaBuilder;
import br.com.menthoros.backend.services.prompt.PaceHistoricoFormatter;
import br.com.menthoros.backend.services.prompt.PlanoTreinoPromptBuilder;
import br.com.menthoros.backend.services.quality.PlanQualityChecker;
import br.com.menthoros.backend.services.quality.ViolacaoQualidade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Primary
public class IaServiceImpl implements IaService {

    private final ModelRouter modelRouter;
    private final PlanoTreinoPromptBuilder promptBuilder;
    private final LlmJsonSchemaBuilder llmJsonSchemaBuilder;
    private final AtletaRepository atletaRepository;
    private final RegraGeracaoTreino regraGeracaoTreino;
    private final TreinoHistoricoProvider treinoHistoricoProvider;
    private final PaceHistoricoFormatter paceHistoricoFormatter;
    private final PaceValidator paceValidator;
    private final ZonaTreinoService zonaTreinoService;
    private final PlanQualityChecker planQualityChecker;
    private final PlanoEstruturaReparador estruturaReparador;
    private final TreinoNormalizador treinoNormalizador;
    private final EtapaFcValidator etapaFcValidator;
    private final PlanoLlmValidator planoLlmValidator;
    private final PlanoResilienceService planoResilienceService;
    private final MeterRegistry meterRegistry;
    private final LlmUsageLogger llmUsageLogger;
    private final PlannerShadowService plannerShadowService;
    private final PlanoLlmLedgerHook ledgerHook;

    public IaServiceImpl(ModelRouter modelRouter, PlanoTreinoPromptBuilder promptBuilder,
                         LlmJsonSchemaBuilder llmJsonSchemaBuilder,
                         AtletaRepository atletaRepository, RegraGeracaoTreino regraGeracaoTreino,
                         TreinoHistoricoProvider treinoHistoricoProvider,
                         PaceHistoricoFormatter paceHistoricoFormatter,
                         PaceValidator paceValidator,
                         ZonaTreinoService zonaTreinoService,
                         PlanQualityChecker planQualityChecker,
                         PlanoEstruturaReparador estruturaReparador,
                         TreinoNormalizador treinoNormalizador,
                         EtapaFcValidator etapaFcValidator,
                         PlanoLlmValidator planoLlmValidator,
                         PlanoResilienceService planoResilienceService,
                         MeterRegistry meterRegistry,
                         LlmUsageLogger llmUsageLogger,
                         PlannerShadowService plannerShadowService,
                         PlanoLlmLedgerHook ledgerHook) {
        this.modelRouter = modelRouter;
        this.promptBuilder = promptBuilder;
        this.atletaRepository = atletaRepository;
        this.llmJsonSchemaBuilder = llmJsonSchemaBuilder;
        this.regraGeracaoTreino = regraGeracaoTreino;
        this.treinoHistoricoProvider = treinoHistoricoProvider;
        this.paceHistoricoFormatter = paceHistoricoFormatter;
        this.paceValidator = paceValidator;
        this.zonaTreinoService = zonaTreinoService;
        this.planQualityChecker = planQualityChecker;
        this.estruturaReparador = estruturaReparador;
        this.treinoNormalizador = treinoNormalizador;
        this.etapaFcValidator = etapaFcValidator;
        this.planoLlmValidator = planoLlmValidator;
        this.planoResilienceService = planoResilienceService;
        this.meterRegistry = meterRegistry;
        this.llmUsageLogger = llmUsageLogger;
        this.plannerShadowService = plannerShadowService;
        this.ledgerHook = ledgerHook;
    }

    @Override
    public PlanoSemanalLlmDto gerarPlanoSemanal(AtletaOutputDto atletaOutputDto, List<TreinoRealizadoOutputDto> treinoRealizadoOutputDtoList, PlanoSemanalOutputDto planoSemanalOutputDto) {
        String prompt = promptBuilder.buildRequest(atletaOutputDto, treinoRealizadoOutputDtoList, planoSemanalOutputDto);

        ChatClient chatClient = modelRouter.route(TaskComplexity.PLANO);
        log.info("Geração de plano roteada via TaskComplexity.PLANO (bean gpt4oPlanoClient)");

        try {
            PlanoSemanalLlmDto plano = chatClient.prompt()
                    .user(prompt)
//                    .options(llmJsonSchemaBuilder.defaultJsonSchemaOptions())
                    .call()
                    .entity(PlanoSemanalLlmDto.class);

            // Validação pós-geração
            plano = validarENormalizarPlanoGerado(plano, atletaOutputDto.id());

            log.info("Plano gerado com sucesso via structured output para atleta: {}", atletaOutputDto.id());
            return plano;

        } catch (Exception e) {
            log.error("Erro ao gerar plano via structured output para atleta {}: {}", atletaOutputDto.id(), e.getMessage(), e);
            throw new LLMException("Falha na geração de plano via IA: " + e.getMessage(), e);
        }
    }

    /**
     * Gera o plano semanal avançado via LLM (roteado por {@code TaskComplexity.PLANO} → GPT-4o).
     *
     * Idempotent: NO — invoca o LLM (saída não-determinística); sem escrita de estado por tentativa.
     * Side Effects: chamada ao LLM (billable); leitura de atleta/histórico/metadados; log de uso de
     *   tokens via {@code LlmUsageLogger} (best-effort); métricas Micrometer via {@code PlanoResilienceService}.
     * Tenant-aware: YES — o atleta é resolvido com predicado de tenant e {@code validarENormalizarPlanoGerado}
     *   opera sob o {@code TenantContext} corrente.
     */
    @Override
    public PlanoSemanalLlmDto geraPlanoSemanalAvancado(Atleta atleta, PlanoMetaDados metaDados, Prova prova, ModoGeracaoPlano modoGeracaoPlano, DecisaoProgressao decisaoProgressao, RevisaoSemanal revisaoConsumida, LocalDate inicioSemana, br.com.menthoros.backend.domain.planner.WeekPlanSkeleton skeleton){
        // Para SEMANA_ATUAL, filtra apenas os dias que ainda não passaram e informa o LLM.
        // Para PROXIMA_SEMANA, passa null — o prompt usa todos os dias disponíveis do atleta.
        List<DiaSemana> diasEfetivos = ModoGeracaoPlano.SEMANA_ATUAL.equals(modoGeracaoPlano)
                ? regraGeracaoTreino.filtrarDiasDisponiveis(atleta.getDiasDisponiveis(), LocalDate.now(), modoGeracaoPlano)
                : null;

        var promptGerado = promptBuilder.buildOptimizedPrompt(atleta, metaDados, prova, inicioSemana, diasEfetivos, decisaoProgressao, revisaoConsumida, skeleton);
        // system é byte-idêntico entre tentativas — capturado aqui e aplicado direto no
        // ChatClient; nunca passa pelo PlanoResilienceService, então o retry (que só reescreve o
        // `user` com o feedback de correção) não pode divergir o cache de prefixo (CA4).
        String system = promptGerado.system();

        ChatClient chatClient = modelRouter.route(TaskComplexity.PLANO);
        log.info("Geração de plano (avançado) roteada via TaskComplexity.PLANO (bean gpt4oPlanoClient)");

        long startTime = System.currentTimeMillis();
        PlanoSemanalLlmDto plano;
        try {
            // Geração resiliente: reparo já aplicado no validar; aqui, retry único com feedback se a
            // validação ainda falhar estruturalmente. Falha final → DomainRuleViolationException (4xx).
            // Ledger (add-plan-generation-ledger, D3): a sessão abre a tentativa em volta da chamada e
            // fecha o resultado após a validação; a IaServiceImpl não conhece escopo nem versões.
            PlanoLlmLedgerHook.Sessao sessao = ledgerHook.novaSessao();
            plano = planoResilienceService.gerarComResiliencia(
                    t -> sessao.chamar(t.numero(), () -> {
                        var resposta = chatClient.prompt().system(system).user(t.prompt())
                                .options(llmJsonSchemaBuilder.defaultJsonSchemaOptions())
                                .call().responseEntity(PlanoSemanalLlmDto.class);
                        llmUsageLogger.registrar(resposta.getResponse()); // best-effort, nunca lança
                        return resposta.getEntity();
                    }),
                    p -> sessao.validar(() -> aplicarComplianceEstagio1(
                            validarENormalizarPlanoGerado(p, atleta.getId()), atleta, skeleton, inicioSemana)),
                    promptGerado.user());
        } catch (DomainRuleViolationException e) {
            throw e; // falha estrutural final → mensagem ao treinador (não re-empacotar como 503)
        } catch (Exception e) {
            log.error("Erro ao gerar plano via structured output para atleta {}: {}", atleta.getId(), e.getMessage(), e);
            throw new LLMException("Falha na geração de plano via IA: " + e.getMessage(), e);
        }

        // Verificação de aderência às Constraint declaradas (mede via Micrometer; ação fica para a harden)
        List<ViolacaoQualidade> violacoes = planQualityChecker.check(plano, promptGerado.regras());
        if (!violacoes.isEmpty()) {
            log.warn("Plano do atleta {} com {} violação(ões) de constraint: {}",
                    atleta.getId(), violacoes.size(), violacoes.stream().map(ViolacaoQualidade::key).toList());
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Plano gerado com sucesso via structured output para atleta: {} - {} s", atleta.getId(), totalTime / 1000.0);
        return plano;
    }

    /**
     * Estagio 1 do enforcement (planner-engine-enforcement secao 4): compliance PRE-redistribuicao.
     * Roda somente quando ha {@code skeleton} (flag {@code planner-engine.enabled=true}); com o flag
     * off recebe {@code skeleton == null} e é um no-op — prompt/geracao legados, sem checagem.
     *
     * <p>Violacao vira {@link LLMException}: dentro de {@code gerarComResiliencia} isso aciona o retry
     * com os motivos no feedback e, esgotado o orcamento, cai em {@code DomainRuleViolationException}
     * (422) — nenhuma nova geracao alem do orcamento (design Decisao 3b). Emite
     * {@code planner.compliance.failure.count{stage=PRE}} a cada violacao detectada.
     */
    private PlanoSemanalLlmDto aplicarComplianceEstagio1(PlanoSemanalLlmDto validado, Atleta atleta,
                                                         br.com.menthoros.backend.domain.planner.WeekPlanSkeleton skeleton,
                                                         LocalDate inicioSemana) {
        if (skeleton == null) {
            return validado;
        }
        List<PlannerViolation> violacoes = plannerShadowService.checkPreRedistribution(
                validado, skeleton, atleta, inicioSemana);
        if (!violacoes.isEmpty()) {
            meterRegistry.counter("planner.compliance.failure.count", "stage", "PRE").increment();
            String motivos = violacoes.stream()
                    .map(v -> v.key() + ": " + v.mensagem())
                    .collect(java.util.stream.Collectors.joining("; "));
            throw new PlanoNaoConformeException("Plano diverge da estrutura prescrita pelo planner: " + motivos,
                    violacoes.stream().map(v -> new Violacao(v.key().name(), v.mensagem())).toList());
        }
        return validado;
    }

    /**
     * Valida o plano gerado pela IA, detectando inconsistências comuns
     */
    private PlanoSemanalLlmDto validarENormalizarPlanoGerado(PlanoSemanalLlmDto plano, UUID atletaId) {

        // tenant-aware: garante que o atleta pertence ao tenant do contexto
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new LLMException("Atleta não encontrado"));

        if (plano == null || plano.treinosPlanejados() == null) {
            throw new LLMException("Plano gerado está nulo ou sem treinos");
        }

        // Pré-computar tetos e pisos de pace para validação
        var ctx = treinoHistoricoProvider.prepararContexto(atleta);
        Map<TipoTreino, BigDecimal> tetoPorTipo = paceHistoricoFormatter.calcularTetoPorTipo(ctx.treinosUltimas4Semanas());
        Map<TipoTreino, BigDecimal> pisoPorTipo = paceHistoricoFormatter.calcularPisoPorTipo(ctx.treinosUltimas4Semanas());

        // Pré-computar zonas de FC para validação de etapas (LTHR) — null se sem dados fisiológicos
        final List<ZonaFC> zonasParaValidacao;
        if (atleta.getFcLimiar() != null || atleta.getFcMaxima() != null) {
            zonasParaValidacao = zonaTreinoService.calcularZonasFC(
                    atleta.getFcMaximaCalculada(), atleta.getFcLimiarCalculada());
        } else {
            zonasParaValidacao = null;
        }

        List<TreinoPlanejadoLlmDto> treinosNormalizados = plano.treinosPlanejados().stream().map(treino -> {
            String tipoTreino = treino.tipoTreino();

            // Validar treinos INTERVALADO ou TIRO
            if ("INTERVALADO".equals(tipoTreino) || "TIRO".equals(tipoTreino)) {
                // (Passo 0) corrige distâncias de etapas temporais antes de expandir e normalizar
                List<EtapaTreinoLlmDto> etapasCorrigidas =
                        treinoNormalizador.corrigirDistanciasEtapasTemporais(treino.etapas(), atleta.getPaceLimiar());
                treino = new TreinoPlanejadoLlmDto(
                        treino.diaSemana(), treino.tipoTreino(), treino.fcAlvo(),
                        treino.tssPlanejado(), treino.intensidadePlanejada(),
                        treino.percepcaoEsforcoEsperada(), treino.justificativaIa(),
                        treino.duracaoMin(), treino.distanciaKm(), treino.ritmoAlvo(),
                        etapasCorrigidas, treino.descricao(), treino.zonaAlvo(), treino.provaId());
                // Expansão ANTES da validação: corrige alucinação de compressão "NxDist"
                treino = treinoNormalizador.expandirEtapasAgregadas(treino, zonasParaValidacao);
                planoLlmValidator.validarTreinoIntervalado(treino, atletaId);
                treino = treinoNormalizador.normalizarTreinoIntervalado(treino, atleta.getNivelExperiencia(), zonasParaValidacao);
                treino = treinoNormalizador.reconciliarDistanciaComEtapas(treino);
            }

            // Fartlek: expande alucinações "Nx (AccelMin + RecovMin)" e reconcilia distância
            if ("FARTLEK".equals(tipoTreino)) {
                List<EtapaTreinoLlmDto> etapasCorrigidas =
                        treinoNormalizador.corrigirDistanciasEtapasTemporais(treino.etapas(), atleta.getPaceLimiar());
                treino = new TreinoPlanejadoLlmDto(
                        treino.diaSemana(), treino.tipoTreino(), treino.fcAlvo(),
                        treino.tssPlanejado(), treino.intensidadePlanejada(),
                        treino.percepcaoEsforcoEsperada(), treino.justificativaIa(),
                        treino.duracaoMin(), treino.distanciaKm(), treino.ritmoAlvo(),
                        etapasCorrigidas, treino.descricao(), treino.zonaAlvo(), treino.provaId());
                treino = treinoNormalizador.expandirEtapasAgregadas(treino, zonasParaValidacao);
                treino = treinoNormalizador.reconciliarDistanciaComEtapas(treino);
            }

            // Reparo determinístico de estrutura "3 etapas" ANTES da validação (não-op p/ outros tipos):
            // sintetiza aquecimento/desaquecimento faltante ou reordena, evitando derrubar o plano por
            // violação trivial. Falta-PRINCIPAL/ambíguo não é reparado → cai na validação (retry).
            treino = estruturaReparador.reparar(treino, tipoTreino);

            // Validar treino LONGO
            if ("LONGO".equals(tipoTreino)) {
                planoLlmValidator.validarTreinoLongo(treino, atletaId);
            }

            // Validar estrutura de treinos REGENERATIVO, CONTINUO e TEMPO_RUN
            if ("REGENERATIVO".equals(tipoTreino)) {
                planoLlmValidator.validarTreinoRegenerativo(treino, atletaId);
            }
            if ("CONTINUO".equals(tipoTreino)) {
                planoLlmValidator.validarTreinoContinuo(treino, atletaId);
            }
            if ("TEMPO_RUN".equals(tipoTreino)) {
                planoLlmValidator.validarTreinoTempoRun(treino, atletaId, atleta);
            }

            // Validar repeticoes = 1 em todas as etapas
            planoLlmValidator.validarRepeticoes(treino, atletaId);

            // Validar FC das etapas contra zonas fisiológicas LTHR
            if (zonasParaValidacao != null && treino.etapas() != null) {
                final String tipoTreinoFinal = treino.tipoTreino();
                List<EtapaTreinoLlmDto> etapasValidadas = treino.etapas().stream()
                        .map(etapa -> etapaFcValidator.validarFcEtapa(etapa, tipoTreinoFinal, zonasParaValidacao))
                        .collect(Collectors.toList());
                treino = new TreinoPlanejadoLlmDto(
                        treino.diaSemana(), treino.tipoTreino(), treino.fcAlvo(),
                        treino.tssPlanejado(), treino.intensidadePlanejada(),
                        treino.percepcaoEsforcoEsperada(), treino.justificativaIa(),
                        treino.duracaoMin(), treino.distanciaKm(), treino.ritmoAlvo(), etapasValidadas,
                        treino.descricao(), treino.zonaAlvo(), treino.provaId()
                );
            }

            // Validar ritmoAlvo contra teto e piso de pace
            BigDecimal teto = null;
            BigDecimal piso = null;
            try {
                TipoTreino tipoEnum = TipoTreino.valueOf(tipoTreino);
                teto = tetoPorTipo.get(tipoEnum);
                piso = pisoPorTipo.get(tipoEnum);
            } catch (IllegalArgumentException ignored) {}
            String ritmoValidado = paceValidator.validar(treino.ritmoAlvo(), teto, piso);
            if (!Objects.equals(ritmoValidado, treino.ritmoAlvo())) {
                treino = new TreinoPlanejadoLlmDto(
                        treino.diaSemana(), treino.tipoTreino(), treino.fcAlvo(),
                        treino.tssPlanejado(), treino.intensidadePlanejada(),
                        treino.percepcaoEsforcoEsperada(), treino.justificativaIa(),
                        treino.duracaoMin(), treino.distanciaKm(), ritmoValidado, treino.etapas(),
                        treino.descricao(), treino.zonaAlvo(), treino.provaId()
                );
            }

            // Recalcular duração total com base na soma das etapas (override do valor gerado pelo LLM)
            if (treino.etapas() != null && !treino.etapas().isEmpty()) {
                int totalMinEtapas = treinoNormalizador.somarDuracoesMin(treino.etapas());
                if (totalMinEtapas > 0) {
                    String duracaoAtual = treino.duracaoMin();
                    treino = treinoNormalizador.recalcularDuracaoTreino(treino, treino.etapas());
                    if (!Objects.equals(duracaoAtual, treino.duracaoMin())) {
                        log.info("DURAÇÃO RECALCULADA [{}]: '{}' → '{}' (baseado nas {} etapas)",
                                tipoTreino, duracaoAtual, treino.duracaoMin(), treino.etapas().size());
                    }
                }
            }

            // Distância zerada em treino contínuo (ex.: REGENERATIVO sintetizado pelo reparo estrutural /
            // substituição por lesão): as etapas nascem só com duração, e corrigirDistanciasEtapasTemporais
            // não deriva a etapa PRINCIPAL. Aqui derivamos de duração×pace e reconciliamos o total — sem
            // sobrescrever distância válida já existente.
            treino = treinoNormalizador.garantirDistanciaContinuo(treino, atleta.getPaceLimiar());

            // Validar triângulo pace × distância × duração (após recálculo)
            planoLlmValidator.validarTrianguloPaceDuracaoDistancia(treino);

            return treino;
        }).collect(Collectors.toList());

        // Validar distribuição de carga semanal (dias consecutivos intensos)
        planoLlmValidator.validarDistribuicaoCargaSemanal(treinosNormalizados);

        return new PlanoSemanalLlmDto(
                plano.volumePlanejadoKm(),
                plano.volumeAlvoKm(),
                plano.tsbInicio(),
                plano.tsbFim(),
                plano.status(),
                plano.objetivoSemanal(),
                treinosNormalizados
        );
    }

    // ======================== VALIDAÇÃO FC POR ZONA (LTHR) ========================
    // Delegada para EtapaFcValidator (refactor-iaservice-decomposition, seção 4).

    /**
     * Valida treino intervalado: mínimo 8 etapas, tiros e recuperações balanceados
     */
    @Override
    public Map<Long, PlanoTreinoOutputDto> gerarPlanosEmLote(Map<AtletaOutputDto, List<TreinoRealizadoOutputDto>> atletaDtoListMap) {
        log.warn("Método gerarPlanosEmLote ainda não implementado");
        return Map.of();
    }

}
