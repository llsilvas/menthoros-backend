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
import br.com.menthoros.backend.domain.compliance.PlannerViolation;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import io.micrometer.core.instrument.Counter;
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
import java.util.regex.Pattern;
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
                validarTreinoIntervalado(treino, atletaId);
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
                validarTreinoLongo(treino, atletaId);
            }

            // Validar estrutura de treinos REGENERATIVO, CONTINUO e TEMPO_RUN
            if ("REGENERATIVO".equals(tipoTreino)) {
                validarTreinoRegenerativo(treino, atletaId);
            }
            if ("CONTINUO".equals(tipoTreino)) {
                validarTreinoContinuo(treino, atletaId);
            }
            if ("TEMPO_RUN".equals(tipoTreino)) {
                validarTreinoTempoRun(treino, atletaId, atleta);
            }

            // Validar repeticoes = 1 em todas as etapas
            validarRepeticoes(treino, atletaId);

            // Validar FC das etapas contra zonas fisiológicas LTHR
            if (zonasParaValidacao != null && treino.etapas() != null) {
                final String tipoTreinoFinal = treino.tipoTreino();
                List<EtapaTreinoLlmDto> etapasValidadas = treino.etapas().stream()
                        .map(etapa -> validarFcEtapa(etapa, tipoTreinoFinal, zonasParaValidacao))
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
            validarTrianguloPaceDuracaoDistancia(treino);

            return treino;
        }).collect(Collectors.toList());

        // Validar distribuição de carga semanal (dias consecutivos intensos)
        validarDistribuicaoCargaSemanal(treinosNormalizados);

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

    /**
     * Extrai o range de FC do formato "NNN-NNN bpm".
     * Retorna null se o formato não for reconhecido ou o valor for nulo.
     */
    private int[] parseFcRange(String fcAlvoEtapa) {
        if (fcAlvoEtapa == null) return null;
        var matcher = Pattern.compile("^(\\d{2,3})-(\\d{2,3}) bpm$").matcher(fcAlvoEtapa.trim());
        if (!matcher.matches()) return null;
        return new int[]{ Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)) };
    }

    /**
     * Retorna o range de FC esperado para o tipo de etapa, considerando também o tipo de treino.
     * <p>O tipoTreino afina o mapeamento da etapa PRINCIPAL, que varia de Z1-Z2 (REGENERATIVO)
     * até Z4-Z5 (INTERVALADO/TIRO). Sem tipoTreino, PRINCIPAL cai no default Z2-Z4.</p>
     * <table border="1">
     *   <tr><th>tipoEtapa</th><th>tipoTreino</th><th>Zona</th></tr>
     *   <tr><td>AQUECIMENTO / DESAQUECIMENTO</td><td>qualquer</td><td>Z1</td></tr>
     *   <tr><td>RECUPERACAO</td><td>qualquer</td><td>Z1</td></tr>
     *   <tr><td>INTERVALADO</td><td>qualquer</td><td>Z4–Z5</td></tr>
     *   <tr><td>PRINCIPAL</td><td>REGENERATIVO</td><td>Z1–Z2</td></tr>
     *   <tr><td>PRINCIPAL</td><td>CONTINUO / FACIL / LONGO</td><td>Z2–Z3</td></tr>
     *   <tr><td>PRINCIPAL</td><td>FARTLEK</td><td>Z2–Z4</td></tr>
     *   <tr><td>PRINCIPAL</td><td>TEMPO_RUN</td><td>Z3–Z4</td></tr>
     *   <tr><td>PRINCIPAL</td><td>INTERVALADO / TIRO</td><td>Z4–Z5</td></tr>
     *   <tr><td>PRINCIPAL</td><td>default/null</td><td>Z2–Z4</td></tr>
     * </table>
     */
    private int[] zonaEsperadaFC(String tipoEtapa, String tipoTreino, List<ZonaFC> zonasFC) {
        if (tipoEtapa == null || zonasFC == null || zonasFC.size() < 5) return null;
        return switch (tipoEtapa.toUpperCase()) {
            case "AQUECIMENTO", "RECUPERACAO", "DESAQUECIMENTO" ->
                    new int[]{ zonasFC.get(0).fcMin(), zonasFC.get(0).fcMax() }; // Z1
            case "PRINCIPAL" -> zonaParaEtapaPrincipal(tipoTreino, zonasFC);
            case "INTERVALADO" ->
                    new int[]{ zonasFC.get(3).fcMin(), zonasFC.get(4).fcMax() }; // Z4–Z5
            default -> null;
        };
    }

    /** Resolve a zona esperada para etapa PRINCIPAL com base no tipo do treino. */
    private int[] zonaParaEtapaPrincipal(String tipoTreino, List<ZonaFC> zonasFC) {
        if (tipoTreino == null) return new int[]{ zonasFC.get(1).fcMin(), zonasFC.get(3).fcMax() }; // Z2-Z4 default
        return switch (tipoTreino.toUpperCase()) {
            case "REGENERATIVO"            -> new int[]{ zonasFC.get(0).fcMin(), zonasFC.get(1).fcMax() }; // Z1-Z2
            case "CONTINUO", "FACIL", "LONGO" -> new int[]{ zonasFC.get(1).fcMin(), zonasFC.get(2).fcMax() }; // Z2-Z3
            case "FARTLEK"                 -> new int[]{ zonasFC.get(1).fcMin(), zonasFC.get(3).fcMax() }; // Z2-Z4
            case "TEMPO_RUN"               -> new int[]{ zonasFC.get(2).fcMin(), zonasFC.get(3).fcMax() }; // Z3-Z4
            case "INTERVALADO", "TIRO"     -> new int[]{ zonasFC.get(3).fcMin(), zonasFC.get(4).fcMax() }; // Z4-Z5
            default                        -> new int[]{ zonasFC.get(1).fcMin(), zonasFC.get(3).fcMax() }; // Z2-Z4
        };
    }

    /**
     * Verifica se o {@code fcAlvoEtapa} tem sobreposição ≥50% com a zona fisiológica esperada.
     * <p>Em caso de divergência, corrige o valor para o quartil central da zona esperada
     * e registra um {@code WARN}. Nunca lança exceção — manter o plano válido é prioridade.</p>
     */
    private EtapaTreinoLlmDto validarFcEtapa(EtapaTreinoLlmDto etapa, String tipoTreino, List<ZonaFC> zonasFC) {
        int[] prescrito = parseFcRange(etapa.fcAlvoEtapa());
        if (prescrito == null) {
            if (etapa.fcAlvoEtapa() != null) {
                log.warn("fcAlvoEtapa não parseable, mantendo original: tipo='{}' valor='{}'",
                        etapa.tipoEtapa(), etapa.fcAlvoEtapa());
            }
            return etapa;
        }

        int[] esperado = zonaEsperadaFC(etapa.tipoEtapa(), tipoTreino, zonasFC);
        if (esperado == null) return etapa;

        int prescMin = prescrito[0], prescMax = prescrito[1];
        int espMin   = esperado[0],  espMax   = esperado[1];

        int overlap = Math.max(0, Math.min(prescMax, espMax) - Math.max(prescMin, espMin));
        int larguraPrescrita = Math.max(1, prescMax - prescMin);
        double overlapPct = (double) overlap / larguraPrescrita;

        if (overlapPct < 0.50) {
            // Corrigir para o quartil central da zona esperada
            int amplitude = espMax - espMin;
            int centroMin = espMin + amplitude / 4;
            int centroMax = espMax - amplitude / 4;
            String fcCorrigida = centroMin + "-" + centroMax + " bpm";
            log.warn("FC fora da zona esperada: tipo='{}', prescrito='{}', esperado='{}-{} bpm', corrigindo para '{}'",
                    etapa.tipoEtapa(), etapa.fcAlvoEtapa(), espMin, espMax, fcCorrigida);
            return new EtapaTreinoLlmDto(
                    etapa.ordem(), etapa.tipoEtapa(), etapa.descricaoEtapa(),
                    etapa.duracaoMin(), etapa.distanciaKm(), fcCorrigida, etapa.repeticoes(), etapa.ritmoAlvo()
            );
        }
        return etapa;
    }

    /**
     * Valida treino intervalado: mínimo 8 etapas, tiros e recuperações balanceados
     */
    private void validarTreinoIntervalado(br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto treino, Object atletaId) {
        var etapas = treino.etapas();

        // 1) Existência e quantidade mínima de etapas
        if (etapas == null || etapas.isEmpty()) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} sem etapas",
                    atletaId, treino.tipoTreino());
            throw new LLMException(String.format(
                    "Treino %s inválido: não foram geradas etapas",
                    treino.tipoTreino()
            ));
        }

        if (etapas.size() < 6) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} tem apenas {} etapas (mínimo 8)",
                    atletaId, treino.tipoTreino(), etapas.size());
            throw new LLMException(String.format(
                    "Treino %s inválido: gerou apenas %d etapas (mínimo 6 para intervalados)",
                    treino.tipoTreino(), etapas.size()
            ));
        }

        // 2) Tipos básicos de etapa
        boolean temAquecimento = etapas.stream()
                .anyMatch(e -> "AQUECIMENTO".equals(e.tipoEtapa()));
        boolean temDesaquecimento = etapas.stream()
                .anyMatch(e -> "DESAQUECIMENTO".equals(e.tipoEtapa()));

        if (!temAquecimento) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não possui etapa de aquecimento",
                    atletaId, treino.tipoTreino());
            throw new LLMException(String.format(
                    "Treino %s inválido: não possui etapa de aquecimento",
                    treino.tipoTreino()
            ));
        }

        if (!temDesaquecimento) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não possui etapa de desaquecimento",
                    atletaId, treino.tipoTreino());
            throw new LLMException(String.format(
                    "Treino %s inválido: não possui etapa de desaquecimento",
                    treino.tipoTreino()
            ));
        }

        // 3) Ordem lógica: primeiro AQUECIMENTO, último DESAQUECIMENTO
        var primeiraEtapa = etapas.get(0);
        var ultimaEtapa   = etapas.get(etapas.size() - 1);

        if (!"AQUECIMENTO".equals(primeiraEtapa.tipoEtapa())) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não inicia com aquecimento (inicia com {})",
                    atletaId, treino.tipoTreino(), primeiraEtapa.tipoEtapa());
            throw new LLMException(String.format(
                    "Treino %s inválido: deve iniciar com aquecimento",
                    treino.tipoTreino()
            ));
        }

        if (!"DESAQUECIMENTO".equals(ultimaEtapa.tipoEtapa())) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não termina com desaquecimento (termina com {})",
                    atletaId, treino.tipoTreino(), ultimaEtapa.tipoEtapa());
            throw new LLMException(String.format(
                    "Treino %s inválido: deve terminar com desaquecimento",
                    treino.tipoTreino()
            ));
        }

        // 4) Contar tiros e recuperações
        long numTiros = etapas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa()))
                .count();
        long numRecuperacoes = etapas.stream()
                .filter(e -> "RECUPERACAO".equals(e.tipoEtapa()))
                .count();

        if (numTiros < 3) {
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Treino {} tem apenas {} tiros (recomendado: 3+)",
                    atletaId, treino.tipoTreino(), numTiros);
        }

        // Balanceamento tiros x recuperações (pode ter 1 rec a menos se o último tiro não tiver rec)
        if (Math.abs(numTiros - numRecuperacoes) > 1) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} desbalanceado: {} tiros vs {} recuperações",
                    atletaId, treino.tipoTreino(), numTiros, numRecuperacoes);
            throw new LLMException(String.format(
                    "Treino %s inválido: %d tiros mas %d recuperações (devem ser iguais ou diferença de 1)",
                    treino.tipoTreino(), numTiros, numRecuperacoes
            ));
        }

        // 5) Validar sequência: recuperação só pode vir após tiro
        boolean ultimoFoiTiro = false;
        boolean jaTeveTiro = false;
        int recuperacoesInvalidas = 0;

        for (var etapa : etapas) {
            String tipo = etapa.tipoEtapa();

            if ("INTERVALADO".equals(tipo)) {
                jaTeveTiro = true;
                ultimoFoiTiro = true;
            } else if ("RECUPERACAO".equals(tipo)) {
                if (!ultimoFoiTiro) {
                    recuperacoesInvalidas++;
                }
                ultimoFoiTiro = false;
            } else if ("AQUECIMENTO".equals(tipo)) {
                // Se aquecimento for gerado depois de tiro, é estranho (mas vamos só logar)
                if (jaTeveTiro) {
                    log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Aquecimento após tiro detectado em treino {}",
                            atletaId, treino.tipoTreino());
                }
                ultimoFoiTiro = false;
            } else if ("DESAQUECIMENTO".equals(tipo)) {
                // Desaquecimento antes de qualquer tiro também é estranho
                if (!jaTeveTiro) {
                    log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Desaquecimento antes de qualquer tiro em treino {}",
                            atletaId, treino.tipoTreino());
                }
                ultimoFoiTiro = false;
            } else {
                // Outros tipos, se existirem
                ultimoFoiTiro = false;
            }
        }

        if (recuperacoesInvalidas > 0) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} possui {} recuperações sem tiro anterior",
                    atletaId, treino.tipoTreino(), recuperacoesInvalidas);
            throw new LLMException(String.format(
                    "Treino %s inválido: existem recuperações sem tiro imediatamente anterior",
                    treino.tipoTreino()
            ));
        }

        // 6) Distâncias: soma total e proporções
        double somaDistancias = etapas.stream()
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();
        double distanciaPlanejada = treino.distanciaKm() != null ? treino.distanciaKm() : 0.0;

        double diferenca = Math.abs(somaDistancias - distanciaPlanejada);
        if (diferenca > 0.5) { // Tolerância de 500m
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Soma das etapas ({} km) difere da distância planejada ({} km) em {} km",
                    atletaId, somaDistancias, distanciaPlanejada, diferenca);
        }

        double distanciaTiros = etapas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa()))
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();

        double distanciaRecuperacoes = etapas.stream()
                .filter(e -> "RECUPERACAO".equals(e.tipoEtapa()))
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();

        if (distanciaPlanejada > 0.0) {
            // Pelo menos 20% da distância em tiros (garante estímulo mínimo)
            if (distanciaTiros < distanciaPlanejada * 0.20) {
                log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Distância em tiros ({}) muito baixa para total de {} km no treino {}",
                        atletaId, distanciaTiros, distanciaPlanejada, treino.tipoTreino());
            }

            // Recuperação não deve ser a maior parte do treino
            if (distanciaRecuperacoes > distanciaPlanejada * 0.65) {
                log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Distância em recuperação ({}) muito alta para total de {} km no treino {}",
                        atletaId, distanciaRecuperacoes, distanciaPlanejada, treino.tipoTreino());
            }
        }

        // 7) Duração dos tiros (coerência fisiológica geral)
        long tirosInvalidos = etapas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa()))
                .filter(e -> {
                    if (e.duracaoMin() == null) return true;
                    double duracao = e.duracaoMin();
                    // mínimo ~18s (0.3 min) e máximo 10 min
                    return duracao < 0.3 || duracao > 10.0;
                })
                .count();

        if (tirosInvalidos > 0) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} possui {} tiros com duração incoerente",
                    atletaId, treino.tipoTreino(), tirosInvalidos);
            throw new LLMException(String.format(
                    "Treino %s inválido: existem tiros com duração incoerente (muito curtos ou muito longos)",
                    treino.tipoTreino()
            ));
        }

        // 8) Log final de sucesso
        log.info("VALIDAÇÃO OK [Atleta {}]: Treino {} - {} etapas ({} tiros, {} recuperações, {} km - tiros: {} km, rec: {} km)",
                atletaId,
                treino.tipoTreino(),
                etapas.size(),
                numTiros,
                numRecuperacoes,
                somaDistancias,
                distanciaTiros,
                distanciaRecuperacoes
        );
    }

    /**
     * Validação estrutural compartilhada de treinos "3 etapas" (AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO).
     * Hard-fail (lança {@link LLMException}) em: número de etapas ≠ 3 e — quando {@code validarOrdem} —
     * AQUECIMENTO/DESAQUECIMENTO fora de posição. Unifica REGENERATIVO/CONTINUO/TEMPO_RUN/LONGO
     * (LONGO valida só a contagem, por isso o flag). As regras não mudam — só deixam de ser copiadas.
     */
    void validarEstrutura3Etapas(TreinoPlanejadoLlmDto treino, String tipo, Object atletaId, boolean validarOrdem) {
        var etapas = treino.etapas();
        if (etapas == null || etapas.size() != 3) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} tem {} etapas (esperado: 3)",
                    atletaId, tipo, etapas != null ? etapas.size() : 0);
            contarViolacaoEstrutural(tipo);
            throw new LLMException(String.format(
                    "Treino %s inválido: gerou %d etapas (esperado 3: aquec, principal, desaq)",
                    tipo, etapas != null ? etapas.size() : 0));
        }
        if (validarOrdem
                && (!"AQUECIMENTO".equals(etapas.get(0).tipoEtapa()) || !"DESAQUECIMENTO".equals(etapas.get(2).tipoEtapa()))) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} fora de ordem: [{}→{}→{}]",
                    atletaId, tipo, etapas.get(0).tipoEtapa(), etapas.get(1).tipoEtapa(), etapas.get(2).tipoEtapa());
            contarViolacaoEstrutural(tipo);
            throw new LLMException(String.format(
                    "Treino %s inválido: deve ser AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO", tipo));
        }
    }

    /** Telemetria: violação estrutural residual (não reparada) por tipo de treino. */
    private void contarViolacaoEstrutural(String tipo) {
        Counter.builder("plano_violacao_estrutural").tag("tipo", tipo).register(meterRegistry).increment();
    }

    private void validarTreinoLongo(br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto treino, Object atletaId) {
        validarEstrutura3Etapas(treino, "LONGO", atletaId, false);
        log.info("VALIDAÇÃO OK [Atleta {}]: Treino LONGO - 3 etapas conforme esperado", atletaId);
    }

    /**
     * Valida que todas as etapas têm repeticoes = 1
     */
    private void validarRepeticoes(br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto treino, Object atletaId) {
        var etapas = treino.etapas();
        if (etapas == null) return;

        etapas.forEach(etapa -> {
            if (etapa.repeticoes() != null && etapa.repeticoes() != 1) {
                log.error("VALIDAÇÃO FALHOU [Atleta {}]: Etapa '{}' tem repeticoes={} (deve ser sempre 1)",
                        atletaId, etapa.descricaoEtapa(), etapa.repeticoes());
                throw new LLMException(String.format(
                        "Etapa '%s' inválida: repeticoes=%d (deve ser sempre 1 - expandir etapas individualmente)",
                        etapa.descricaoEtapa(), etapa.repeticoes()
                ));
            }
        });
    }

    // ======================== P2-B — TRIÂNGULO pace × distância × duração ========================

    /**
     * Valida a consistência entre ritmoAlvo, distanciaKm e duracaoMin (identidade física).
     * Se desvio > 20%, registra WARN. Não corrige: os três valores são prescrições do LLM e
     * nenhum deles tem precedência clara sobre os outros.
     */
    private void validarTrianguloPaceDuracaoDistancia(TreinoPlanejadoLlmDto treino) {
        if (treino.ritmoAlvo() == null || treino.distanciaKm() == null || treino.duracaoMin() == null) return;

        var paceMediaOpt = paceValidator.calcularPaceMedia(treino.ritmoAlvo());
        if (paceMediaOpt.isEmpty()) return;

        double distanciaKm = treino.distanciaKm();
        if (distanciaKm <= 0) return;

        var mDuracao = java.util.regex.Pattern.compile("^(\\d{1,3}):(\\d{2})$").matcher(treino.duracaoMin().trim());
        if (!mDuracao.matches()) return;
        double duracaoMin;
        try {
            duracaoMin = Integer.parseInt(mDuracao.group(1)) + Integer.parseInt(mDuracao.group(2)) / 60.0;
        } catch (NumberFormatException e) {
            return;
        }
        if (duracaoMin <= 0) return;

        double paceMedia = paceMediaOpt.getAsDouble();
        double duracaoEsperada = paceMedia * distanciaKm;
        double desvio = Math.abs(duracaoEsperada - duracaoMin) / duracaoEsperada;

        if (desvio > 0.20) {
            log.warn("TRIÂNGULO pace×dist×dur [{}]: ritmoAlvo='{}', dist={} km, duracao={} min → esperado {} min (desvio {}%)",
                    treino.tipoTreino(), treino.ritmoAlvo(), distanciaKm, duracaoMin,
                    String.format("%.1f", duracaoEsperada), String.format("%.0f", desvio * 100));
        }
    }

    // ======================== P3-A — VALIDAÇÃO ESTRUTURAL POR TIPO ========================

    /**
     * Valida treino REGENERATIVO: 3 etapas (AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO),
     * duração 20–45 min.
     */
    private void validarTreinoRegenerativo(TreinoPlanejadoLlmDto treino, Object atletaId) {
        validarEstrutura3Etapas(treino, "REGENERATIVO", atletaId, true);

        if (treino.duracaoMin() != null) {
            var m = java.util.regex.Pattern.compile("^(\\d{1,3}):(\\d{2})$").matcher(treino.duracaoMin().trim());
            if (m.matches()) {
                try {
                    int minutos = Integer.parseInt(m.group(1));
                    if (minutos > 45) {
                        log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Treino REGENERATIVO com {} min (máximo recomendado: 45 min)",
                                atletaId, minutos);
                    } else if (minutos < 20) {
                        log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Treino REGENERATIVO com {} min (mínimo recomendado: 20 min)",
                                atletaId, minutos);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        log.info("VALIDAÇÃO OK [Atleta {}]: Treino REGENERATIVO - 3 etapas conforme esperado", atletaId);
    }

    /**
     * Valida treino CONTINUO: 3 etapas (AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO),
     * distância mínima de 5 km.
     */
    private void validarTreinoContinuo(TreinoPlanejadoLlmDto treino, Object atletaId) {
        validarEstrutura3Etapas(treino, "CONTINUO", atletaId, true);

        if (treino.distanciaKm() != null && treino.distanciaKm() < 5.0) {
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Treino CONTINUO com {} km (mínimo recomendado: 5 km)",
                    atletaId, treino.distanciaKm());
        }

        log.info("VALIDAÇÃO OK [Atleta {}]: Treino CONTINUO - 3 etapas conforme esperado", atletaId);
    }

    /**
     * Valida treino TEMPO_RUN: 3 etapas (AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO),
     * PRINCIPAL mínimo 15 min, ritmoAlvo do PRINCIPAL dentro de ±10% do paceLimiar do atleta.
     */
    private void validarTreinoTempoRun(TreinoPlanejadoLlmDto treino, Object atletaId,
                                        br.com.menthoros.backend.entity.Atleta atleta) {
        validarEstrutura3Etapas(treino, "TEMPO_RUN", atletaId, true);

        var etapaPrincipal = treino.etapas().get(1);

        if (etapaPrincipal.duracaoMin() != null && etapaPrincipal.duracaoMin() < 15) {
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: TEMPO_RUN principal com {} min (mínimo para indução de limiar: 15 min)",
                    atletaId, etapaPrincipal.duracaoMin());
        }

        if (atleta.getPaceLimiar() != null && etapaPrincipal.ritmoAlvo() != null) {
            var paceMediaOpt = paceValidator.calcularPaceMedia(etapaPrincipal.ritmoAlvo());
            if (paceMediaOpt.isPresent()) {
                double paceMedia = paceMediaOpt.getAsDouble();
                double limiar = atleta.getPaceLimiar().doubleValue();
                double tolerancia = limiar * 0.10;
                if (paceMedia < limiar - tolerancia || paceMedia > limiar + tolerancia) {
                    log.warn("VALIDAÇÃO ALERTA [Atleta {}]: TEMPO_RUN principal ritmoAlvo='{}' (média={} min/km) fora da faixa limiar ±10% [{}-{} min/km]",
                            atletaId, etapaPrincipal.ritmoAlvo(),
                            String.format("%.2f", paceMedia),
                            String.format("%.2f", limiar - tolerancia),
                            String.format("%.2f", limiar + tolerancia));
                }
            }
        }

        log.info("VALIDAÇÃO OK [Atleta {}]: Treino TEMPO_RUN - 3 etapas conforme esperado", atletaId);
    }

    // ======================== P3-B — DISTRIBUIÇÃO DE CARGA SEMANAL ========================

    /**
     * Verifica se existem treinos "duros" (INTERVALADO, TIRO, TEMPO_RUN) em dias consecutivos
     * e registra WARN. Não rejeita o plano — apenas alerta.
     */
    private void validarDistribuicaoCargaSemanal(List<TreinoPlanejadoLlmDto> treinos) {
        if (treinos == null || treinos.size() < 2) return;

        java.util.Set<String> tiposDuros = java.util.Set.of("INTERVALADO", "TIRO", "TEMPO_RUN", "LONGO");

        java.util.Map<Integer, String> ordemParaTipo = new java.util.TreeMap<>();
        for (TreinoPlanejadoLlmDto treino : treinos) {
            if (treino.diaSemana() == null || treino.tipoTreino() == null) continue;
            try {
                DiaSemana dia = DiaSemana.valueOf(treino.diaSemana().toUpperCase());
                ordemParaTipo.put(dia.getOrder(), treino.tipoTreino());
            } catch (IllegalArgumentException ignored) {}
        }

        List<java.util.Map.Entry<Integer, String>> entradas = new java.util.ArrayList<>(ordemParaTipo.entrySet());
        for (int i = 0; i < entradas.size() - 1; i++) {
            var atual = entradas.get(i);
            var proximo = entradas.get(i + 1);
            if ((proximo.getKey() - atual.getKey()) == 1
                    && tiposDuros.contains(atual.getValue())
                    && tiposDuros.contains(proximo.getValue())) {
                DiaSemana diaAtual   = diaPorOrdem(atual.getKey());
                DiaSemana diaProximo = diaPorOrdem(proximo.getKey());
                log.warn("CARGA SEMANAL: treinos duros em dias consecutivos — {} ({}) e {} ({})",
                        diaAtual   != null ? diaAtual.getLabel()   : atual.getKey(),   atual.getValue(),
                        diaProximo != null ? diaProximo.getLabel() : proximo.getKey(), proximo.getValue());
            }
        }
    }

    private DiaSemana diaPorOrdem(int order) {
        for (DiaSemana d : DiaSemana.values()) {
            if (d.getOrder() == order) return d;
        }
        return null;
    }

    @Override
    public Map<Long, PlanoTreinoOutputDto> gerarPlanosEmLote(Map<AtletaOutputDto, List<TreinoRealizadoOutputDto>> atletaDtoListMap) {
        log.warn("Método gerarPlanosEmLote ainda não implementado");
        return Map.of();
    }

}
