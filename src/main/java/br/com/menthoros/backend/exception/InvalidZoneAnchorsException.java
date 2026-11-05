package br.com.menthoros.backend.exception;

import java.util.Map;

/** Regra de âncora violada; {@code fields} mapeia o caminho do campo ("fc.maxima") à mensagem. */
public class InvalidZoneAnchorsException extends RuntimeException {

    private final transient Map<String, String> fields;

    public InvalidZoneAnchorsException(Map<String, String> fields) {
        super("Âncoras de zona inválidas: " + fields);
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
