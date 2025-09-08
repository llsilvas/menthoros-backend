package com.menthoros.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum FonteDados {
    
    GARMIN("GARMIN", "Garmin Connect", "Sincronização via Garmin", "#007ACC"),
    STRAVA("STRAVA", "Strava", "Sincronização via Strava", "#FC4C02"),
    MANUAL("MANUAL", "Manual", "Inserção manual de dados", "#9E9E9E");

    @JsonProperty("value")
    private final String value;
    
    @JsonProperty("label")
    private final String label;
    
    @JsonProperty("description")
    private final String description;
    
    @JsonProperty("color")
    private final String color;

    FonteDados(String value, String label, String description, String color) {
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
