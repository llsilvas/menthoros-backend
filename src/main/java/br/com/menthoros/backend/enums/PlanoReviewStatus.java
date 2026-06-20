package br.com.menthoros.backend.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum PlanoReviewStatus {

    AGUARDANDO_REVISAO("AGUARDANDO_REVISAO", "Aguardando revisão", "Plano gerado pela IA, pendente de aprovação do coach", "#FF9800", false),
    APROVADO("APROVADO", "Aprovado", "Plano aprovado pelo coach e visível ao atleta", "#4CAF50", true),
    REJEITADO("REJEITADO", "Rejeitado", "Plano rejeitado pelo coach; atleta não visualiza", "#F44336", false);

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

    PlanoReviewStatus(String value, String label, String description, String color, boolean active) {
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
