package br.com.menthoros.backend.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum PlanoStatus {

    PLANEJADO("PLANEJADO", "Planejado", "Plano criado mas ainda não iniciado", "#9E9E9E", false),
    INICIADO("INICIADO", "Iniciado", "Plano foi iniciado recentemente", "#2196F3", true),
    EM_ANDAMENTO("EM_ANDAMENTO", "Em Andamento", "Plano está sendo executado", "#FF9800", true),
    ATIVO("ATIVO", "Ativo", "Plano está ativo e em execução", "#4CAF50", true),
    CONCLUIDO("CONCLUIDO", "Concluído", "Plano foi finalizado com sucesso", "#607D8B", false);

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

    PlanoStatus(String value, String label, String description, String color, boolean active) {
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
