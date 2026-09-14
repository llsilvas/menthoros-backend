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
import br.com.menthoros.backend.domain.compliance.PlannerViolation;
import io.micrometer.core.instrument.MeterRegistry;
import br.com.menthoros.backend.services.prompt.LlmJsonSchemaBuilder;
import br.com.menthoros.backend.services.prompt.PlanoTreinoPromptBuilder;
import br.com.menthoros.backend.services.quality.PlanQualityChecker;
import br.com.menthoros.backend.services.quality.ViolacaoQualidade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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

    public IaServiceImpl(ModelRouter modelRouter, PlanoTreinoPromptBuilder promptBuilder,
                         LlmJsonSchemaBuilder llmJsonSchemaBuilder,
                         AtletaRepository atletaRepository, RegraGeracaoTreino regraGeracaoTreino,
                         PlanQualityChecker planQualityChecker,
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
        this.planQualityChecker = planQualityChecker;
        this.planoLlmValidator = planoLlmValidator;
        this.planoResilienceService = planoResilienceService;
        this.meterRegistry = meterRegistry;
        this.llmUsageLogger = llmUsageLogger;
        this.plannerShadowService = plannerShadowService;
        this.ledgerHook = ledgerHook;
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
                        // TODO(plan-generation-repair-turn, seção 4): ainda usa .responseEntity() e
                        // ignora t.jsonAnterior()/t.violacoesAnteriores() — adoção mínima da nova
                        // assinatura de PlanoResilienceService.Tentativa/ChamadaLlm (seção 2) para
                        // manter a compilação; a task 4.0 troca para .call().content() + parse
                        // manual e a 4.2 monta a conversa multi-mensagem no turno de reparo.
                        var resposta = chatClient.prompt().system(system).user(t.promptOriginal())
                                .options(llmJsonSchemaBuilder.defaultJsonSchemaOptions())
                                .call().responseEntity(PlanoSemanalLlmDto.class);
                        llmUsageLogger.registrar(resposta.getResponse()); // best-effort, nunca lança
                        String jsonBruto = resposta.getResponse() != null
                                && resposta.getResponse().getResult() != null
                                && resposta.getResponse().getResult().getOutput() != null
                                ? resposta.getResponse().getResult().getOutput().getText() : null;
                        return new PlanoResilienceService.ChamadaLlm(resposta.getEntity(), jsonBruto);
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
