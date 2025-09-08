package com.menthoros.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ProvaStatus {

    PLANEJADA("PLANEJADA", "Planejada", "Prova está no planejamento", "#9E9E9E", false),
    CONFIRMADA("CONFIRMADA", "Confirmada", "Prova confirmada e inscrita", "#2196F3", true),
    CONCLUIDA("CONCLUIDA", "Concluída", "Prova finalizada", "#4CAF50", false),
    CANCELADA("CANCELADA", "Cancelada", "Prova cancelada", "#F44336", false);

    @JsonProperty("value")
    private final String value;
    
    @JsonProperty("label")
    private final String label;
    
    @JsonProperty("description")
    private final String description;
    
    @JsonProperty("color")
    private final String color;
    
    @JsonProperty("active")
    private final boolean active;

    ProvaStatus(String value, String label, String description, String color, boolean active) {
        this.value = value;
        this.label = label;
        this.description = description;
        this.color = color;
        this.active = active;
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

    public boolean isActive() {
        return active;
    }
}
