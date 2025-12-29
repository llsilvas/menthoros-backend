package com.menthoros.services.impl;

import com.menthoros.dto.llm.EtapaTreinoLlmDto;
import com.menthoros.dto.llm.PlanoSemanalLlmDto;
import com.menthoros.dto.llm.TreinoPlanejadoLlmDto;
import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.PlanoTreinoOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.Prova;
import com.menthoros.enums.NivelExperiencia;
import com.menthoros.exception.LLMException;
import com.menthoros.repository.AtletaRepository;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Primary
public class IaServiceImpl implements IaService {

    private final ChatClient chatClient;
    private final PlanoTreinoPromptBuilder promptBuilder;
    private final AtletaRepository atletaRepository;

    public IaServiceImpl(ChatClient chatClient, PlanoTreinoPromptBuilder promptBuilder, AtletaRepository atletaRepository) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
        this.atletaRepository = atletaRepository;
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
        Map<String, Object> treinos = (Map<String, Object>) planoProps.get("treinosPlanejados");
        if (treinos != null) {
            treinos.put("minItems", 3);
            treinos.put("maxItems", 5);

            // TREINO items
            Map<String, Object> treinoItems = (Map<String, Object>) treinos.get("items");
            Map<String, Object> treinoProps = treinoItems != null
                    ? (Map<String, Object>) treinoItems.get("properties")
                    : null;

            if (treinoProps != null) {
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

                        // Pattern FC: "60-70% FCmax"
                        Map<String, Object> fc = (Map<String, Object>) etapaProps.get("fcAlvoEtapa");
                        if (fc != null) {
                            fc.put("pattern", "^[0-9]{1,3}-[0-9]{1,3}% FCmax$");
                        }
                    }

                    // Tornar todos os campos da etapa obrigatórios
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



    @Override
    public PlanoSemanalLlmDto gerarPlanoSemanal(AtletaOutputDto atletaOutputDto, List<TreinoRealizadoOutputDto> treinoRealizadoOutputDtoList, PlanoSemanalOutputDto planoSemanalOutputDto) {
        String prompt = promptBuilder.buildRequest(atletaOutputDto, treinoRealizadoOutputDtoList, planoSemanalOutputDto);

        try {
            PlanoSemanalLlmDto plano = chatClient.prompt()
                    .user(prompt)
//                    .options(defaultJsonSchemaOptions())
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

//        String prompt = promptBuilder.buildEnhancedPrompt(atleta, metaDados, prova, inicioSemana);
        String prompt = promptBuilder.buildOptimizedPrompt(atleta, metaDados, prova, inicioSemana);

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
    private void validarPlanoGerado(PlanoSemanalLlmDto plano, UUID atletaId) {

        Atleta atleta = atletaRepository.findById(atletaId).orElseThrow(() -> new LLMException("Atleta não encontrado"));


        if (plano == null || plano.treinosPlanejados() == null) {
            throw new LLMException("Plano gerado está nulo ou sem treinos");
        }

        plano.treinosPlanejados().forEach(treino -> {
            String tipoTreino = treino.tipoTreino();

            // Validar treinos INTERVALADO ou TIRO
            if ("INTERVALADO".equals(tipoTreino) || "TIRO".equals(tipoTreino)) {
                validarTreinoIntervalado(treino, atletaId);
                normalizarTreinoIntervalado(treino, atleta.getNivelExperiencia());
            }

            // Validar treino LONGO
            if ("LONGO".equals(tipoTreino)) {
                validarTreinoLongo(treino, atletaId);
            }

            // Validar repeticoes = 1 em todas as etapas
            validarRepeticoes(treino, atletaId);
        });
    }

    private TreinoPlanejadoLlmDto normalizarTreinoIntervalado(TreinoPlanejadoLlmDto treino, NivelExperiencia nivel) {
        if (!"INTERVALADO".equalsIgnoreCase(treino.tipoTreino())) {
            return treino;
        }

        var etapas = treino.etapas();
        if (etapas == null || etapas.isEmpty()) return treino;

        double alvo = treino.distanciaKm() != null ? treino.distanciaKm() : 0.0;
        if (alvo <= 0.0) return treino;

        // 1) Ajustar aquecimento / desaquecimento para faixas fisiológicas
        var aquecimento   = filtrarPorTipo(etapas, "AQUECIMENTO");
        var desaquecimento = filtrarPorTipo(etapas, "DESAQUECIMENTO");
        clampDistancia(aquecimento,   1.0, 2.0);  // km
        clampDistancia(desaquecimento, 0.8, 1.5); // km

        // 2) Recalcular gap de distância
        double soma = somarDistancias(etapas);
        double gap = alvo - soma; // >0: faltando, <0: sobrando

        if (Math.abs(gap) > 0.05) {
            var tiros = filtrarPorTipo(etapas, "INTERVALADO");
            var recs  = filtrarPorTipo(etapas, "RECUPERACAO");

            // 2.1) Se sobrar bastante distância e ainda dá pra ter mais tiros → adiciona tiro/rec
            int maxTiros = maxTirosPorNivel(nivel);
            int tirosAtuais = tiros.size();

            while (gap > 0.6 && tirosAtuais < maxTiros) {
                // exemplo: novo tiro de 0.8 km + rec de 0.3 km
                adicionarTiroERecuperacao(etapas, 0.8, 0.3, 4, 2);
                tirosAtuais++;
                gap -= 1.1; // aproximado
            }

            // 2.2) Recalcular depois de adicionar tiros
            soma = somarDistancias(etapas);
            gap = alvo - soma;

            // 2.3) Distribuir delta restante nos tiros e recuperações
            if (Math.abs(gap) > 0.05) {
                tiros = filtrarPorTipo(etapas, "INTERVALADO");
                recs  = filtrarPorTipo(etapas, "RECUPERACAO");

                gap = distribuirDeltaDistancia(tiros, gap, 0.4, 1.2);
                gap = distribuirDeltaDistancia(recs,  gap, 0.2, 0.5);

                double somaFinal = somarDistancias(etapas);
                double deltaFinal = alvo - somaFinal;

                if (Math.abs(deltaFinal) > 0.2) {
                    log.warn("NORMALIZADOR: ainda há desvio de distância (alvo={} km, final={} km, delta={})",
                            alvo, somaFinal, deltaFinal);
                }
            }
        }

        // 3) Ajustar duração total do treino = soma das etapas e retornar novo treino
        return recalcularDuracaoTreino(treino);
    }

    private List<EtapaTreinoLlmDto> filtrarPorTipo(List<EtapaTreinoLlmDto> etapas, String tipo) {
        return etapas.stream()
                .filter(e -> tipo.equalsIgnoreCase(e.tipoEtapa()))
                .collect(Collectors.toList());
    }

    private double somarDistancias(List<EtapaTreinoLlmDto> etapas) {
        return etapas.stream()
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();
    }

    private int somarDuracoesMin(List<EtapaTreinoLlmDto> etapas) {
        return etapas.stream()
                .mapToInt(e -> e.duracaoMin() != null ? e.duracaoMin() : 0)
                .sum();
    }

    private void clampDistancia(List<EtapaTreinoLlmDto> etapas, double min, double max) {
        for (int i = 0; i < etapas.size(); i++) {
            EtapaTreinoLlmDto e = etapas.get(i);
            Double d = e.distanciaKm();
            if (d == null || d <= 0) continue;

            // Se a distância está fora dos limites, criar novo record com distância ajustada
            if (d < min || d > max) {
                double novaDistancia = Math.max(min, Math.min(max, d));
                etapas.set(i, new EtapaTreinoLlmDto(
                        e.ordem(),
                        e.tipoEtapa(),
                        e.descricaoEtapa(),
                        e.duracaoMin(),
                        novaDistancia,
                        e.fcAlvoEtapa(),
                        e.repeticoes()
                ));
            }
        }
    }

    /**
     * Distribui um delta de distância entre etapas, respeitando min/max.
     * Retorna quanto ainda sobrou de delta (se não conseguiu distribuir tudo).
     */
    private double distribuirDeltaDistancia(List<EtapaTreinoLlmDto> etapas,
                                            double delta,
                                            double min,
                                            double max) {
        if (etapas == null || etapas.isEmpty()) return delta;
        double restante = delta;

        // Faz algumas "rodadas" de ajuste para ir aproximando
        for (int round = 0; round < 3 && Math.abs(restante) > 0.01; round++) {
            double passo = restante / etapas.size();

            for (int i = 0; i < etapas.size(); i++) {
                if (Math.abs(restante) < 0.01) break;

                EtapaTreinoLlmDto e = etapas.get(i);
                double atual = e.distanciaKm() != null ? e.distanciaKm() : 0.0;
                double proposto = atual + passo;

                if (restante > 0) { // aumentar
                    double novo = Math.min(proposto, max);
                    double aplicado = novo - atual;
                    if (aplicado > 0) {
                        // Criar novo record com distância atualizada
                        etapas.set(i, new EtapaTreinoLlmDto(
                                e.ordem(),
                                e.tipoEtapa(),
                                e.descricaoEtapa(),
                                e.duracaoMin(),
                                atual + aplicado,
                                e.fcAlvoEtapa(),
                                e.repeticoes()
                        ));
                        restante -= aplicado;
                    }
                } else { // diminuir
                    double novo = Math.max(proposto, min);
                    double aplicado = novo - atual;
                    if (aplicado < 0) {
                        // Criar novo record com distância atualizada
                        etapas.set(i, new EtapaTreinoLlmDto(
                                e.ordem(),
                                e.tipoEtapa(),
                                e.descricaoEtapa(),
                                e.duracaoMin(),
                                atual + aplicado,
                                e.fcAlvoEtapa(),
                                e.repeticoes()
                        ));
                        restante -= aplicado;
                    }
                }
            }
        }

        return restante;
    }

    private int maxTirosPorNivel(NivelExperiencia nivel) {
        if (nivel == null) return 5; // default

        return switch (nivel) {
            case INICIANTE      -> 4;
            case INTERMEDIARIO  -> 5;
            case AVANCADO       -> 7;
            case ELITE          -> 10;
        };
    }

    private void adicionarTiroERecuperacao(List<EtapaTreinoLlmDto> etapas,
                                           double distTiro,
                                           double distRec,
                                           int duracaoTiroMin,
                                           int duracaoRecMin) {
        if (etapas.isEmpty()) return;

        // encontrar última ordem
        int ordemMax = etapas.stream()
                .mapToInt(e -> e.ordem() != null ? e.ordem() : 0)
                .max()
                .orElse(0);

        // garantir que desaquecimento continue sendo o último:
        // insere antes da primeira etapa DESAQUECIMENTO (se existir)
        int idxDesaq = -1;
        for (int i = 0; i < etapas.size(); i++) {
            if ("DESAQUECIMENTO".equalsIgnoreCase(etapas.get(i).tipoEtapa())) {
                idxDesaq = i;
                break;
            }
        }

        int insertIndex = (idxDesaq >= 0) ? idxDesaq : etapas.size();

        // criar tiro usando construtor do record
        EtapaTreinoLlmDto tiro = new EtapaTreinoLlmDto(
                ordemMax + 1,
                "INTERVALADO",
                "Tiro extra em Z5",
                duracaoTiroMin,
                distTiro,
                "90-95% FCmáx",
                1
        );

        // criar recuperação usando construtor do record
        EtapaTreinoLlmDto rec = new EtapaTreinoLlmDto(
                ordemMax + 2,
                "RECUPERACAO",
                "Recuperação extra em Z2",
                duracaoRecMin,
                distRec,
                "70-80% FCmáx",
                1
        );

        // inserir antes do desaquecimento (ou no fim)
        etapas.add(insertIndex, tiro);
        etapas.add(insertIndex + 1, rec);

        // reordenar ordens 1..N usando nova lista com records atualizados
        for (int i = 0; i < etapas.size(); i++) {
            EtapaTreinoLlmDto etapa = etapas.get(i);
            etapas.set(i, new EtapaTreinoLlmDto(
                    i + 1, // nova ordem
                    etapa.tipoEtapa(),
                    etapa.descricaoEtapa(),
                    etapa.duracaoMin(),
                    etapa.distanciaKm(),
                    etapa.fcAlvoEtapa(),
                    etapa.repeticoes()
            ));
        }
    }

    private TreinoPlanejadoLlmDto recalcularDuracaoTreino(TreinoPlanejadoLlmDto treino) {
        int totalMin = somarDuracoesMin(treino.etapas());
        String novaDuracao = String.format("%02d:00", totalMin);

        // Criar novo record com duração atualizada
        return new TreinoPlanejadoLlmDto(
                treino.diaSemana(),
                treino.tipoTreino(),
                treino.fcAlvo(),
                treino.tssPlanejado(),
                treino.intensidadePlanejada(),
                treino.percepcaoEsforcoEsperada(),
                treino.justificativaIa(),
                novaDuracao,
                treino.distanciaKm(),
                treino.ritmoAlvo(),
                treino.etapas()
        );
    }


    /**
     * Valida treino intervalado: mínimo 8 etapas, tiros e recuperações balanceados
     */
    private void validarTreinoIntervalado(com.menthoros.dto.llm.TreinoPlanejadoLlmDto treino, Object atletaId) {
        var etapas = treino.etapas();

        // 1) Existência e quantidade mínima de etapas
        if (etapas == null || etapas.isEmpty()) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} sem etapas",
                    atletaId, treino.tipoTreino());
            throw new LLMException(String.format(
                    "Treino %s inválido: não foram geradas etapas",
                    treino.tipoTreino()
            ));
        }

        if (etapas.size() < 8) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} tem apenas {} etapas (mínimo 8)",
                    atletaId, treino.tipoTreino(), etapas.size());
            throw new LLMException(String.format(
                    "Treino %s inválido: gerou apenas %d etapas (mínimo 8 para intervalados)",
                    treino.tipoTreino(), etapas.size()
            ));
        }

        // 2) Tipos básicos de etapa
        boolean temAquecimento = etapas.stream()
                .anyMatch(e -> "AQUECIMENTO".equals(e.tipoEtapa()));
        boolean temDesaquecimento = etapas.stream()
                .anyMatch(e -> "DESAQUECIMENTO".equals(e.tipoEtapa()));

        if (!temAquecimento) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não possui etapa de aquecimento",
                    atletaId, treino.tipoTreino());
            throw new LLMException(String.format(
                    "Treino %s inválido: não possui etapa de aquecimento",
                    treino.tipoTreino()
            ));
        }

        if (!temDesaquecimento) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não possui etapa de desaquecimento",
                    atletaId, treino.tipoTreino());
            throw new LLMException(String.format(
                    "Treino %s inválido: não possui etapa de desaquecimento",
                    treino.tipoTreino()
            ));
        }

        // 3) Ordem lógica: primeiro AQUECIMENTO, último DESAQUECIMENTO
        var primeiraEtapa = etapas.get(0);
        var ultimaEtapa   = etapas.get(etapas.size() - 1);

        if (!"AQUECIMENTO".equals(primeiraEtapa.tipoEtapa())) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não inicia com aquecimento (inicia com {})",
                    atletaId, treino.tipoTreino(), primeiraEtapa.tipoEtapa());
            throw new LLMException(String.format(
                    "Treino %s inválido: deve iniciar com aquecimento",
                    treino.tipoTreino()
            ));
        }

        if (!"DESAQUECIMENTO".equals(ultimaEtapa.tipoEtapa())) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não termina com desaquecimento (termina com {})",
                    atletaId, treino.tipoTreino(), ultimaEtapa.tipoEtapa());
            throw new LLMException(String.format(
                    "Treino %s inválido: deve terminar com desaquecimento",
                    treino.tipoTreino()
            ));
        }

        // 4) Contar tiros e recuperações
        long numTiros = etapas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa()))
                .count();
        long numRecuperacoes = etapas.stream()
                .filter(e -> "RECUPERACAO".equals(e.tipoEtapa()))
                .count();

        if (numTiros < 3) {
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Treino {} tem apenas {} tiros (recomendado: 3+)",
                    atletaId, treino.tipoTreino(), numTiros);
        }

        // Balanceamento tiros x recuperações (pode ter 1 rec a menos se o último tiro não tiver rec)
        if (Math.abs(numTiros - numRecuperacoes) > 1) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} desbalanceado: {} tiros vs {} recuperações",
                    atletaId, treino.tipoTreino(), numTiros, numRecuperacoes);
            throw new LLMException(String.format(
                    "Treino %s inválido: %d tiros mas %d recuperações (devem ser iguais ou diferença de 1)",
                    treino.tipoTreino(), numTiros, numRecuperacoes
            ));
        }

        // 5) Validar sequência: recuperação só pode vir após tiro
        boolean ultimoFoiTiro = false;
        boolean jaTeveTiro = false;
        int recuperacoesInvalidas = 0;

        for (var etapa : etapas) {
            String tipo = etapa.tipoEtapa();

            if ("INTERVALADO".equals(tipo)) {
                jaTeveTiro = true;
                ultimoFoiTiro = true;
            } else if ("RECUPERACAO".equals(tipo)) {
                if (!ultimoFoiTiro) {
                    recuperacoesInvalidas++;
                }
                ultimoFoiTiro = false;
            } else if ("AQUECIMENTO".equals(tipo)) {
                // Se aquecimento for gerado depois de tiro, é estranho (mas vamos só logar)
                if (jaTeveTiro) {
                    log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Aquecimento após tiro detectado em treino {}",
                            atletaId, treino.tipoTreino());
                }
                ultimoFoiTiro = false;
            } else if ("DESAQUECIMENTO".equals(tipo)) {
                // Desaquecimento antes de qualquer tiro também é estranho
                if (!jaTeveTiro) {
                    log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Desaquecimento antes de qualquer tiro em treino {}",
                            atletaId, treino.tipoTreino());
                }
                ultimoFoiTiro = false;
            } else {
                // Outros tipos, se existirem
                ultimoFoiTiro = false;
            }
        }

        if (recuperacoesInvalidas > 0) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} possui {} recuperações sem tiro anterior",
                    atletaId, treino.tipoTreino(), recuperacoesInvalidas);
            throw new LLMException(String.format(
                    "Treino %s inválido: existem recuperações sem tiro imediatamente anterior",
                    treino.tipoTreino()
            ));
        }

        // 6) Distâncias: soma total e proporções
        double somaDistancias = etapas.stream()
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();
        double distanciaPlanejada = treino.distanciaKm() != null ? treino.distanciaKm() : 0.0;

        double diferenca = Math.abs(somaDistancias - distanciaPlanejada);
        if (diferenca > 0.5) { // Tolerância de 500m
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Soma das etapas ({} km) difere da distância planejada ({} km) em {} km",
                    atletaId, somaDistancias, distanciaPlanejada, diferenca);
        }

        double distanciaTiros = etapas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa()))
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();

        double distanciaRecuperacoes = etapas.stream()
                .filter(e -> "RECUPERACAO".equals(e.tipoEtapa()))
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();

        if (distanciaPlanejada > 0.0) {
            // Pelo menos 20% da distância em tiros (garante estímulo mínimo)
            if (distanciaTiros < distanciaPlanejada * 0.20) {
                log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Distância em tiros ({}) muito baixa para total de {} km no treino {}",
                        atletaId, distanciaTiros, distanciaPlanejada, treino.tipoTreino());
            }

            // Recuperação não deve ser a maior parte do treino
            if (distanciaRecuperacoes > distanciaPlanejada * 0.65) {
                log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Distância em recuperação ({}) muito alta para total de {} km no treino {}",
                        atletaId, distanciaRecuperacoes, distanciaPlanejada, treino.tipoTreino());
            }
        }

        // 7) Duração dos tiros (coerência fisiológica geral)
        long tirosInvalidos = etapas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa()))
                .filter(e -> {
                    Double d = Double.valueOf(e.duracaoMin());
                    if (d == null) {
                        return true; // tiro sem duração é inválido
                    }
                    double duracao = d;
                    // Aqui definimos um intervalo "aceitável" genérico:
                    // mínimo ~18s (0.3 min) e máximo 10 min
                    return duracao < 0.3 || duracao > 10.0;
                })
                .count();

        if (tirosInvalidos > 0) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} possui {} tiros com duração incoerente",
                    atletaId, treino.tipoTreino(), tirosInvalidos);
            throw new LLMException(String.format(
                    "Treino %s inválido: existem tiros com duração incoerente (muito curtos ou muito longos)",
                    treino.tipoTreino()
            ));
        }

        // 8) Log final de sucesso
        log.info("VALIDAÇÃO OK [Atleta {}]: Treino {} - {} etapas ({} tiros, {} recuperações, {} km - tiros: {} km, rec: {} km)",
                atletaId,
                treino.tipoTreino(),
                etapas.size(),
                numTiros,
                numRecuperacoes,
                somaDistancias,
                distanciaTiros,
                distanciaRecuperacoes
        );
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
