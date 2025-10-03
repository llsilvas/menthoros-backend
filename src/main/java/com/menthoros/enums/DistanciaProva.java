package com.menthoros.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum DistanciaProva {

    KM_5 ("5K", "5K", "5 KM", 0),
    KM_10 ("10K", "10K", "10 KM", 1),
    KM_21 ("21K", "21K", "21 KM", 2),
    KM_42 ("42K", "42K", "42 KM", 3);

    @JsonProperty("value")
    private final String value;

    @JsonProperty("label")
    private final String label;

    @JsonProperty("short")
    private final String shortName;

    @JsonProperty("order")
    private final int order;

    DistanciaProva(String value, String label, String shortName, int order) {
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
