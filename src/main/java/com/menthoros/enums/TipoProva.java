package com.menthoros.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum TipoProva {

    CORRIDA_RUA("CORRIDA_RUA", "Corrida de Rua", "Corrida em asfalto ou ruas", "#2196F3", "5K - 10K"),
    TRAIL("TRAIL", "Trail Running", "Corrida em trilhas e montanhas", "#4CAF50", "Variável"),
    MEIA("MEIA", "Meia Maratona", "Corrida de 21,1 km", "#FF9800", "21.1K"),
    MARATONA("MARATONA", "Maratona", "Corrida de 42,2 km", "#F44336", "42.2K");

    @JsonProperty("value")
    private final String value;
    
    @JsonProperty("label")
    private final String label;
    
    @JsonProperty("description")
    private final String description;
    
    @JsonProperty("color")
    private final String color;
    
    @JsonProperty("distance")
    private final String typicalDistance;

    TipoProva(String value, String label, String description, String color, String typicalDistance) {
        this.value = value;
        this.label = label;
        this.description = description;
        this.color = color;
        this.typicalDistance = typicalDistance;
    }

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

    public String getTypicalDistance() {
        return typicalDistance;
    }
}
