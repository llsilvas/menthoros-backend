package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.ai.ledger.Violacao;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.exception.PlanoNaoConformeException;
import br.com.menthoros.backend.services.helper.PlanoLlmLedgerHook;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.services.IaService;
import br.com.menthoros.backend.services.helper.LlmUsageLogger;
import br.com.menthoros.backend.services.helper.RegraGeracaoTreino;
import br.com.menthoros.backend.services.helper.PlanoResilienceService;
import br.com.menthoros.backend.services.helper.PlannerShadowService;
import br.com.menthoros.backend.services.helper.PlanoLlmValidator;
import br.com.menthoros.backend.services.helper.AthleteZones;
import br.com.menthoros.backend.services.helper.SchemaVersionResolver;
import br.com.menthoros.backend.services.helper.SessionResolver;
import br.com.menthoros.backend.services.helper.RepairTurnMessageBuilder;
import br.com.menthoros.backend.dto.llm.v2.PlanoSemanalLlmDtoV2;
import br.com.menthoros.backend.domain.compliance.SchemaVersion;
import br.com.menthoros.backend.domain.compliance.PlannerViolation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import br.com.menthoros.backend.services.prompt.LlmJsonSchemaBuilder;
import br.com.menthoros.backend.services.prompt.PlanoTreinoPromptBuilder;
import br.com.menthoros.backend.services.quality.PlanQualityChecker;
import br.com.menthoros.backend.services.quality.ViolacaoQualidade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Primary
public class IaServiceImpl implements IaService {

    private final ModelRouter modelRouter;
    private final PlanoTreinoPromptBuilder promptBuilder;
    private final LlmJsonSchemaBuilder llmJsonSchemaBuilder;
    private final AtletaRepository atletaRepository;
    private final RegraGeracaoTreino regraGeracaoTreino;
    private final PlanQualityChecker planQualityChecker;
    private final PlanoLlmValidator planoLlmValidator;
    private final PlanoResilienceService planoResilienceService;
    private final MeterRegistry meterRegistry;
    private final LlmUsageLogger llmUsageLogger;
    private final PlannerShadowService plannerShadowService;
    private final PlanoLlmLedgerHook ledgerHook;
    private final RepairTurnMessageBuilder repairTurnMessageBuilder;
    private final ObjectMapper objectMapper;
    private final SchemaVersionResolver schemaVersionResolver;
    private final SessionResolver sessionResolver;

    public IaServiceImpl(ModelRouter modelRouter, PlanoTreinoPromptBuilder promptBuilder,
                         LlmJsonSchemaBuilder llmJsonSchemaBuilder,
                         AtletaRepository atletaRepository, RegraGeracaoTreino regraGeracaoTreino,
                         PlanQualityChecker planQualityChecker,
                         PlanoLlmValidator planoLlmValidator,
                         PlanoResilienceService planoResilienceService,
                         MeterRegistry meterRegistry,
                         LlmUsageLogger llmUsageLogger,
                         PlannerShadowService plannerShadowService,
                         PlanoLlmLedgerHook ledgerHook,
                         RepairTurnMessageBuilder repairTurnMessageBuilder,
                         ObjectMapper objectMapper,
                         SchemaVersionResolver schemaVersionResolver,
                         SessionResolver sessionResolver) {
        this.modelRouter = modelRouter;
        this.promptBuilder = promptBuilder;
        this.atletaRepository = atletaRepository;
        this.llmJsonSchemaBuilder = llmJsonSchemaBuilder;
        this.regraGeracaoTreino = regraGeracaoTreino;
        this.planQualityChecker = planQualityChecker;
        this.planoLlmValidator = planoLlmValidator;
        this.planoResilienceService = planoResilienceService;
        this.meterRegistry = meterRegistry;
        this.llmUsageLogger = llmUsageLogger;
        this.plannerShadowService = plannerShadowService;
        this.ledgerHook = ledgerHook;
        this.repairTurnMessageBuilder = repairTurnMessageBuilder;
        this.objectMapper = objectMapper;
        this.schemaVersionResolver = schemaVersionResolver;
        this.sessionResolver = sessionResolver;
    }

    /**
     * Gera o plano semanal via LLM sem o roteamento avançado (prompt legado, sem retry/resiliência
     * nem ledger) — caminho mais simples, mantido por compatibilidade com chamadores que ainda não
     * migraram para {@link #geraPlanoSemanalAvancado}.
     *
     * Idempotent: NO — invoca o LLM (saída não-determinística).
     * Side Effects: chamada ao LLM (billable).
     * Tenant-aware: YES — {@code validarENormalizarPlanoGerado} opera sob o {@code TenantContext} corrente.
     */
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

        // semantic-session-schema: resolvido uma vez por geração (allowlist de tenant), não a cada
        // tentativa de retry — o schema (v1/v2) não muda entre a 1ª e a 2ª tentativa.
        boolean usaV2 = schemaVersionResolver.usaV2(TenantContext.getRequiredTenantId());
        String schemaVersion = usaV2 ? SchemaVersion.V2 : SchemaVersion.CURRENT;

        var promptGerado = promptBuilder.buildOptimizedPrompt(atleta, metaDados, prova, inicioSemana, diasEfetivos, decisaoProgressao, revisaoConsumida, skeleton, usaV2);
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
                    t -> sessao.chamar(t.numero(), schemaVersion,
                            () -> usaV2 ? gerarChamadaLlmV2(chatClient, system, t, atleta)
                                        : gerarChamadaLlm(chatClient, system, t)),
                    p -> sessao.validar(() -> aplicarComplianceEstagio1(
                            usaV2 ? planoLlmValidator.validarPlanoV2(p, atleta, atleta.getId(), skeleton)
                                  : validarENormalizarPlanoGerado(p, atleta.getId()),
                            atleta, skeleton, inicioSemana)),
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
     * Chama a LLM: 1ª tentativa é {@code system + user} simples; a partir da 2ª (turno de reparo,
     * plan-generation-repair-turn) a conversa acrescenta o JSON da tentativa anterior como
     * {@code AssistantMessage} e as violações completas como a última {@code UserMessage} — o
     * {@code system}/{@code user} original nunca são reescritos (design.md, Decisão 1).
     *
     * <p>Sem {@code .responseEntity(Class)}/{@code BeanOutputConverter} de propósito: o converter
     * embutido injeta texto de formato na última {@code UserMessage} da conversa, o que quebraria
     * a igualdade de prefixo entre tentativas (achado do pré-mortem, confirmado por bytecode do
     * Spring AI 1.1.6 e provado em {@code ChatClientRepairTurnPrefixSpikeTest}). O parse do JSON
     * é manual, com guarda de conteúdo nulo/vazio: preserva o caminho de retry de hoje (entidade
     * {@code null} chega a {@code validar}, que já rejeita com {@link LLMException}) em vez de
     * lançar dentro desta função (que não teria retry).</p>
     *
     * <p>Idempotent: NO — invoca o LLM. Side Effects: chamada ao LLM (billable). Tenant-aware: NO.</p>
     */
    private PlanoResilienceService.ChamadaLlm gerarChamadaLlm(ChatClient chatClient, String system,
                                                              PlanoResilienceService.Tentativa tentativa) {
        String json = chamarLlm(chatClient, system, tentativa, llmJsonSchemaBuilder.defaultJsonSchemaOptions());
        PlanoSemanalLlmDto entidade = (json == null || json.isBlank()) ? null : parsearPlano(json);
        return new PlanoResilienceService.ChamadaLlm(entidade, json);
    }

    /**
     * Monta a conversa (system + user na 1ª tentativa; system + user + assistant(json anterior) +
     * user(correção) nas seguintes) e faz a chamada, devolvendo o texto bruto da resposta (ou
     * {@code null} se vazia) — reusado por {@link #gerarChamadaLlm} (v1) e
     * {@link #gerarChamadaLlmV2}, que só variam o {@code options} (schema) e o parse do retorno
     * (achado do /qa: as ~15 linhas de montagem de conversa e extração de texto eram idênticas nos
     * dois, risco de um fix futuro — como o de {@code jsonAnteriorOuFallback} — divergir entre eles
     * se aplicado só num).
     */
    private String chamarLlm(ChatClient chatClient, String system, PlanoResilienceService.Tentativa tentativa,
                              ChatOptions options) {
        ChatClient.ChatClientRequestSpec pedido = tentativa.numero() == 1
                ? chatClient.prompt().system(system).user(tentativa.promptOriginal())
                : chatClient.prompt().messages(List.of(
                        new SystemMessage(system),
                        new UserMessage(tentativa.promptOriginal()),
                        new AssistantMessage(jsonAnteriorOuFallback(tentativa.jsonAnterior())),
                        new UserMessage(repairTurnMessageBuilder.construirCorrecao(tentativa.violacoesAnteriores()))));

        ChatResponse resposta = pedido.options(options).call().chatResponse();
        llmUsageLogger.registrar(resposta); // best-effort, nunca lança

        return resposta != null && resposta.getResult() != null && resposta.getResult().getOutput() != null
                ? resposta.getResult().getOutput().getText() : null;
    }

    /**
     * {@code jsonAnterior} é {@code null} quando a 1ª tentativa devolveu resposta vazia/sem
     * conteúdo (achado do `/qa`: dois revisores independentes — {@code content()} nulo/vazio não
     * lança dentro de {@code gerarChamadaLlm}, então a validação estrutural a jusante rejeita com
     * {@code LLMException} genérica, sem {@code PlanoNaoConformeException}, e {@code jsonAnterior}
     * segue nulo para a 2ª tentativa). Sem esta guarda, {@code new AssistantMessage(null)} não
     * lança (Spring AI só valida não-nulo em SYSTEM/USER), mas envia um turno de assistente vazio
     * ao modelo, contrariando o design ("o JSON completo da tentativa anterior viaja como
     * AssistantMessage").
     */
    private static String jsonAnteriorOuFallback(@org.jspecify.annotations.Nullable String jsonAnterior) {
        // isBlank(), não só != null: content() pode vir "" ou só espaços (não-nulo, mas igualmente
        // sem conteúdo) — achado do /qa, 2ª verificação: o guard original só cobria null.
        return jsonAnterior != null && !jsonAnterior.isBlank() ? jsonAnterior
                : "(a tentativa anterior não devolveu conteúdo — resposta vazia do modelo)";
    }

    private PlanoSemanalLlmDto parsearPlano(String json) {
        try {
            return objectMapper.readValue(json, PlanoSemanalLlmDto.class);
        } catch (JsonProcessingException e) {
            // JSON não-vazio malformado: lança dentro de gerar → propaga sem retry (mesmo
            // comportamento de hoje, quando o converter do Spring AI lançava no mesmo ponto).
            throw new LLMException("Resposta da LLM não é um JSON válido: " + e.getMessage(), e);
        }
    }

    /**
     * Branch v2 (semantic-session-schema) de {@link #gerarChamadaLlm} — mesma estrutura de
     * conversa (system + user na 1ª tentativa; system + user + assistant(json anterior) +
     * user(correção) nas seguintes), só o schema pedido e o parse mudam. Logo após o parse,
     * {@link SessionResolver#resolverPlano} converte {@link PlanoSemanalLlmDtoV2} (blocos) para o
     * shape v1 (etapas absolutas) — **sem validar nada** (design.md, Decisão 3): o resto do
     * pipeline (turno de reparo, validação, persistência) recebe um {@code PlanoSemanalLlmDto}
     * normal, sem saber que veio de v2.
     */
    private PlanoResilienceService.ChamadaLlm gerarChamadaLlmV2(ChatClient chatClient, String system,
                                                                 PlanoResilienceService.Tentativa tentativa,
                                                                 Atleta atleta) {
        String json = chamarLlm(chatClient, system, tentativa, llmJsonSchemaBuilder.v2JsonSchemaOptions());

        if (json == null || json.isBlank()) {
            return new PlanoResilienceService.ChamadaLlm(null, json);
        }
        PlanoSemanalLlmDtoV2 planoV2 = parsearPlanoV2(json);
        AthleteZones zonas = new AthleteZones(
                atleta.getFcMaximaCalculada(), atleta.getFcLimiarCalculada(), atleta.getPaceLimiar());
        // Defesa em profundidade (achado do /qa, pré-mortem codex): o schema v2 já limita
        // repeticoes/quantidadePorRepeticao (LlmJsonSchemaBuilder.buildSchemaV2) para que a
        // resolução nunca estoure Integer — mas resolverPlano roda dentro de `gerar`, fora do
        // escopo de retry, então uma exceção de aritmética aqui (ex. um provedor que não honra
        // strict:true) mataria a geração sem chance de reparo. Tratada como resposta malformada
        // (mesma categoria de JSON inválido, task 2.4 — não retry-elegível, comportamento já
        // existente para infra/parse).
        PlanoSemanalLlmDto resolvido;
        try {
            resolvido = sessionResolver.resolverPlano(planoV2, zonas);
        } catch (ArithmeticException e) {
            throw new LLMException("Resposta da LLM (schema v2) tem valores numéricos inválidos: " + e.getMessage(), e);
        }
        return new PlanoResilienceService.ChamadaLlm(resolvido, json);
    }

    private PlanoSemanalLlmDtoV2 parsearPlanoV2(String json) {
        try {
            return objectMapper.readValue(json, PlanoSemanalLlmDtoV2.class);
        } catch (JsonProcessingException e) {
            throw new LLMException("Resposta da LLM (schema v2) não é um JSON válido: " + e.getMessage(), e);
        }
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
     * Resolve o atleta (tenant-aware) e delega a validação/normalização completa do plano gerado
     * pela LLM a {@link PlanoLlmValidator#validarENormalizarPlano} (refactor-iaservice-decomposition,
     * seções 5-6).
     */
    private PlanoSemanalLlmDto validarENormalizarPlanoGerado(PlanoSemanalLlmDto plano, UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new LLMException("Atleta não encontrado"));
        return planoLlmValidator.validarENormalizarPlano(plano, atleta, atletaId);
    }

}
