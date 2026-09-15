package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.compliance.SchemaVersion;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.services.prompt.LlmJsonSchemaBuilder;
import br.com.menthoros.backend.services.prompt.PlanoTreinoPromptBuilder.PromptGerado;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;

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

    public record ResultadoCandidato(String responseJson, EvalDeterministicGrader.Resultado avaliacao) {
    }

    /**
     * Chama a LLM com o prompt já montado (v1, por ora — ver Open Questions do proposal sobre v2
     * em modo candidato) e grada a resposta. Idempotent: NÃO. Side Effects: chamada real à LLM.
     */
    public ResultadoCandidato rodar(PromptGerado prompt, @Nullable WeekPlanSkeleton skeleton,
                                     AthleteZones zonasAtleta, Atleta atleta, LocalDate semanaInicio) {
        String responseJson = chatClient.prompt()
                .system(prompt.system())
                .user(prompt.user())
                .options(llmJsonSchemaBuilder.defaultJsonSchemaOptions())
                .call()
                .content();
        if (responseJson == null || responseJson.isBlank()) {
            throw new IllegalStateException("LLM não retornou conteúdo para o modo candidato do eval set");
        }
        var avaliacao = grader.avaliar(responseJson, SchemaVersion.CURRENT, prompt.regras(), skeleton,
                zonasAtleta, atleta, semanaInicio);
        return new ResultadoCandidato(responseJson, avaliacao);
    }
}
