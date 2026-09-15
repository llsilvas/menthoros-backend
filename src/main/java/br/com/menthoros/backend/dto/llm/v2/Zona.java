package br.com.menthoros.backend.dto.llm.v2;

/**
 * Zona de intensidade que a LLM escolhe para um bloco (schema v2) — enum fechado, sem parsing de
 * texto livre. {@code LIMIAR} resolve no mesmo índice de {@code Z4} em {@link ZonaTreinoService}
 * ("Limiar anaeróbico", 94-100% FC limiar) — não é um ponto médio entre Z4/Z5.
 */
public enum Zona {
    Z1(1), Z2(2), Z3(3), Z4(4), Z5(5), LIMIAR(4);

    private final int indice;

    Zona(int indice) {
        this.indice = indice;
    }

    /** Índice 1-5 correspondente em {@link br.com.menthoros.backend.services.helper.ZonaTreinoService}. */
    public int indice() {
        return indice;
    }
}
