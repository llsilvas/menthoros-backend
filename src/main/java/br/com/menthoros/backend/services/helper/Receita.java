package br.com.menthoros.backend.services.helper;

import java.util.List;

/**
 * A sequência ordenada de passos de uma família de treino — a "receita de normalização" do
 * glossário. A ordem É a regra de negócio (os dois bugs de ordem da F2 nasceram dela), por isso é
 * dado inspecionável: {@link #nomes()} é o test surface do golden por família.
 */
public record Receita(List<Passo> passos) {

    public Receita {
        passos = List.copyOf(passos);
    }

    public List<String> nomes() {
        return passos.stream().map(Passo::nome).toList();
    }
}
