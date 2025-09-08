package com.menthoros.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menthoros.dto.llm.PlanoSemanalLlmDto;
import com.menthoros.dto.llm.TreinoPlanejadoLlmDto;
import com.menthoros.dto.output.*;
import com.menthoros.enums.TipoEtapa;
import com.menthoros.enums.TipoTreino;
import com.menthoros.exception.LLMException;
import com.menthoros.services.IaService;
import com.menthoros.services.prompt.PlanoTreinoPromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
@ConditionalOnProperty(value = "app.ia.service.strategy", havingValue = "enhanced", matchIfMissing = true)
public class SpringAiEnhancedIaServiceImpl_old implements IaService {

    private final ChatClient chatClient;
    private final PlanoTreinoPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    
    private static final Pattern UNSAFE_PATTERN = Pattern.compile("[{}$<>\"'\\\\]");
    private static final int MAX_FIELD_LENGTH = 500;
    
    @Value("${spring.ai.openai.chat.options.max-tokens:4000}")
    private Integer maxTokens;
    
    @Value("${spring.ai.openai.chat.options.temperature:0.2}")
    private Double temperature;
    
    @Value("${app.llm.timeout:30000}")
    private int timeoutMs;

    public SpringAiEnhancedIaServiceImpl_old(ChatClient chatClient,
                                             PlanoTreinoPromptBuilder promptBuilder,
                                             ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    @Cacheable(value = "ia-responses", key = "#atletaOutputDto.id + '_' + #treinoRealizadoOutputDtoList.size()")
    public PlanoSemanalLlmDto gerarPlanoSemanal(AtletaOutputDto atletaOutputDto,
                                         List<TreinoRealizadoOutputDto> treinoRealizadoOutputDtoList,
                                         PlanoSemanalOutputDto planoSemanalOutputDto) {
        
        log.info("Iniciando geração de plano para atleta: {} (ID: {})", 
                atletaOutputDto.nome(), atletaOutputDto.id());
        
        try {
            // 1. Validar entrada
            validateInput(atletaOutputDto, treinoRealizadoOutputDtoList);
            
            // 2. Sanitizar atleta
            AtletaOutputDto atletaSanitizado = sanitizeAtleta(atletaOutputDto);
            
            // 3. Construir prompt
            String userPrompt = promptBuilder.buildRequest(atletaSanitizado, treinoRealizadoOutputDtoList, planoSemanalOutputDto);
            
            // 4. Verificar tamanho do contexto
            if (estimateTokenCount(userPrompt) > maxTokens * 0.8) {
                log.warn("Prompt muito longo para atleta {}, truncando histórico", atletaOutputDto.id());
                // Reduzir histórico de treinos
                List<TreinoRealizadoOutputDto> treinosReduzidos = treinoRealizadoOutputDtoList
                        .subList(0, Math.min(3, treinoRealizadoOutputDtoList.size()));
                userPrompt = promptBuilder.buildRequest(atletaSanitizado, treinosReduzidos, planoSemanalOutputDto);
            }
            
            // 5. Configurar opções do OpenAI
            OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                    .temperature(temperature.doubleValue())
                    .maxTokens(maxTokens)
                    .topP(0.9)
                    .frequencyPenalty(0.1)
                    .presencePenalty(0.0)
                    .build();
            
            // 6. Chamada para LLM
            String response = chatClient.prompt()
                    .user(userPrompt)
                    .options(chatOptions)
                    .call()
                    .content();
            
            // 7. Validar e processar resposta
            log.info("Resposta bruta da IA para atleta {}: {}", atletaOutputDto.nome(), response);
            PlanoSemanalLlmDto plano = parseAndValidateResponse(response);
            
            log.info("Plano gerado com sucesso para atleta {} - {} treinos", 
                    atletaOutputDto.nome(), plano.treinosPlanejados().size());
                    
            // Log detalhado dos treinos gerados
            plano.treinosPlanejados().forEach(treino ->
                log.debug("Treino gerado: {} - {} - {}km - {}min", 
                    treino.diaSemana(), treino.tipoTreino(), 
                    treino.distanciaKm(), treino.duracaoMin()));
            
                    
            return plano;
            
        } catch (Exception e) {
            log.error("Erro na geração de plano para atleta {}: {}", atletaOutputDto.id(), e.getMessage(), e);
            log.info("Ativando fallback automaticamente para garantir resposta válida");
            return generateFallbackPlan(atletaOutputDto, treinoRealizadoOutputDtoList);
        }
    }

    @Override
    public Map<Long, PlanoTreinoOutputDto> gerarPlanosEmLote(Map<AtletaOutputDto, List<TreinoRealizadoOutputDto>> atletaDtoListMap) {
        log.info("Iniciando geração em lote de {} planos", atletaDtoListMap.size());
        // TODO: Implementar processamento assíncrono
        return Map.of();
    }
    
    /**
     * Método específico para usar structured output do OpenAI via Spring AI
     */
    public PlanoSemanalOutputDto gerarPlanoComStructuredOutput(AtletaOutputDto atletaOutputDto, 
                                                              List<TreinoRealizadoOutputDto> treinoRealizadoOutputDtoList, 
                                                              PlanoSemanalOutputDto planoSemanalOutputDto) {
        try {
            AtletaOutputDto atletaSanitizado = sanitizeAtleta(atletaOutputDto);
            String userPrompt = promptBuilder.buildRequest(atletaSanitizado, treinoRealizadoOutputDtoList, planoSemanalOutputDto);
            
            OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                    .temperature(0.1) // Mais determinístico para JSON
                    .maxTokens(maxTokens)
                    .build();
            
            // Usar Spring AI structured output
            return chatClient.prompt()
                    .user(userPrompt)
                    .options(chatOptions)
                    .call()
                    .entity(PlanoSemanalOutputDto.class);
                    
        } catch (Exception e) {
            log.error("Erro na geração com structured output: {}", e.getMessage(), e);
            throw new LLMException("Falha na geração de plano com structured output", e);
        }
    }
    
    private void validateInput(AtletaOutputDto atleta, List<TreinoRealizadoOutputDto> treinos) {
        if (atleta == null || atleta.id() == null) {
            throw new IllegalArgumentException("Dados do atleta inválidos");
        }
        
        if (atleta.nome() == null || atleta.nome().trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do atleta é obrigatório");
        }
        
        if (atleta.objetivo() != null && atleta.objetivo().length() > 500) {
            throw new IllegalArgumentException("Objetivo do atleta muito longo (máximo 500 caracteres)");
        }
    }
    
    private AtletaOutputDto sanitizeAtleta(AtletaOutputDto atleta) {
        return new AtletaOutputDto(
                atleta.id(),
                sanitizeString(atleta.nome()),
                Math.max(10, Math.min(100, atleta.idade())),
                BigDecimal.valueOf(Math.max(30.0, Math.min(300.0, atleta.pesoKg().doubleValue()))),
                BigDecimal.valueOf(Math.max(100.0, Math.min(250.0, atleta.alturaCm().doubleValue()))),
                sanitizeString(atleta.objetivo()),
                atleta.nivelExperiencia(),
                atleta.diasDisponiveis(),
                atleta.diaPreferidoLongo(),
                atleta.temLesao(),
                sanitizeString(atleta.descricaoLesao()),
                atleta.provas()
        );
    }
    
    private String sanitizeString(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }
        
        // Remove caracteres potencialmente perigosos
        String sanitized = UNSAFE_PATTERN.matcher(input).replaceAll("");
        
        // Limita tamanho
        return truncateText(sanitized, MAX_FIELD_LENGTH);
    }
    
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    private PlanoSemanalLlmDto parseAndValidateResponse(String content) {
        try {
            String cleanContent = cleanLLMResponse(content);
            PlanoSemanalLlmDto plano = objectMapper.readValue(cleanContent, PlanoSemanalLlmDto.class);
            
            // Validação estrutural - se não tem treinos, vai para fallback
            if (plano.treinosPlanejados() == null || plano.treinosPlanejados().isEmpty()) {
                log.warn("IA retornou plano sem treinos. Conteúdo da resposta: {}", cleanContent);
                log.info("Acionando fallback devido à ausência de treinos na resposta da IA");
                throw new LLMException("Resposta do LLM não contém treinos - ativando fallback");
            }
            
            if (plano.treinosPlanejados().size() > 7) {
                throw new LLMException("Número excessivo de treinos retornado pelo LLM");
            }
            
            validatePlanSafety(plano);
            return plano;
            
        } catch (JsonProcessingException e) {
            log.error("Erro ao fazer parse da resposta do LLM: {}", content);
            throw new LLMException("Resposta do LLM inválida", e);
        }
    }
    
    private String cleanLLMResponse(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new LLMException("Resposta vazia do LLM");
        }
        
        // Remover markdown code blocks
        content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        
        // Encontrar JSON
        int startJson = content.indexOf('{');
        int endJson = content.lastIndexOf('}');
        
        if (startJson == -1 || endJson == -1) {
            throw new LLMException("JSON não encontrado na resposta do LLM");
        }
        
        String jsonContent = content.substring(startJson, endJson + 1);
        
        // Fix mixed JSON syntax - add quotes to unquoted keys
        jsonContent = fixJsonSyntax(jsonContent);
        
        return jsonContent;
    }
    
    private String fixJsonSyntax(String json) {
        // Fix double-quoted keys (remove extra quotes)
        json = json.replaceAll("\"([a-zA-Z_][a-zA-Z0-9_]*)\"\"", "\"$1\"");
        
        // Fix unquoted object keys
        json = json.replaceAll("([{,\\s]+)([a-zA-Z_][a-zA-Z0-9_]*)(\\s*:)", "$1\"$2\"$3");
        
        // Fix single quotes around string values to double quotes
        json = json.replaceAll(":\\s*'([^']*)'", ": \"$1\"");
        
        // Fix invalid UUIDs by generating valid ones
        json = fixInvalidUUIDs(json);
        
        // Fix numeric values that are quoted but shouldn't be
        json = json.replaceAll(":\\s*\"(\\d+)\"", ": $1");
        json = json.replaceAll(":\\s*\"(-?\\d*\\.\\d+)\"", ": $1");
        
        // Fix null values
        json = json.replaceAll(":\\s*\"null\"", ": null");
        json = json.replaceAll(":\\s*null(?![a-zA-Z0-9_])", ": null");
        
        // Clean up spacing
        json = json.replaceAll("\"\\s*:\\s*", "\": ");
        json = json.replaceAll(",\\s*([\"{}\\[])", ", $1");
        
        return json;
    }
    
    private String fixInvalidUUIDs(String json) {
        // Pattern to match UUID fields with invalid values
        java.util.regex.Pattern uuidFieldPattern = java.util.regex.Pattern.compile(
            "\"(atletaId|planoTreinoId|planoSemanalId)\"\\s*:\\s*\"([^\"]*)\""
        );
        
        java.util.regex.Matcher matcher = uuidFieldPattern.matcher(json);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            String currentValue = matcher.group(2);
            
            // Check if current value is a valid UUID format
            if (!isValidUUID(currentValue)) {
                // Generate a new valid UUID
                String newUUID = java.util.UUID.randomUUID().toString();
                matcher.appendReplacement(sb, "\"" + fieldName + "\": \"" + newUUID + "\"");
                log.debug("Substituindo UUID inválido '{}' por '{}' no campo '{}'", currentValue, newUUID, fieldName);
            } else {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    private boolean isValidUUID(String uuid) {
        try {
            java.util.UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    private void validatePlanSafety(PlanoSemanalLlmDto plano) {
        // Validar volumes seguros (volumePlanejadoKm é double primitivo, não pode ser null)
        if (plano.volumePlanejadoKm() > 200.0) {
            log.warn("Volume semanal muito alto detectado: {} km", plano.volumePlanejadoKm());
        }
        
        // Validar distribuição de treinos
        if (plano.treinosPlanejados() != null) {
            long treinosIntensos = plano.treinosPlanejados().stream()
                    .filter(t -> t.tipoTreino() != null &&
                               (t.tipoTreino() == TipoTreino.INTERVALADO ||
                                t.tipoTreino() == TipoTreino.LONGO))
                    .count();
                    
            if (treinosIntensos > 3) {
                log.warn("Muitos treinos intensos detectados: {}", treinosIntensos);
            }
        }
    }
    
    private PlanoSemanalLlmDto generateFallbackPlan(AtletaOutputDto atleta, List<TreinoRealizadoOutputDto> treinos) {
        log.info("Gerando plano de fallback para atleta {}", atleta.nome());
        
        Double volumeConservador = determineConservativeVolume(atleta);
        
        // Agora PlanoSemanalOutputDto tem @Builder
        return PlanoSemanalLlmDto.builder()
//                .atletaId(atleta.id())
//                .semanaInicio(LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)))
//                .semanaFim(LocalDate.now().with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)))
                .volumePlanejadoKm(volumeConservador)
                .volumeRealizadoKm(0.0)
                .volumeAlvoKm(volumeConservador)
                .status(com.menthoros.enums.PlanoStatus.ATIVO)
//                .observacoes("Plano de fallback gerado automaticamente devido à indisponibilidade do serviço de IA")
                .objetivoSemanal("Manter atividade física com volume conservador")
                .treinosPlanejados(generateBasicTrainingPlan(atleta))
                .build();
    }
    
    private Double determineConservativeVolume(AtletaOutputDto atleta) {
        return switch (atleta.nivelExperiencia()) {
            case INICIANTE -> 15.0;
            case INTERMEDIARIO -> 25.0;
            case AVANCADO -> 35.0;
            default -> 20.0;
        };
    }
    
    private int estimateTokenCount(String text) {
        return text.length() / 4; // Aproximação: ~4 chars por token
    }
    
    private List<TreinoPlanejadoLlmDto> generateBasicTrainingPlan(AtletaOutputDto atleta) {
        log.info("Gerando plano básico de fallback para atleta {}", atleta.nome());
        
        List<TreinoPlanejadoLlmDto> treinos = new ArrayList<>();
        
        // Treino básico de acordo com o nível
        int numeroTreinos = switch (atleta.nivelExperiencia()) {
            case INICIANTE -> 3;
            case INTERMEDIARIO -> 4;
            case AVANCADO -> 5;
            default -> 3;
        };
        
        // Gerar treinos básicos de fallback
        for (int i = 0; i < numeroTreinos; i++) {
            TreinoPlanejadoLlmDto treino = new TreinoPlanejadoLlmDto(
//                    UUID.randomUUID(),
                    getDiaSemanaByIndex(i),
                    getTipoTreinoByIndex(i, atleta.nivelExperiencia()),
                    "Treino básico de " + getTipoTreinoByIndex(i, atleta.nivelExperiencia()).name().toLowerCase(),
                    "Treino básico gerado automaticamente",
                    null, // fcAlvo
//                    null, // dataTreino - será definido no service
                    null, // percepcaoEsforcoEsperada
                    getDuracaoByNivel(atleta.nivelExperiencia(), i),
                    getDistanciaByNivel(atleta.nivelExperiencia(), i),
                    null, // ritmoAlvo
//                    null, // planoSemanalId - será definido no service
                    generateBasicEtapas(atleta.nivelExperiencia(), i)
            );
            treinos.add(treino);
        }
        
        log.info("Gerado plano básico com {} treinos para atleta {}", treinos.size(), atleta.nome());
        return treinos;
    }
    
    private com.menthoros.enums.DiaSemana getDiaSemanaByIndex(int index) {
        com.menthoros.enums.DiaSemana[] dias = com.menthoros.enums.DiaSemana.values();
        return dias[index % dias.length];
    }
    
    private com.menthoros.enums.TipoTreino getTipoTreinoByIndex(int index, com.menthoros.enums.NivelExperiencia nivel) {
        return switch (index) {
            case 0 -> com.menthoros.enums.TipoTreino.REGENERATIVO;
            case 1 -> com.menthoros.enums.TipoTreino.TIRO;
            case 2 -> com.menthoros.enums.TipoTreino.LONGO;
            case 3 -> nivel == com.menthoros.enums.NivelExperiencia.AVANCADO ? 
                     com.menthoros.enums.TipoTreino.INTERVALADO : com.menthoros.enums.TipoTreino.REGENERATIVO;
            case 4 -> com.menthoros.enums.TipoTreino.FARTLEK;
            default -> com.menthoros.enums.TipoTreino.REGENERATIVO;
        };
    }
    
    private Double getDistanciaByNivel(com.menthoros.enums.NivelExperiencia nivel, int treinoIndex) {
        return switch (nivel) {
            case INICIANTE -> switch (treinoIndex) {
                case 0, 1 -> 3.0;
                case 2 -> 5.0; // Longo
                default -> 3.0;
            };
            case INTERMEDIARIO -> switch (treinoIndex) {
                case 0, 1 -> 5.0;
                case 2 -> 8.0; // Longo
                case 3 -> 4.0;
                default -> 5.0;
            };
            case AVANCADO -> switch (treinoIndex) {
                case 0, 1 -> 8.0;
                case 2 -> 12.0; // Longo
                case 3 -> 6.0; // Intervalado
                case 4 -> 5.0;
                default -> 8.0;
            };
            default -> 5.0;
        };
    }
    
    private Integer getDuracaoByNivel(com.menthoros.enums.NivelExperiencia nivel, int treinoIndex) {
        return switch (nivel) {
            case INICIANTE -> switch (treinoIndex) {
                case 0, 1 -> 30;
                case 2 -> 45; // Longo
                default -> 30;
            };
            case INTERMEDIARIO -> switch (treinoIndex) {
                case 0, 1 -> 45;
                case 2 -> 60; // Longo
                case 3 -> 40;
                default -> 45;
            };
            case AVANCADO -> switch (treinoIndex) {
                case 0, 1 -> 60;
                case 2 -> 90; // Longo
                case 3 -> 45; // Intervalado
                case 4 -> 50;
                default -> 60;
            };
            default -> 45;
        };
    }
    
    private List<com.menthoros.dto.output.EtapaTreinoDto> generateBasicEtapas(com.menthoros.enums.NivelExperiencia nivel, int treinoIndex) {
        List<com.menthoros.dto.output.EtapaTreinoDto> etapas = new ArrayList<>();
        
        // Etapa básica de aquecimento
        etapas.add(new com.menthoros.dto.output.EtapaTreinoDto(
                1, // ordem
                TipoEtapa.AQUECIMENTO, // tipoEtapa
                "Corrida leve para aquecimento", // descricaoEtapa
                10, // duracaoMin
                1.0, // distanciaKm
                null, // fcAlvoEtapa
                null // repeticoes
        ));
        
        // Etapa principal
        etapas.add(new com.menthoros.dto.output.EtapaTreinoDto(
                2, // ordem
                TipoEtapa.PRINCIPAL, // tipoEtapa
                "Parte principal do treino", // descricaoEtapa
                getDuracaoByNivel(nivel, treinoIndex) - 15, // duracaoMin
                Math.max(1.0, getDistanciaByNivel(nivel, treinoIndex) - 2.0), // distanciaKm
                null, // fcAlvoEtapa
                null // repeticoes
        ));
        
        // Volta à calma
        etapas.add(new com.menthoros.dto.output.EtapaTreinoDto(
                3, // ordem
                TipoEtapa.DESAQUECIMENTO, // tipoEtapa
                "Corrida muito leve para recuperação", // descricaoEtapa
                5, // duracaoMin
                1.0, // distanciaKm
                null, // fcAlvoEtapa
                null // repeticoes
        ));
        
        return etapas;
    }
    
    private String getIntensidadePrincipal(int treinoIndex) {
        return switch (treinoIndex) {
            case 0 -> "Leve";
            case 1 -> "Moderada";
            case 2 -> "Moderada"; // Longo
            case 3 -> "Alta"; // Intervalado
            default -> "Moderada";
        };
    }
}