package br.com.menthoros.backend.enums;

/**
 * Classificação de prontidão subjetiva diária do atleta, derivada do {@code readinessScore}.
 *
 * <ul>
 *   <li>PRONTO — score ≥ 0.75. Sem restrição adicional na prescrição.</li>
 *   <li>CAUTELOSO — score entre 0.50 e 0.74. Intervalado permitido com atenuação de volume (20–30%).</li>
 *   <li>DESCANSAR — score &lt; 0.50. Bloqueio total de intervalado nesta data.</li>
 * </ul>
 */
public enum NivelProntidao {

    PRONTO("Pronto",
           "Sinais subjetivos positivos — sem restrição adicional na prescrição."),

    CAUTELOSO("Cauteloso",
              "Sinais mistos — intervalado permitido com atenuação de volume entre 20% e 30%."),

    DESCANSAR("Descansar",
              "Sinais críticos — bloqueio total de intervalado; priorizar recuperação.");

    private final String label;
    private final String interpretacao;

    NivelProntidao(String label, String interpretacao) {
        this.label = label;
        this.interpretacao = interpretacao;
    }

    public String getLabel() {
        return label;
    }

    public String getInterpretacao() {
        return interpretacao;
    }
}
