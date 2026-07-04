package br.com.menthoros.backend.enums;

/**
 * Motivo do reconhecimento (kudo) que o coach dá ao atleta.
 */
public enum MotivoKudos {

    CONSISTENCIA("Consistência"),
    MELHORA("Melhora"),
    ESFORCO("Esforço"),
    SUPERACAO("Superação"),
    VOLTA("Volta por cima");

    private final String label;

    MotivoKudos(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
