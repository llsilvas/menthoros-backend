package com.menthoros.enums;

/**
 * Categorias de treino intervalado para prescrição segura e progressiva.
 *
 * <ul>
 *   <li>A — VO2max curto: tiros de 200-600m em Z5 (95-100% FCmax)</li>
 *   <li>B — VO2max longo: repetições de 3-5 min em Z5</li>
 *   <li>C — Threshold: blocos de 4-6 min no pace de limiar (Z4)</li>
 *   <li>D — Tempo Run / Fartlek suave: contínuo Z3 ou fartlek estruturado leve</li>
 *   <li>E — Fartlek específico de prova: variações no pace-alvo de competição</li>
 * </ul>
 *
 * Rotação recomendada por fase:
 * <ul>
 *   <li>BASE       → A ou B (constrói motor aeróbico)</li>
 *   <li>BUILD      → B ou C (adiciona threshold)</li>
 *   <li>ESPECIFICO → C ou E (pace de prova)</li>
 *   <li>TAPER      → D apenas (manutenção suave)</li>
 * </ul>
 */
public enum CategoriaIntervalado {

    A("VO2max curto",
      "Tiros de 200-600m em Z5 (95-100% FCmax). Recuperação 1:3.",
      "Prescreva 6-10 tiros de 200-600m em Z5 com recuperação de 90-180s em Z1."),

    B("VO2max longo",
      "Repetições de 3-5 min em Z5. Recuperação 1:1.",
      "Prescreva 4-6 repetições de 3-5 min em Z5 com recuperação igual ao esforço em Z1-Z2."),

    C("Threshold",
      "Blocos de 4-6 min no pace de limiar Z4 (85-90% FCmax).",
      "Prescreva 3-5 blocos de 4-6 min em Z4 com recuperação de 2 min em Z1-Z2."),

    D("Tempo Run / Fartlek suave",
      "Corrida contínua em Z3 ou fartlek leve.",
      "Prescreva fartlek livre de 20-30 min em Z2-Z3 com acelerações espontâneas curtas."),

    E("Fartlek específico de prova",
      "Variações no pace-alvo de competição.",
      "Prescreva fartlek estruturado com 10-15 min no pace de prova intercalados com Z2.");

    private final String nome;
    private final String descricao;
    private final String instrucaoPadrao;

    CategoriaIntervalado(String nome, String descricao, String instrucaoPadrao) {
        this.nome = nome;
        this.descricao = descricao;
        this.instrucaoPadrao = instrucaoPadrao;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getInstrucaoPadrao() {
        return instrucaoPadrao;
    }
}
