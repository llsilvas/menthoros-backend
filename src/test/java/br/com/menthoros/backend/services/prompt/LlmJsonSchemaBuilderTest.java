package br.com.menthoros.backend.services.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema JSON `strict:true` enviado à OpenAI para a geração de plano (extraído de
 * {@code IaServiceImpl.buildSchemaTightInlineOrDefs}, refactor-iaservice-decomposition seção 2).
 */
@DisplayName("LlmJsonSchemaBuilder")
class LlmJsonSchemaBuilderTest {

    private final LlmJsonSchemaBuilder builder = new LlmJsonSchemaBuilder();

    @Test
    @DisplayName("provaId/descricao/zonaAlvo nunca vão no schema da LLM")
    void schemaOmiteCamposQueNuncaVemDoLlm() {
        // prova-no-plano-semanal: os três só são preenchidos pelo ProvaNoPlanoService, no servidor,
        // depois da resposta da LLM. Se aparecerem no schema strict:true, a OpenAI é forçada a
        // devolver um valor para eles em TODO treino — e para provaId (tipo UUID, sem anyOf de
        // null) isso produz o sentinel 00000000-0000-0000-0000-000000000000, que quebra a FK de
        // tb_treino_planejado.prova_id ao persistir um treino comum (bug real, corrigido aqui).
        Map<String, Object> schema = builder.buildSchemaTightInlineOrDefs();

        Map<String, Object> treinoProps = treinoItemProperties(schema);
        assertThat(treinoProps).doesNotContainKeys("provaId", "descricao", "zonaAlvo");

        @SuppressWarnings("unchecked")
        Map<String, Object> treinoItems = (Map<String, Object>) treinos(schema).get("items");
        @SuppressWarnings("unchecked")
        Collection<String> required = (Collection<String>) treinoItems.get("required");
        assertThat(required).doesNotContain("provaId", "descricao", "zonaAlvo");
    }

    @Test
    @DisplayName("campos que a LLM de fato preenche continuam no schema")
    void schemaMantemCamposReaisDaLlm() {
        Map<String, Object> schema = builder.buildSchemaTightInlineOrDefs();
        Map<String, Object> treinoProps = treinoItemProperties(schema);

        assertThat(treinoProps).containsKeys(
                "diaSemana", "tipoTreino", "duracaoMin", "distanciaKm", "ritmoAlvo", "etapas");
    }

    @Test
    @DisplayName("CA10 — prompt e schema declaram o mesmo teto de treinos (maxItems == 'máximo N treinos')")
    void promptESchemaAlinhamTetoDeTreinos() throws Exception {
        Map<String, Object> schema = builder.buildSchemaTightInlineOrDefs();
        int schemaMax = (int) treinos(schema).get("maxItems");

        String template = new String(new org.springframework.core.io.ClassPathResource(
                "prompts/plano-treino-system.txt").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        // O template deve declarar exatamente o teto do schema — divergir aqui quebra o teste (CA10).
        assertThat(template)
                .as("prompt deve declarar 'máximo %d treinos' alinhado ao maxItems do schema", schemaMax)
                .contains("máximo " + schemaMax + " treinos");
        assertThat(schemaMax).isEqualTo(5);
    }

    @Test
    @DisplayName("defaultJsonSchemaOptions() envolve o schema em ResponseFormat strict:true")
    void defaultJsonSchemaOptionsEnvolveOSchema() {
        var options = builder.defaultJsonSchemaOptions();
        assertThat(options.getResponseFormat()).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> treinos(Map<String, Object> schema) {
        Map<String, Object> planoProps = (Map<String, Object>) schema.get("properties");
        return (Map<String, Object>) planoProps.get("treinosPlanejados");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> treinoItemProperties(Map<String, Object> schema) {
        Map<String, Object> treinoItems = (Map<String, Object>) treinos(schema).get("items");
        return (Map<String, Object>) treinoItems.get("properties");
    }
}
