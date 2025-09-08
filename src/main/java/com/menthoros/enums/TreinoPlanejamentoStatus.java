package com.menthoros.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum TreinoPlanejamentoStatus {
    
    RASCUNHO("RASCUNHO", "Rascunho", "Treino em elaboração", "#9E9E9E", false),
    REVISADO("REVISADO", "Revisado", "Treino revisado e aprovado", "#FF9800", false),
    ENVIADO("ENVIADO", "Enviado", "Treino enviado ao atleta", "#4CAF50", true);

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

    TreinoPlanejamentoStatus(String value, String label, String description, String color, boolean active) {
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
