package com.menthoros.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.UUID;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Permitir coerção de string vazia para null
        mapper.coercionConfigFor(LogicalType.Enum)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper;
    }

    public static class UUIDDeserializer extends JsonDeserializer<UUID> {
        @Override
        public UUID deserialize(JsonParser p, DeserializationContext ctxt) 
                throws IOException, JsonProcessingException {
            String value = p.getValueAsString();
            
            // Remove aspas extras se existirem
            if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            
            return value != null && !value.trim().isEmpty() ? UUID.fromString(value) : null;
        }
    }
}