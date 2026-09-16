package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.compliance.SchemaVersion;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.services.prompt.LlmJsonSchemaBuilder;
import br.com.menthoros.backend.services.prompt.PlanoTreinoPromptBuilder.PromptGerado;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Modo candidato do eval set (plan-generation-eval-set, task 1.7) — chama a LLM real com o
 * prompt/schema **do código atual do checkout** e grada a resposta nova com
 * {@link EvalDeterministicGrader}. Nunca passa por {@code IaServiceImpl}/
 * {@code PlanoResilienceService} (design.md §4 — zero acoplamento ao pipeline de produção).
 *
 * <p>Não é {@code @Component}: quem o instancia (teste `@Tag("eval")` ou execução manual) escolhe
 * o {@link ChatClient} — o mesmo `ModelRouter.route(TaskComplexity.PLANO)` que produção usa, para
 * exercitar o modelo real de plano, não um substituto.
 *
 * <p>Suporta v1 e v2 via o parâmetro {@code usaV2} de {@link #rodar(PromptGerado, WeekPlanSkeleton,
 * AthleteZones, Atleta, LocalDate, boolean)} — achado do /qa (Codex): a versão anterior só pedia
 * v1, então uma mudança de schema v2 nunca seria exercitada pelo gate. Nenhum arquétipo de
 * candidato usa v2 hoje (ver proposal.md Open Questions) — a capacidade existe, falta um
 * arquétipo que a exercite.
 *
 * <p>Idempotent: NÃO — cada chamada é uma nova geração real. Side Effects: chamada de rede à LLM
 * (custo real). Tenant-aware: NÃO — opera sobre fixtures sintéticas, sem tenant real.
 */
public class EvalCandidateRunner {

    private final ChatClient chatClient;
    private final LlmJsonSchemaBuilder llmJsonSchemaBuilder;
    private final EvalDeterministicGrader grader;

    public EvalCandidateRunner(ChatClient chatClient, LlmJsonSchemaBuilder llmJsonSchemaBuilder,
                                EvalDeterministicGrader grader) {
        this.chatClient = chatClient;
        this.llmJsonSchemaBuilder = llmJsonSchemaBuilder;
        this.grader = grader;
    }

    public record ResultadoCandidato(String responseJson, EvalDeterministicGrader.Resultado avaliacao,
                                      @Nullable BigDecimal custoUsd) {
    }

    /** Overload v1 (compatibilidade) — equivalente a {@code rodar(..., false)}. */
    public ResultadoCandidato rodar(PromptGerado prompt, @Nullable WeekPlanSkeleton skeleton,
                                     AthleteZones zonasAtleta, Atleta atleta, LocalDate semanaInicio) {
        return rodar(prompt, skeleton, zonasAtleta, atleta, semanaInicio, false);
    }

    /**
     * Chama a LLM com o prompt já montado e grada a resposta. {@code usaV2} escolhe o schema v1
     * ou v2 (achado do /qa, Codex: a versão anterior sempre pedia v1, então uma mudança de schema
     * v2 nunca era exercitada pelo gate). Custo calculado em memória a partir do {@code Usage}
     * desta chamada (design.md §2) — {@code null} se o modelo não tiver preço em
     * {@code llm-pricing.yml}. Idempotent: NÃO. Side Effects: chamada real à LLM.
     */
    public ResultadoCandidato rodar(PromptGerado prompt, @Nullable WeekPlanSkeleton skeleton,
                                     AthleteZones zonasAtleta, Atleta atleta, LocalDate semanaInicio,
                                     boolean usaV2) {
        var options = usaV2 ? llmJsonSchemaBuilder.v2JsonSchemaOptions() : llmJsonSchemaBuilder.defaultJsonSchemaOptions();
        ChatResponse chatResponse = chatClient.prompt()
                .system(prompt.system())
                .user(prompt.user())
                .options(options)
                .call()
                .chatResponse();
        String responseJson = chatResponse != null && chatResponse.getResult() != null
                && chatResponse.getResult().getOutput() != null
                ? chatResponse.getResult().getOutput().getText() : null;
        if (responseJson == null || responseJson.isBlank()) {
            throw new IllegalStateException("LLM não retornou conteúdo para o modo candidato do eval set");
        }
        String schemaVersion = usaV2 ? SchemaVersion.V2 : SchemaVersion.CURRENT;
        var avaliacao = grader.avaliar(responseJson, schemaVersion, prompt.regras(), skeleton,
                zonasAtleta, atleta, semanaInicio);
        BigDecimal custo = custoDaChamada(chatResponse);
        return new ResultadoCandidato(responseJson, avaliacao, custo);
    }

    private @Nullable BigDecimal custoDaChamada(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        String modelo = chatResponse.getMetadata().getModel();
        return EvalCostCalculator.custoUsd(modelo, chatResponse.getMetadata().getUsage()).orElse(null);
    }
}
