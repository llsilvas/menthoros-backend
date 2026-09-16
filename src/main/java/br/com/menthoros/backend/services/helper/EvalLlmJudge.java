package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.eval.NotaJuizCompleta;
import br.com.menthoros.backend.dto.eval.NotaJuizReduzida;
import br.com.menthoros.backend.services.prompt.EvalJudgeSchemaBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Juiz-LLM do eval set (plan-generation-eval-set, fatia 2) — roda nos dois modos, mudando só a
 * rubrica ({@link NotaJuizCompleta} em candidato, {@link NotaJuizReduzida} em auditoria) e o
 * quanto de contexto acompanha a resposta a julgar.
 *
 * <p>Recebe o {@link ChatClient} de fora (mesmo padrão de {@link EvalCandidateRunner}) — quem
 * monta o cliente decide se quer um {@code CostTrackingAdvisor} com {@code route=EVAL_JUDGE} para
 * o ledger (auditoria/retenção; não é a fonte do custo agregado do eval, que é calculado em
 * memória a partir do {@code Usage} da própria chamada — ver design.md §2).
 *
 * <p>Idempotent: NÃO — cada chamada é uma nova avaliação real. Side Effects: chamada de rede à
 * LLM (custo real). Tenant-aware: NÃO.
 */
public class EvalLlmJudge {

    private static final String SYSTEM_REDUZIDA = """
            Você é um coach experiente de corrida de rua avaliando planos de treino semanais \
            gerados por IA. Julgue o plano abaixo apenas pelos eixos observáveis diretamente na \
            resposta, sem inventar contexto que você não tem: polarização de intensidade (mistura \
            saudável entre fácil/moderado/forte), especificidade para a prova-alvo (quando houver) \
            e clareza da comunicação/justificativa. Responda só o JSON pedido, nota 1 (péssimo) a \
            5 (excelente) por eixo.""";

    private static final String SYSTEM_COMPLETA = """
            Você é um coach experiente de corrida de rua avaliando planos de treino semanais \
            gerados por IA. Você recebe o histórico e perfil do atleta junto do plano — use-os \
            para julgar progressão de carga frente às semanas anteriores e segurança/gestão de \
            risco de lesão, além de polarização de intensidade, especificidade para a prova, \
            clareza e exequibilidade da carga frente à rotina do atleta. Responda só o JSON \
            pedido, nota 1 (péssimo) a 5 (excelente) por eixo.""";

    private final ChatClient chatClient;
    private final EvalJudgeSchemaBuilder schemaBuilder;
    private final ObjectMapper objectMapper;

    public EvalLlmJudge(ChatClient chatClient, EvalJudgeSchemaBuilder schemaBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.schemaBuilder = schemaBuilder;
        this.objectMapper = objectMapper;
    }

    /** Modo auditoria — rubrica reduzida, só a resposta congelada, sem contexto de atleta. */
    public NotaJuizReduzida avaliarReduzida(String respostaLlmJson) {
        String user = "Plano gerado pela IA (JSON):\n" + respostaLlmJson;
        String json = chamarJuiz(SYSTEM_REDUZIDA, user, schemaBuilder.optionsReduzida());
        return parsear(json, NotaJuizReduzida.class);
    }

    /** Modo candidato — rubrica completa, com contexto do atleta (histórico/perfil/prova). */
    public NotaJuizCompleta avaliarCompleta(String respostaLlmJson, @Nullable String contextoAtleta) {
        String contexto = contextoAtleta != null && !contextoAtleta.isBlank()
                ? contextoAtleta : "(sem contexto adicional)";
        String user = "Contexto do atleta:\n" + contexto + "\n\nPlano gerado pela IA (JSON):\n" + respostaLlmJson;
        String json = chamarJuiz(SYSTEM_COMPLETA, user, schemaBuilder.optionsCompleta());
        return parsear(json, NotaJuizCompleta.class);
    }

    private String chamarJuiz(String system, String user, org.springframework.ai.chat.prompt.ChatOptions options) {
        String resposta = chatClient.prompt().system(system).user(user).options(options).call().content();
        if (resposta == null || resposta.isBlank()) {
            throw new IllegalStateException("Juiz-LLM não retornou conteúdo");
        }
        return resposta;
    }

    private <T> T parsear(String json, Class<T> tipo) {
        try {
            return objectMapper.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Resposta do juiz-LLM não é um JSON válido para " + tipo.getSimpleName() + ": "
                            + e.getMessage(), e);
        }
    }
}
