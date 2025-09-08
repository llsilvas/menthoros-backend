package com.menthoros.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum AtletaStatus {
    
    ATIVO("ATIVO", "Ativo", "Atleta ativo no sistema", "#4CAF50", true),
    INATIVO("INATIVO", "Inativo", "Atleta inativo no sistema", "#9E9E9E", false);

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

    AtletaStatus(String value, String label, String description, String color, boolean active) {
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
