package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.dto.eval.NotaJuizCompleta;
import br.com.menthoros.backend.dto.eval.NotaJuizReduzida;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import java.util.Map;

/**
 * Constrói o JSON Schema `strict:true` da resposta do juiz-LLM (plan-generation-eval-set, fatia 2)
 * — mesmo padrão de {@link LlmJsonSchemaBuilder} (reflexão via {@link BeanOutputConverter}, nunca
 * usado para parse — só para gerar o schema enviado à OpenAI).
 *
 * <p>Idempotent: YES — pura reflexão, sem I/O. Side Effects: NONE. Tenant-aware: NÃO.
 *
 * <p>Deliberadamente sem {@code @Component} — nunca instanciada pelo Spring (achado do /qa).
 */
public class EvalJudgeSchemaBuilder {

    /** Rubrica completa (modo candidato) — {@link NotaJuizCompleta}, 6 eixos. */
    public OpenAiChatOptions optionsCompleta() {
        return options(NotaJuizCompleta.class, "NotaJuizCompleta");
    }

    /** Rubrica reduzida (modo auditoria) — {@link NotaJuizReduzida}, 3 eixos observáveis. */
    public OpenAiChatOptions optionsReduzida() {
        return options(NotaJuizReduzida.class, "NotaJuizReduzida");
    }

    private OpenAiChatOptions options(Class<?> tipo, String nomeSchema) {
        var converter = new BeanOutputConverter<>(tipo);
        @SuppressWarnings("unchecked")
        Map<String, Object> schemaMap = (Map<String, Object>) ModelOptionsUtils.jsonToMap(converter.getJsonSchema());

        var responseFormat = ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(ResponseFormat.JsonSchema.builder()
                        .name(nomeSchema)
                        .schema(schemaMap)
                        .strict(true)
                        .build())
                .build();

        return OpenAiChatOptions.builder().responseFormat(responseFormat).build();
    }
}
