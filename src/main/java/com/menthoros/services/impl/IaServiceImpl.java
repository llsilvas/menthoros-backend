package com.menthoros.services.impl;

import com.menthoros.dto.llm.PlanoSemanalLlmDto;
import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.PlanoTreinoOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.Prova;
import com.menthoros.exception.LLMException;
import com.menthoros.services.IaService;
import com.menthoros.services.prompt.PlanoTreinoPromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class IaServiceImpl implements IaService {

    private final ChatClient chatClient;
    private final PlanoTreinoPromptBuilder promptBuilder;

    public IaServiceImpl(ChatClient chatClient, PlanoTreinoPromptBuilder promptBuilder) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
    }

    private OpenAiChatOptions defaultJsonSchemaOptions() {
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
    @SuppressWarnings("unchecked")
    private static void enforceAllRequired(Map<String,Object> objNode) {
        if (objNode == null) return;
        Map<String,Object> props = (Map<String,Object>) objNode.get("properties");
        if (props == null) return;
        objNode.put("required", new java.util.ArrayList<>(props.keySet())); // strict:true exige TODAS as chaves
    }

    @SuppressWarnings("unchecked")
    private static void putMin(Map<String,Object> props, String name, Number min) {
        Map<String,Object> p = (Map<String,Object>) props.get(name);
        if (p != null) p.put("minimum", min);
    }

    @SuppressWarnings("unchecked")
    private static void putMax(Map<String,Object> props, String name, Number max) {
        Map<String,Object> p = (Map<String,Object>) props.get(name);
        if (p != null) p.put("maximum", max);
    }

    @SuppressWarnings("unchecked")
    private static void putEnum(Map<String,Object> props, String name, java.util.List<String> values) {
        Map<String,Object> p = (Map<String,Object>) props.get(name);
        if (p != null) p.put("enum", values);
    }

    private Map<String, Object> buildSchemaTightInlineOrDefs() {
        var converter = new BeanOutputConverter<>(PlanoSemanalLlmDto.class);
        var schema = (Map<String, Object>) ModelOptionsUtils.jsonToMap(converter.getJsonSchema());

        // ROOT
        Map<String, Object> planoProps = (Map<String, Object>) schema.get("properties");
        if (planoProps == null) return schema;

        // volumes >= 0
        for (String k : List.of("volumePlanejadoKm","volumeRealizadoKm","volumeAlvoKm")) {
            Map<String, Object> p = (Map<String, Object>) planoProps.get(k);
            if (p != null) p.put("minimum", 0);
        }

        // status como enum (string)
        putEnum(planoProps, "status", List.of("PLANEJADO","INICIADO","EM_ANDAMENTO","ATIVO","CONCLUIDO"));

        // treinosPlanejados array 3..5
        Map<String, Object> treinos = (Map<String, Object>) planoProps.get("treinosPlanejados");
        if (treinos != null) {
            treinos.put("minItems", 3);
            treinos.put("maxItems", 5);
        }

        // TREINO (items)
        Map<String, Object> treinoItems = treinos != null ? (Map<String, Object>) treinos.get("items") : null;
        Map<String, Object> treinoProps = treinoItems != null ? (Map<String, Object>) treinoItems.get("properties") : null;

//        treinoItems.put("oneOf", List.of(
//                Map.of( // INTERVALADO/TIRO
//                        "properties", Map.of("tipoTreino", Map.of("enum", List.of("INTERVALADO","TIRO"))),
//                        "required", List.of("tipoTreino"),
//                        "allOf", List.of(Map.of("properties", Map.of("etapas", Map.of("minItems", 8))))
//                ),
//                Map.of( // LONGO
//                        "properties", Map.of("tipoTreino", Map.of("const", "LONGO")),
//                        "required", List.of("tipoTreino"),
//                        "allOf", List.of(Map.of("properties", Map.of("etapas", Map.of("minItems", 3, "maxItems", 3))))
//                ),
//                Map.of( // Demais tipos
//                        "properties", Map.of("tipoTreino", Map.of("enum", List.of("REGENERATIVO","CONTINUO","FARTLEK","TEMPO_RUN","SUBIDA","PROVA","FACIL"))),
//                        "required", List.of("tipoTreino"),
//                        "allOf", List.of(Map.of("properties", Map.of("etapas", Map.of("minItems", 2, "maxItems", 4))))
//                )
//        ));


        if (treinoProps != null) {
            // enums em treino
            putEnum(treinoProps, "diaSemana",  List.of("DOMINGO","SEGUNDA","TERCA","QUARTA","QUINTA","SEXTA","SABADO"));
            putEnum(treinoProps, "tipoTreino", List.of("REGENERATIVO","INTERVALADO","CONTINUO","LONGO","TIRO","FARTLEK","TEMPO_RUN"));
            putEnum(treinoProps, "statusTreino", List.of("PENDENTE","REALIZADO","CANCELADO"));

            // limites
            putMin(treinoProps, "intensidadePlanejada", 0.5);
            putMax(treinoProps, "intensidadePlanejada", 1.5);
            putMin(treinoProps, "percepcaoEsforcoEsperada", 1);
            putMax(treinoProps, "percepcaoEsforcoEsperada", 10);
            putMin(treinoProps, "duracaoMin", 1);
            putMin(treinoProps, "distanciaKm", 0);

            // padrão do ritmo (1–2 dígitos nos minutos)
            Map<String,Object> ritmo = (Map<String,Object>) treinoProps.get("ritmoAlvo");
            if (ritmo != null) ritmo.put("pattern", "^[0-9]{1,2}:[0-5][0-9]-[0-9]{1,2}:[0-5][0-9]/km$");

            // limitar justificativa
            Map<String,Object> just = (Map<String,Object>) treinoProps.get("justificativaIa");
            if (just != null) just.put("maxLength", 220);

            // ETAPAS
            Map<String,Object> etapas = (Map<String,Object>) treinoProps.get("etapas");
            if (etapas != null) {
                etapas.put("minItems", 3);
                etapas.remove("maxItems");
                Map<String,Object> etapaItems = (Map<String,Object>) etapas.get("items");
                Map<String,Object> etapaProps = etapaItems != null ? (Map<String,Object>) etapaItems.get("properties") : null;

                if (etapaProps != null) {
                    // tipoEtapa enum
                    putEnum(etapaProps, "tipoEtapa", List.of("AQUECIMENTO","PRINCIPAL","INTERVALADO","RECUPERACAO","DESAQUECIMENTO"));

                    // limites/pattern
                    putMin(etapaProps, "duracaoMin", 1);
                    putMin(etapaProps, "distanciaKm", 0);
                    putMin(etapaProps, "repeticoes", 1);

                    Map<String,Object> desc = (Map<String,Object>) etapaProps.get("descricaoEtapa");
                    if (desc != null) desc.put("maxLength", 120);

                    Map<String,Object> fc = (Map<String,Object>) etapaProps.get("fcAlvoEtapa");
                    if (fc != null) fc.put("pattern", "^[0-9]{1,3}-[0-9]{1,3}% FCmáx$");
                }

                // required da etapa = TODAS as chaves
                if (etapaItems != null) enforceAllRequired(etapaItems);
            }
            // required do treino = TODAS as chaves (inclui statusTreino se existir)
            enforceAllRequired(treinoItems);
        }

        // required do ROOT = TODAS as chaves (inclui tsbInicio e tsbFim)
        enforceAllRequired(schema);

        return schema;
    }



    @Override
    public PlanoSemanalLlmDto gerarPlanoSemanal(AtletaOutputDto atletaOutputDto, List<TreinoRealizadoOutputDto> treinoRealizadoOutputDtoList, PlanoSemanalOutputDto planoSemanalOutputDto) {
        String prompt = promptBuilder.buildRequest(atletaOutputDto, treinoRealizadoOutputDtoList, planoSemanalOutputDto);

        try {
            PlanoSemanalLlmDto plano = chatClient.prompt()
                    .user(prompt)
                    .options(defaultJsonSchemaOptions())
                    .call()
                    .entity(PlanoSemanalLlmDto.class);

            log.info("Plano gerado com sucesso via structured output para atleta: {}", atletaOutputDto.id());
            return plano;

        } catch (Exception e) {
            log.error("Erro ao gerar plano via structured output para atleta {}: {}", atletaOutputDto.id(), e.getMessage(), e);
            throw new LLMException("Falha na geração de plano via IA: " + e.getMessage(), e);
        }
    }

    public PlanoSemanalLlmDto geraPlanoSemanalAvancado(Atleta atleta, PlanoMetaDados metaDados, Prova prova){
        LocalDate inicioSemana = LocalDate.now().plusWeeks(1).with(DayOfWeek.MONDAY);

        String prompt = promptBuilder.buildEnhancedPrompt(atleta, metaDados, prova, inicioSemana);

        try {
            PlanoSemanalLlmDto plano = chatClient.prompt()
                    .user(prompt)
                    .options(defaultJsonSchemaOptions())
                    .call()
                    .entity(PlanoSemanalLlmDto.class);

            log.info("Plano gerado com sucesso via structured output para atleta: {}", atleta.getId());
            return plano;

        } catch (Exception e) {
            log.error("Erro ao gerar plano via structured output para atleta {}: {}", atleta.getId(), e.getMessage(), e);
            throw new LLMException("Falha na geração de plano via IA: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<Long, PlanoTreinoOutputDto> gerarPlanosEmLote(Map<AtletaOutputDto, List<TreinoRealizadoOutputDto>> atletaDtoListMap) {
        log.warn("Método gerarPlanosEmLote ainda não implementado");
        return Map.of(); // Retorna mapa vazio em vez de null
    }

}
