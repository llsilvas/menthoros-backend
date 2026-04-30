package br.com.menthoros.backend.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum FonteDados {

    MANUAL("MANUAL", "Manual", "Criado manualmente no sistema", "#9E9E9E"),
    IA_GERADO("IA_GERADO", "IA Gerado", "Gerado pela IA do sistema", "#9C27B0"),
    GARMIN("GARMIN", "Garmin Connect", "Importado do Garmin", "#007ACC"),
    STRAVA("STRAVA", "Strava", "Importado do Strava", "#FC4C02"),
    TRAINING_PEAKS("TRAINING_PEAKS", "TrainingPeaks", "Importado do TrainingPeaks (futuro)", "#F57C00"),
    POLAR("POLAR", "Polar Flow", "Importado do Polar Flow (futuro)", "#E30613"),
    WAHOO("WAHOO", "Wahoo Fitness", "Importado do Wahoo Fitness (futuro)", "#0066CC");

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
