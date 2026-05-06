package br.com.menthoros.backend.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum TipoEtapa {

    AQUECIMENTO("AQUECIMENTO", "Aquecimento", "Preparação corporal inicial", "#4CAF50", 1),
    PRINCIPAL("PRINCIPAL", "Principal", "Etapa principal do treino", "#F44336", 2),
    INTERVALADO("INTERVALADO", "Intervalado", "Série de intervalos", "#FF9800", 3),
    RECUPERACAO("RECUPERACAO", "Recuperação", "Pausa ativa ou passiva", "#2196F3", 4),
    DESAQUECIMENTO("DESAQUECIMENTO", "Desaquecimento", "Finalização e relaxamento", "#9C27B0", 5);

    @JsonProperty("value")
    private final String value;
    
    @JsonProperty("label")
    private final String label;
    
    @JsonProperty("description")
    private final String description;
    
    @JsonProperty("color")
    private final String color;
    
    @JsonProperty("order")
    private final int order;

    TipoEtapa(String value, String label, String description, String color, int order) {
        this.value = value;
        this.label = label;
        this.description = description;
        this.color = color;
        this.order = order;
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

    public int getOrder() {
        return order;
    }
}
