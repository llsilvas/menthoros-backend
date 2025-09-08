package com.menthoros.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TipoTreino {
    
    REGENERATIVO("REGENERATIVO", "Regenerativo", "Treino leve para recuperação", "#4CAF50", "recovery"),
    INTERVALADO("INTERVALADO", "Intervalado", "Treino com intervalos de alta e baixa intensidade", "#FF5722", "intervals"),
    CONTINUO("CONTINUO", "Contínuo", "Treino em ritmo constante", "#2196F3", "steady"),
    LONGO("LONGO", "Longo", "Treino de longa duração", "#9C27B0", "long"),
    TIRO("TIRO", "Tiro", "Treino de velocidade", "#FF9800", "speed"),
    FARTLEK("FARTLEK", "Fartlek", "Treino com mudanças de ritmo livres", "#607D8B", "fartlek"),
    TEMPO_RUN("TEMPO_RUN", "Tempo Run", "Treino em ritmo de limiar", "#F44336", "tempo");

    @JsonProperty("value")
    private final String value;
    
    @JsonProperty("label")
    private final String label;
    
    @JsonProperty("description")
    private final String description;
    
    @JsonProperty("color")
    private final String color;
    
    @JsonProperty("icon")
    private final String icon;

    TipoTreino(String value, String label, String description, String color, String icon) {
        this.value = value;
        this.label = label;
        this.description = description;
        this.color = color;
        this.icon = icon;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }

    @JsonCreator
    public static TipoTreino fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        for (TipoTreino tipo : TipoTreino.values()) {
            if (tipo.value.equals(value) || tipo.name().equals(value)) {
                return tipo;
            }
        }

        // Se não encontrar por value, tenta por name (para compatibilidade)
        try {
            return TipoTreino.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de treino inválido: " + value);
        }
    }
}
