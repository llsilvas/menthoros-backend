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

    @org.junit.jupiter.api.Nested
    @DisplayName("buildSchemaV2 (semantic-session-schema)")
    class BuildSchemaV2 {

        @Test
        @DisplayName("blocos é array com minItems 1, sem pace/FC/distância/duração no treino")
        void schemaV2TemBlocosSemCamposAbsolutos() {
            Map<String, Object> schema = builder.buildSchemaV2();
            Map<String, Object> treinoProps = treinoItemProperties(schema);

            assertThat(treinoProps).containsKeys("diaSemana", "tipoTreino", "justificativaIa", "blocos");
            assertThat(treinoProps).doesNotContainKeys("fcAlvo", "duracaoMin", "distanciaKm", "ritmoAlvo", "etapas");

            @SuppressWarnings("unchecked")
            Map<String, Object> blocos = (Map<String, Object>) treinoProps.get("blocos");
            assertThat(blocos.get("minItems")).isEqualTo(1);
        }

        @Test
        @DisplayName("zona/papel/unidade viram enum no JSON Schema por reflexão do tipo Java")
        void zonaPapelUnidadeViramEnum() {
            Map<String, Object> schema = builder.buildSchemaV2();
            Map<String, Object> blocoProps = blocoItemProperties(schema);

            assertThat(blocoProps).containsKeys("papel", "zona", "unidade", "repeticoes",
                    "quantidadePorRepeticao", "recuperacao");

            @SuppressWarnings("unchecked")
            Map<String, Object> zona = (Map<String, Object>) blocoProps.get("zona");
            @SuppressWarnings("unchecked")
            Collection<Object> zonaEnum = (Collection<Object>) zona.get("enum");
            assertThat(zonaEnum).containsExactlyInAnyOrder("Z1", "Z2", "Z3", "Z4", "Z5", "LIMIAR");

            @SuppressWarnings("unchecked")
            Map<String, Object> papel = (Map<String, Object>) blocoProps.get("papel");
            @SuppressWarnings("unchecked")
            Collection<Object> papelEnum = (Collection<Object>) papel.get("enum");
            assertThat(papelEnum).containsExactlyInAnyOrder("AQUEC", "PRINCIPAL", "RECUP", "DESAQ");
        }

        @Test
        @DisplayName("required cobre os campos obrigatórios do treino e do bloco")
        void requiredCobreCamposObrigatorios() {
            Map<String, Object> schema = builder.buildSchemaV2();

            @SuppressWarnings("unchecked")
            Map<String, Object> treinoItems = (Map<String, Object>) treinos(schema).get("items");
            @SuppressWarnings("unchecked")
            Collection<String> treinoRequired = (Collection<String>) treinoItems.get("required");
            assertThat(treinoRequired).contains("diaSemana", "tipoTreino", "blocos");

            @SuppressWarnings("unchecked")
            Map<String, Object> blocos = (Map<String, Object>) treinoItemProperties(schema).get("blocos");
            @SuppressWarnings("unchecked")
            Map<String, Object> blocoItems = (Map<String, Object>) blocos.get("items");
            @SuppressWarnings("unchecked")
            Collection<String> blocoRequired = (Collection<String>) blocoItems.get("required");
            assertThat(blocoRequired).contains("papel", "repeticoes", "quantidadePorRepeticao", "unidade", "zona");
        }

        @Test
        @DisplayName("achado do /qa: repeticoes/quantidadePorRepeticao têm maximum — evita overflow em SessionResolver")
        void repeticoesEQuantidadeTemMaximum() {
            Map<String, Object> schema = builder.buildSchemaV2();
            Map<String, Object> blocoProps = blocoItemProperties(schema);

            assertThat(blocoProps.get("repeticoes")).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> repeticoes = (Map<String, Object>) blocoProps.get("repeticoes");
            assertThat(repeticoes.get("maximum")).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> quantidade = (Map<String, Object>) blocoProps.get("quantidadePorRepeticao");
            assertThat(quantidade.get("maximum")).isNotNull();
        }

        @Test
        @DisplayName("v2JsonSchemaOptions() envolve o schema v2 em ResponseFormat strict:true")
        void v2JsonSchemaOptionsEnvolveOSchema() {
            var options = builder.v2JsonSchemaOptions();
            assertThat(options.getResponseFormat()).isNotNull();
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> blocoItemProperties(Map<String, Object> schema) {
            Map<String, Object> treinoProps = treinoItemProperties(schema);
            Map<String, Object> blocos = (Map<String, Object>) treinoProps.get("blocos");
            Map<String, Object> blocoItems = (Map<String, Object>) blocos.get("items");
            return (Map<String, Object>) blocoItems.get("properties");
        }
    }
}
