package br.com.menthoros.backend.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum NivelExperiencia {
    
    INICIANTE("INICIANTE", "Iniciante", "Até 6 meses de experiência", 1),
    INTERMEDIARIO("INTERMEDIARIO", "Intermediário", "6 meses a 2 anos de experiência", 2),
    AVANCADO("AVANCADO", "Avançado", "Mais de 2 anos de experiência", 3),
    ELITE("ELITE", "Elite", "Mais de 5 anos de experiência", 4);

    @JsonProperty("value")
    private final String value;
    
    @JsonProperty("label")
    private final String label;
    
    @JsonProperty("description")
    private final String description;
    
    @JsonProperty("level")
    private final int level;

    NivelExperiencia(String value, String label, String description, int level) {
        this.value = value;
        this.label = label;
        this.description = description;
        this.level = level;
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

    public int getLevel() {
        return level;
    }
}

