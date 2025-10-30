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
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Primary
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

        // NOTA: OpenAI strict:true não aceita anyOf/oneOf condicional complexo
        // Solução: usar minItems conservador (3) + validação pós-geração rigorosa
        // As instruções detalhadas no prompt guiam a IA para gerar corretamente


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

                    // CRÍTICO: repeticoes SEMPRE = 1 (não permitir valores > 1)
                    Map<String,Object> reps = (Map<String,Object>) etapaProps.get("repeticoes");
                    if (reps != null) {
                        reps.put("minimum", 1);
                        reps.put("maximum", 1); // Força a IA expandir TODAS as etapas
                        reps.put("const", 1); // Reforço adicional
                    }

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

            // Validação pós-geração
            validarPlanoGerado(plano, atletaOutputDto.id());

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
            long startTime = System.currentTimeMillis(); // Captura o tempo de início

            PlanoSemanalLlmDto plano = chatClient.prompt()
                    .user(prompt)
                    .options(defaultJsonSchemaOptions())
                    .call()
                    .entity(PlanoSemanalLlmDto.class);

            // Validação pós-geração
            validarPlanoGerado(plano, atleta.getId());

            long endTime = System.currentTimeMillis(); // Captura o tempo de fim
            long totalTime = endTime - startTime; // Calcula o tempo total em milissegundos
            log.info("Plano gerado com sucesso via structured output para atleta: {} - {} s", atleta.getId(), totalTime/1000.0);
            return plano;

        } catch (Exception e) {
            log.error("Erro ao gerar plano via structured output para atleta {}: {}", atleta.getId(), e.getMessage(), e);
            throw new LLMException("Falha na geração de plano via IA: " + e.getMessage(), e);
        }
    }

    /**
     * Valida o plano gerado pela IA, detectando inconsistências comuns
     */
    private void validarPlanoGerado(PlanoSemanalLlmDto plano, Object atletaId) {
        if (plano == null || plano.treinosPlanejados() == null) {
            throw new LLMException("Plano gerado está nulo ou sem treinos");
        }

        plano.treinosPlanejados().forEach(treino -> {
            String tipoTreino = treino.tipoTreino();

            // Validar treinos INTERVALADO ou TIRO
            if ("INTERVALADO".equals(tipoTreino) || "TIRO".equals(tipoTreino)) {
                validarTreinoIntervalado(treino, atletaId);
            }

            // Validar treino LONGO
            if ("LONGO".equals(tipoTreino)) {
                validarTreinoLongo(treino, atletaId);
            }

            // Validar repeticoes = 1 em todas as etapas
            validarRepeticoes(treino, atletaId);
        });
    }

    /**
     * Valida treino intervalado: mínimo 8 etapas, tiros e recuperações balanceados
     */
    private void validarTreinoIntervalado(com.menthoros.dto.llm.TreinoPlanejadoLlmDto treino, Object atletaId) {
        var etapas = treino.etapas();

        if (etapas == null || etapas.size() < 8) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} tem apenas {} etapas (mínimo 8)",
                    atletaId, treino.tipoTreino(), etapas != null ? etapas.size() : 0);
            throw new LLMException(String.format(
                    "Treino %s inválido: gerou apenas %d etapas (mínimo 8 para intervalados)",
                    treino.tipoTreino(), etapas != null ? etapas.size() : 0
            ));
        }

        // Contar tiros e recuperações
        long numTiros = etapas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa()))
                .count();
        long numRecuperacoes = etapas.stream()
                .filter(e -> "RECUPERACAO".equals(e.tipoEtapa()))
                .count();

        // Validar balanceamento (pode ter 1 recuperação a menos se o último tiro não tem recuperação)
        if (numTiros < 3) {
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Treino {} tem apenas {} tiros (recomendado: 3+)",
                    atletaId, treino.tipoTreino(), numTiros);
        }

        if (Math.abs(numTiros - numRecuperacoes) > 1) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} desbalanceado: {} tiros vs {} recuperações",
                    atletaId, treino.tipoTreino(), numTiros, numRecuperacoes);
            throw new LLMException(String.format(
                    "Treino %s inválido: %d tiros mas %d recuperações (devem ser iguais ou diferença de 1)",
                    treino.tipoTreino(), numTiros, numRecuperacoes
            ));
        }

        // Validar soma aproximada das distâncias
        double somaDistancias = etapas.stream()
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();
        double distanciaPlanejada = treino.distanciaKm() != null ? treino.distanciaKm() : 0.0;

        double diferenca = Math.abs(somaDistancias - distanciaPlanejada);
        if (diferenca > 0.5) { // Tolerância de 500m
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Soma das etapas ({} km) difere da distância planejada ({} km) em {} km",
                    atletaId, somaDistancias, distanciaPlanejada, diferenca);
        }

        log.info("VALIDAÇÃO OK [Atleta {}]: Treino {} - {} etapas ({} tiros, {} recuperações, {} km)",
                atletaId, treino.tipoTreino(), etapas.size(), numTiros, numRecuperacoes, somaDistancias);
    }

    /**
     * Valida treino longo: deve ter exatamente 3 etapas
     */
    private void validarTreinoLongo(com.menthoros.dto.llm.TreinoPlanejadoLlmDto treino, Object atletaId) {
        var etapas = treino.etapas();

        if (etapas == null || etapas.size() != 3) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino LONGO tem {} etapas (esperado: 3)",
                    atletaId, etapas != null ? etapas.size() : 0);
            throw new LLMException(String.format(
                    "Treino LONGO inválido: gerou %d etapas (esperado exatamente 3: aquec, principal, desaq)",
                    etapas != null ? etapas.size() : 0
            ));
        }

        log.info("VALIDAÇÃO OK [Atleta {}]: Treino LONGO - 3 etapas conforme esperado", atletaId);
    }

    /**
     * Valida que todas as etapas têm repeticoes = 1
     */
    private void validarRepeticoes(com.menthoros.dto.llm.TreinoPlanejadoLlmDto treino, Object atletaId) {
        var etapas = treino.etapas();
        if (etapas == null) return;

        etapas.forEach(etapa -> {
            if (etapa.repeticoes() != null && etapa.repeticoes() != 1) {
                log.error("VALIDAÇÃO FALHOU [Atleta {}]: Etapa '{}' tem repeticoes={} (deve ser sempre 1)",
                        atletaId, etapa.descricaoEtapa(), etapa.repeticoes());
                throw new LLMException(String.format(
                        "Etapa '%s' inválida: repeticoes=%d (deve ser sempre 1 - expandir etapas individualmente)",
                        etapa.descricaoEtapa(), etapa.repeticoes()
                ));
            }
        });
    }

    @Override
    public Map<Long, PlanoTreinoOutputDto> gerarPlanosEmLote(Map<AtletaOutputDto, List<TreinoRealizadoOutputDto>> atletaDtoListMap) {
        log.warn("Método gerarPlanosEmLote ainda não implementado");
        return Map.of(); // Retorna mapa vazio em vez de null
    }

}
