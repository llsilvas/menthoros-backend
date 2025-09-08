package com.menthoros.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum DiaSemana {

    DOMINGO("DOMINGO", "Domingo", "DOM", 0),
    SEGUNDA("SEGUNDA", "Segunda-feira", "SEG", 1),
    TERCA("TERCA", "Terça-feira", "TER", 2),
    QUARTA("QUARTA", "Quarta-feira", "QUA", 3),
    QUINTA("QUINTA", "Quinta-feira", "QUI", 4),
    SEXTA("SEXTA", "Sexta-feira", "SEX", 5),
    SABADO("SABADO", "Sábado", "SAB", 6);

    @JsonProperty("value")
    private final String value;
    
    @JsonProperty("label")
    private final String label;
    
    @JsonProperty("short")
    private final String shortName;
    
    @JsonProperty("order")
    private final int order;

    DiaSemana(String value, String label, String shortName, int order) {
        this.value = value;
        this.label = label;
        this.shortName = shortName;
        this.order = order;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public String getShortName() {
        return shortName;
    }

    public int getOrder() {
        return order;
    }
}
