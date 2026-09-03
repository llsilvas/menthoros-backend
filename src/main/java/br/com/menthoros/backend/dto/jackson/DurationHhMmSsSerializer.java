package br.com.menthoros.backend.dto.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Duration;

/**
 * Serializa {@link Duration} como {@code "HH:mm:ss"} — ver {@link DurationHhMmSsFormat}.
 */
public class DurationHhMmSsSerializer extends JsonSerializer<Duration> {

    @Override
    public void serialize(Duration value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(DurationHhMmSsFormat.format(value));
    }
}
