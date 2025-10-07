package com.menthoros.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menthoros.dto.llm.PlanoSemanalLlmDto;
import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.PlanoTreinoOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.Prova;
import com.menthoros.enums.TipoTreino;
import com.menthoros.exception.LLMException;
import com.menthoros.services.IaService;
import com.menthoros.services.prompt.PlanoTreinoPromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component("springAiFixedIaService")
@ConditionalOnProperty(value = "app.ia.service.strategy", havingValue = "fixed", matchIfMissing = false)
public class SpringAiFixedIaServiceImpl implements IaService {

    private final ChatClient chatClient;
    private final PlanoTreinoPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    
    private static final Pattern UNSAFE_PATTERN = Pattern.compile("[{}$<>\"'\\\\]");
    private static final int MAX_FIELD_LENGTH = 500;
    
    @Value("${spring.ai.openai.chat.options.max-tokens:4000}")
    private Integer maxTokens;
    
    @Value("${spring.ai.openai.chat.options.temperature:0.2}")
    private Double temperature;

    public SpringAiFixedIaServiceImpl(ChatClient chatClient, 
                                     PlanoTreinoPromptBuilder promptBuilder,
                                     ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
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
                List<TreinoRealizadoOutputDto> treinosReduzidos = treinoRealizadoOutputDtoList
                        .subList(0, Math.min(3, treinoRealizadoOutputDtoList.size()));
                userPrompt = promptBuilder.buildRequest(atletaSanitizado, treinosReduzidos, planoSemanalOutputDto);
            }
            
            // 5. Chamada para LLM usando Spring AI (sem options customizadas para evitar erros)
            String response = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();
            
            // 6. Validar e processar resposta
            PlanoSemanalLlmDto plano = parseAndValidateResponse(response);
            
            log.info("Plano gerado com sucesso para atleta {} - {} treinos", 
                    atletaOutputDto.nome(), plano.treinosPlanejados().size());
                    
            return plano;
            
        } catch (Exception e) {
            log.error("Erro na geração de plano para atleta {}: {}", atletaOutputDto.id(), e.getMessage(), e);
            return generateFallbackPlan(atletaOutputDto, treinoRealizadoOutputDtoList);
        }
    }

    @Override
    public PlanoSemanalLlmDto geraPlanoSemanalAvancado(Atleta atleta, PlanoMetaDados metaDados, Prova prova) {
        return null;
    }

    @Override
    public Map<Long, PlanoTreinoOutputDto> gerarPlanosEmLote(Map<AtletaOutputDto, List<TreinoRealizadoOutputDto>> atletaDtoListMap) {
        log.info("Iniciando geração em lote de {} planos", atletaDtoListMap.size());
        // TODO: Implementar processamento assíncrono
        return Map.of();
    }
    
    /**
     * Método usando structured output nativo do Spring AI
     */
    public PlanoSemanalOutputDto gerarPlanoComStructuredOutput(AtletaOutputDto atletaOutputDto, 
                                                              List<TreinoRealizadoOutputDto> treinoRealizadoOutputDtoList, 
                                                              PlanoSemanalOutputDto planoSemanalOutputDto) {
        try {
            AtletaOutputDto atletaSanitizado = sanitizeAtleta(atletaOutputDto);
            String userPrompt = promptBuilder.buildRequest(atletaSanitizado, treinoRealizadoOutputDtoList, planoSemanalOutputDto);
            
            // Usar Spring AI structured output - mais confiável
            return chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .entity(PlanoSemanalOutputDto.class);
                    
        } catch (Exception e) {
            log.error("Erro na geração com structured output: {}", e.getMessage(), e);
            throw new LLMException("Falha na geração de plano com structured output", e);
        }
    }
    
    /**
     * Método com opções customizadas (se OpenAiChatOptions funcionar)
     */
    public PlanoSemanalOutputDto gerarPlanoComOpcoes(AtletaOutputDto atletaOutputDto, 
                                                     List<TreinoRealizadoOutputDto> treinoRealizadoOutputDtoList, 
                                                     PlanoSemanalOutputDto planoSemanalOutputDto) {
        try {
            AtletaOutputDto atletaSanitizado = sanitizeAtleta(atletaOutputDto);
            String userPrompt = promptBuilder.buildRequest(atletaSanitizado, treinoRealizadoOutputDtoList, planoSemanalOutputDto);
            
            // Tentar com opções customizadas
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .temperature(0.1)
                    .maxTokens(maxTokens)
                    .topP(0.9)
                    .build();
            
            return chatClient.prompt()
                    .user(userPrompt)
                    .options(options)
                    .call()
                    .entity(PlanoSemanalOutputDto.class);
                    
        } catch (Exception e) {
            log.error("Erro com opções customizadas: {}", e.getMessage(), e);
            // Fallback para método simples
            return gerarPlanoComStructuredOutput(atletaOutputDto, treinoRealizadoOutputDtoList, planoSemanalOutputDto);
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
        
        String sanitized = UNSAFE_PATTERN.matcher(input).replaceAll("");
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
            
            // Validação estrutural
            if (plano.treinosPlanejados() == null || plano.treinosPlanejados().isEmpty()) {
                throw new LLMException("Resposta do LLM não contém treinos");
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
                               (t.tipoTreino() == TipoTreino.INTERVALADO.getValue() ||
                                t.tipoTreino() == TipoTreino.LONGO.getValue()))
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
//                .semanaInicio(java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)))
//                .semanaFim(java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)))
                .volumePlanejadoKm(volumeConservador)
//                .volumeRealizadoKm(0.0)
                .volumeAlvoKm(volumeConservador)
                .status(com.menthoros.enums.PlanoStatus.ATIVO.getValue())
//                .observacoes("Plano de fallback gerado automaticamente devido à indisponibilidade do serviço de IA")
                .objetivoSemanal("Manter atividade física com volume conservador")
                .treinosPlanejados(new java.util.ArrayList<>()) // Lista vazia por enquanto
                .build();
    }
    
    private int estimateTokenCount(String text) {
        return text.length() / 4; // Aproximação: ~4 chars por token
    }
    
    private Double determineConservativeVolume(AtletaOutputDto atleta) {
        return switch (atleta.nivelExperiencia()) {
            case INICIANTE -> 15.0;
            case INTERMEDIARIO -> 25.0;
            case AVANCADO -> 35.0;
            default -> 20.0;
        };
    }
}