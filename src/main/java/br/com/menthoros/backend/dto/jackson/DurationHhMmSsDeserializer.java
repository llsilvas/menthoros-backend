package br.com.menthoros.backend.dto.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * Deserializa {@code "HH:mm:ss"} para {@link Duration} — ver {@link DurationHhMmSsFormat}.
 */
public class DurationHhMmSsDeserializer extends JsonDeserializer<Duration> {

    @Override
    public Duration deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String valor = p.getValueAsString();
        try {
            return DurationHhMmSsFormat.parse(valor);
        } catch (DateTimeParseException e) {
            throw ctxt.weirdStringException(valor, Duration.class, e.getMessage());
        }
    }
}
