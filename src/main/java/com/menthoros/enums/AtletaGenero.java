package com.menthoros.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum AtletaGenero {
    
    MASCULINO("MASCULINO", "Masculino", "M"),
    FEMININO("FEMININO", "Feminino", "F"),
    OUTRO("OUTRO", "Outro", "O");

    @JsonProperty("value")
    private final String value;
    
    @JsonProperty("label")
    private final String label;
    
    @JsonProperty("short")
    private final String shortName;

    AtletaGenero(String value, String label, String shortName) {
        this.value = value;
        this.label = label;
        this.shortName = shortName;
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
}

