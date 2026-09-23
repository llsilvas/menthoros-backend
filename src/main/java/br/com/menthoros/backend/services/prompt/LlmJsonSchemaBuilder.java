package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.v2.PlanoSemanalLlmDtoV2;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Constrói o JSON Schema `strict:true` de {@link PlanoSemanalLlmDto} enviado à OpenAI na geração
 * de plano — extraído de {@code IaServiceImpl} (refactor-iaservice-decomposition, seção 2).
 *
 * <p>Idempotent: YES — pura reflexão sobre {@code PlanoSemanalLlmDto.class}, sem I/O.
 * Side Effects: NONE. Tenant-aware: NO.</p>
 */
@Component
public class LlmJsonSchemaBuilder {

    /** Monta as {@code OpenAiChatOptions} com o schema apertado, pronto para `.options(...)`. */
    public OpenAiChatOptions defaultJsonSchemaOptions() {
        Map<String, Object> schemaMap = buildSchemaTightInlineOrDefs();

        var rf = ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(ResponseFormat.JsonSchema.builder()
                        .name("PlanoSemanalLlmDto")
                        .schema(schemaMap)
                        .strict(true)
                        .build())
                .build();

        return OpenAiChatOptions.builder()
                .responseFormat(rf)
                .build();
    }

    /**
     * Restringe o array {@code restDays}: dia como enum de strings e motivo de até 200 caracteres
     * (add-descanso-explicito-por-fadiga). O array em si vem do DTO; aqui só apertamos os itens.
     */
    @SuppressWarnings("unchecked")
    private static void ajustarRestDays(Map<String, Object> planoProps) {
        Map<String, Object> restDays = (Map<String, Object>) planoProps.get("restDays");
        if (restDays == null) return;
        Map<String, Object> items = (Map<String, Object>) restDays.get("items");
        if (items == null) return;
        Map<String, Object> itemProps = (Map<String, Object>) items.get("properties");
        if (itemProps == null) return;

        putEnum(itemProps, "dayOfWeek",
                List.of("DOMINGO", "SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO"));
        Map<String, Object> reason = (Map<String, Object>) itemProps.get("reason");
        if (reason != null) {
            reason.put("maxLength", 200);
        }
        enforceAllRequired(items);
    }

    @SuppressWarnings("unchecked")
    private static void enforceAllRequired(Map<String, Object> objNode) {
        if (objNode == null) return;
        Map<String, Object> props = (Map<String, Object>) objNode.get("properties");
        if (props == null) return;
        objNode.put("required", new java.util.ArrayList<>(props.keySet())); // strict:true exige TODAS as chaves
    }

    @SuppressWarnings("unchecked")
    private static void putMin(Map<String, Object> props, String name, Number min) {
        Map<String, Object> p = (Map<String, Object>) props.get(name);
        if (p != null) p.put("minimum", min);
    }

    @SuppressWarnings("unchecked")
    private static void putMax(Map<String, Object> props, String name, Number max) {
        Map<String, Object> p = (Map<String, Object>) props.get(name);
        if (p != null) p.put("maximum", max);
    }

    @SuppressWarnings("unchecked")
    private static void putEnum(Map<String, Object> props, String name, List<String> values) {
        Map<String, Object> p = (Map<String, Object>) props.get(name);
        if (p != null) p.put("enum", values);
    }

    /** Monta as {@code OpenAiChatOptions} com o schema v2 (blocos), pronto para `.options(...)`. */
    public OpenAiChatOptions v2JsonSchemaOptions() {
        Map<String, Object> schemaMap = buildSchemaV2();

        var rf = ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(ResponseFormat.JsonSchema.builder()
                        .name("PlanoSemanalLlmDtoV2")
                        .schema(schemaMap)
                        .strict(true)
                        .build())
                .build();

        return OpenAiChatOptions.builder()
                .responseFormat(rf)
                .build();
    }

    /**
     * Schema v2 (semantic-session-schema) — mesmo padrão de {@link #buildSchemaTightInlineOrDefs()},
     * refletindo sobre {@link PlanoSemanalLlmDtoV2} em vez de {@link PlanoSemanalLlmDto}. `zona`/
     * `papel`/`unidade` são enums Java reais nos DTOs v2 (não `String`, como em v1) — o
     * {@link BeanOutputConverter} já gera `enum` no JSON Schema para eles por reflexão, sem
     * pós-processamento manual.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> buildSchemaV2() {
        var converter = new BeanOutputConverter<>(PlanoSemanalLlmDtoV2.class);
        var schema = (Map<String, Object>) ModelOptionsUtils.jsonToMap(converter.getJsonSchema());

        Map<String, Object> planoProps = (Map<String, Object>) schema.get("properties");
        if (planoProps == null) return schema;

        for (String k : List.of("volumePlanejadoKm", "volumeAlvoKm")) {
            putMin(planoProps, k, 0);
        }
        putEnum(planoProps, "status", List.of("PLANEJADO", "INICIADO", "EM_ANDAMENTO", "ATIVO", "CONCLUIDO"));

        ajustarRestDays(planoProps);

        Map<String, Object> treinos = (Map<String, Object>) planoProps.get("treinosPlanejados");
        if (treinos == null) {
            enforceAllRequired(schema);
            return schema;
        }
        // 1..7: a cobertura da semana governa quantos treinos existem (um por dia disponível que não
        // for descanso), inclusive para quem treina 6-7 dias (add-descanso-explicito-por-fadiga).
        treinos.put("minItems", 1);
        treinos.put("maxItems", 7);

        Map<String, Object> treinoItems = (Map<String, Object>) treinos.get("items");
        Map<String, Object> treinoProps = treinoItems != null
                ? (Map<String, Object>) treinoItems.get("properties")
                : null;

        if (treinoProps != null) {
            putEnum(treinoProps, "diaSemana",
                    List.of("DOMINGO", "SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO"));
            // Mesmo domínio de tipoTreino que v1 (LlmJsonSchemaBuilder.buildSchemaTightInlineOrDefs)
            // — o tipo do dia já vem fixado pelo skeleton (planner-engine-enforcement), esta change
            // não muda esse domínio.
            putEnum(treinoProps, "tipoTreino",
                    List.of("REGENERATIVO", "INTERVALADO", "CONTINUO", "LONGO", "TIRO", "FARTLEK", "TEMPO_RUN"));

            Map<String, Object> just = (Map<String, Object>) treinoProps.get("justificativaIa");
            if (just != null) {
                just.put("maxLength", 200);
            }

            Map<String, Object> blocos = (Map<String, Object>) treinoProps.get("blocos");
            if (blocos != null) {
                blocos.put("minItems", 1);

                Map<String, Object> blocoItems = (Map<String, Object>) blocos.get("items");
                Map<String, Object> blocoProps = blocoItems != null
                        ? (Map<String, Object>) blocoItems.get("properties")
                        : null;

                if (blocoProps != null) {
                    // Limites superiores (achado do /qa, pré-mortem codex): sem maximum, um
                    // quantidadePorRepeticao astronômico faz SessionResolver.intValueExact()
                    // estourar ArithmeticException dentro de `gerar` (fora do escopo de retry) —
                    // os valores abaixo são generosos o bastante para qualquer treino real (100km/
                    // ~28h por repetição, 50 repetições) e pequenos o bastante para nunca chegar
                    // perto de Integer.MAX_VALUE mesmo somados.
                    putMin(blocoProps, "repeticoes", 1);
                    putMax(blocoProps, "repeticoes", 50);
                    putMin(blocoProps, "quantidadePorRepeticao", 0);
                    putMax(blocoProps, "quantidadePorRepeticao", 100000);

                    // recuperacao é opcional (bloco sem repetição, ou repetição sem descanso) —
                    // anyOf com o objeto ou null, compatível com strict:true da OpenAI (mesmo
                    // padrão de ritmoAlvo nullable em v1).
                    Map<String, Object> recuperacao = (Map<String, Object>) blocoProps.get("recuperacao");
                    if (recuperacao != null) {
                        Map<String, Object> recuperacaoObjeto = new java.util.LinkedHashMap<>(recuperacao);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> recuperacaoProps =
                                (Map<String, Object>) recuperacaoObjeto.get("properties");
                        if (recuperacaoProps != null) {
                            putMin(recuperacaoProps, "quantidade", 0);
                            putMax(recuperacaoProps, "quantidade", 100000);
                        }
                        enforceAllRequired(recuperacaoObjeto);
                        blocoProps.put("recuperacao", new java.util.LinkedHashMap<>(Map.of(
                                "anyOf", List.of(recuperacaoObjeto, Map.of("type", "null"))
                        )));
                    }
                }

                if (blocoItems != null) {
                    enforceAllRequired(blocoItems);
                }
            }

            enforceAllRequired(treinoItems);
        }

        enforceAllRequired(schema);
        return schema;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buildSchemaTightInlineOrDefs() {
        var converter = new BeanOutputConverter<>(PlanoSemanalLlmDto.class);
        var schema = (Map<String, Object>) ModelOptionsUtils.jsonToMap(converter.getJsonSchema());

        // ROOT properties
        Map<String, Object> planoProps = (Map<String, Object>) schema.get("properties");
        if (planoProps == null) return schema;

        // Volumes >= 0
        for (String k : List.of("volumePlanejadoKm", "volumeRealizadoKm", "volumeAlvoKm")) {
            putMin(planoProps, k, 0);
        }

        // Status enum
        putEnum(planoProps, "status", List.of("PLANEJADO", "INICIADO", "EM_ANDAMENTO", "ATIVO", "CONCLUIDO"));

        // treinosPlanejados array 3..5
        ajustarRestDays(planoProps);

        Map<String, Object> treinos = (Map<String, Object>) planoProps.get("treinosPlanejados");
        if (treinos != null) {
            // 1..7 — ver buildSchemaV2 (add-descanso-explicito-por-fadiga)
            treinos.put("minItems", 1);
            treinos.put("maxItems", 7);

            // TREINO items
            Map<String, Object> treinoItems = (Map<String, Object>) treinos.get("items");
            Map<String, Object> treinoProps = treinoItems != null
                    ? (Map<String, Object>) treinoItems.get("properties")
                    : null;

            if (treinoProps != null) {
                // provaId/descricao/zonaAlvo nunca vêm do LLM (prova-no-plano-semanal,
                // TreinoPlanejadoLlmDto.java) — ProvaNoPlanoService os preenche depois, no
                // servidor. Removidos ANTES de enforceAllRequired: sem isso, strict:true da
                // OpenAI força os três a "required" com um schema de UUID sem opção de nulo, e o
                // modelo não tem como expressar "sem prova" — cai no sentinel
                // 00000000-0000-0000-0000-000000000000, que é um UUID sintaticamente válido e
                // quebra a FK de tb_treino_planejado.prova_id ao persistir.
                treinoProps.remove("provaId");
                treinoProps.remove("descricao");
                treinoProps.remove("zonaAlvo");

                // Enums
                putEnum(treinoProps, "diaSemana",
                        List.of("DOMINGO", "SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO"));
                putEnum(treinoProps, "tipoTreino",
                        List.of("REGENERATIVO", "INTERVALADO", "CONTINUO", "LONGO", "TIRO", "FARTLEK", "TEMPO_RUN"));
                putEnum(treinoProps, "statusTreino",
                        List.of("PENDENTE", "REALIZADO", "CANCELADO"));

                // Limites numéricos
                putMin(treinoProps, "intensidadePlanejada", 0.5);
                putMax(treinoProps, "intensidadePlanejada", 1.5);
                putMin(treinoProps, "percepcaoEsforcoEsperada", 1);
                putMax(treinoProps, "percepcaoEsforcoEsperada", 10);
                putMin(treinoProps, "duracaoMin", 1);
                putMin(treinoProps, "distanciaKm", 0);
                putMin(treinoProps, "tssPlanejado", 0);

                // Pattern ritmo: "5:30-6:00/km"
                Map<String, Object> ritmo = (Map<String, Object>) treinoProps.get("ritmoAlvo");
                if (ritmo != null) {
                    ritmo.put("pattern", "^[0-9]{1,2}:[0-5][0-9]-[0-9]{1,2}:[0-5][0-9]/km$");
                }

                // MaxLength justificativa
                Map<String, Object> just = (Map<String, Object>) treinoProps.get("justificativaIa");
                if (just != null) {
                    just.put("maxLength", 200);
                }

                // ETAPAS
                Map<String, Object> etapas = (Map<String, Object>) treinoProps.get("etapas");
                if (etapas != null) {
                    etapas.put("minItems", 2); // Mínimo 2 etapas (qualquer treino)
                    // Sem maxItems - permitir expansão completa de intervalados

                    Map<String, Object> etapaItems = (Map<String, Object>) etapas.get("items");
                    Map<String, Object> etapaProps = etapaItems != null
                            ? (Map<String, Object>) etapaItems.get("properties")
                            : null;

                    if (etapaProps != null) {
                        // Enum tipoEtapa
                        putEnum(etapaProps, "tipoEtapa",
                                List.of("AQUECIMENTO", "PRINCIPAL", "INTERVALADO", "RECUPERACAO", "DESAQUECIMENTO"));

                        // Limites
                        putMin(etapaProps, "ordem", 1);
                        putMin(etapaProps, "duracaoMin", 1);
                        putMin(etapaProps, "distanciaKm", 0);

                        // 🎯 CRÍTICO: repeticoes SEMPRE = 1
                        Map<String, Object> reps = (Map<String, Object>) etapaProps.get("repeticoes");
                        if (reps != null) {
                            reps.put("const", 1); // Força valor constante = 1
                            reps.put("default", 1);
                        }

                        // MaxLength descrição
                        Map<String, Object> desc = (Map<String, Object>) etapaProps.get("descricaoEtapa");
                        if (desc != null) {
                            desc.put("maxLength", 120);
                        }

                        // Pattern FC: "140-160 bpm" (range absoluto em bpm, alinhado com LTHR)
                        Map<String, Object> fc = (Map<String, Object>) etapaProps.get("fcAlvoEtapa");
                        if (fc != null) {
                            fc.put("pattern", "^[0-9]{2,3}-[0-9]{2,3} bpm$");
                        }

                        // ritmoAlvo por etapa: nullable (null para AQUECIMENTO/DESAQUECIMENTO/RECUPERACAO)
                        // anyOf com pattern válido ou null — compatível com strict:true do OpenAI
                        etapaProps.put("ritmoAlvo", new java.util.LinkedHashMap<>(Map.of(
                                "anyOf", List.of(
                                        Map.of("type", "string",
                                                "pattern", "^[0-9]{1,2}:[0-5][0-9]-[0-9]{1,2}:[0-5][0-9]/km$"),
                                        Map.of("type", "null")
                                )
                        )));
                    }

                    // Tornar todos os campos da etapa obrigatórios (ritmoAlvo nullable via anyOf)
                    if (etapaItems != null) {
                        enforceAllRequired(etapaItems);
                    }
                }

                // Tornar todos os campos do treino obrigatórios
                enforceAllRequired(treinoItems);
            }
        }

        // Tornar todos os campos do ROOT obrigatórios
        enforceAllRequired(schema);

        return schema;
    }
}
