package com.menthoros.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum TreinoExecucaoStatus {
    
    REALIZADO("REALIZADO", "Realizado", "Treino executado conforme planejado", "#4CAF50"),
    PERDIDO("PERDIDO", "Perdido", "Treino não foi realizado", "#F44336"),
    PARCIAL("PARCIAL", "Parcial", "Treino realizado parcialmente", "#FF9800"),
    LIVRE("LIVRE", "Livre", "Treino livre sem estrutura definida", "#2196F3");

    @JsonProperty("value")
    private final String value;
    
    @JsonProperty("label")
    private final String label;
    
    @JsonProperty("description")
    private final String description;
    
    @JsonProperty("color")
    private final String color;

    TreinoExecucaoStatus(String value, String label, String description, String color) {
        this.value = value;
        this.label = label;
        this.description = description;
        this.color = color;
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
}
