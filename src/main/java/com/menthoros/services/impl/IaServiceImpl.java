package com.menthoros.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.PlanoTreinoOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.exception.LLMException;
import com.menthoros.services.IaService;
import com.menthoros.services.prompt.PlanoTreinoPromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class IaServiceImpl implements IaService {

    private final ChatClient chatClient;
    private final PlanoTreinoPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public IaServiceImpl(ChatClient chatClient, PlanoTreinoPromptBuilder promptBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public PlanoSemanalOutputDto gerarPlano(AtletaOutputDto atletaOutputDto, List<TreinoRealizadoOutputDto> treinoRealizadoOutputDtoList, PlanoSemanalOutputDto planoSemanalOutputDto) {
        String prompt = promptBuilder.buildRequest(atletaOutputDto, treinoRealizadoOutputDtoList, planoSemanalOutputDto);

        PlanoSemanalOutputDto content = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(PlanoSemanalOutputDto.class);

//        String cleanContent = cleanLLMResponse(content);

//        PlanoSemanalOutputDto outputDto = null;
//        try {
//            outputDto = objectMapper.readValue(cleanContent, PlanoSemanalOutputDto.class);
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException(e);
//        }

        return content;
    }

    @Override
    public Map<Long, PlanoTreinoOutputDto> gerarPlanosEmLote(Map<AtletaOutputDto, List<TreinoRealizadoOutputDto>> atletaDtoListMap) {
        // TODO: Implementar a lógica de geração de planos em lote
        return null;
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
}
