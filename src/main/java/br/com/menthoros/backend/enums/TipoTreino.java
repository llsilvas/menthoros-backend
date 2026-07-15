package br.com.menthoros.backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Tipos de treino de corrida com fatores de impacto fisiológico
 *
 * <p>O fator de impacto ajusta o TSS baseado no tipo de treino, refletindo:
 * <ul>
 *   <li>Fadiga neuromuscular (intervalados, tiros, subidas)</li>
 *   <li>Depleção de glicogênio (longões, provas)</li>
 *   <li>Estresse metabólico (tempo run, fartlek)</li>
 *   <li>Tempo de recuperação necessário</li>
 * </ul>
 *
 * @see <a href="https://www.trainingpeaks.com/blog/training-stress-score-explained/">TrainingPeaks TSS</a>
 */
public enum TipoTreino {

    /**
     * Regenerativo: Zona 1, recuperação ativa
     * Ritmo: Conversa fácil, 60-70% FCmax
     * Recuperação: 12-24h
     * Fator Impacto: 0.85 (menor que treino normal)
     */
    REGENERATIVO("REGENERATIVO", "Regenerativo", "Treino leve para recuperação", "#4CAF50", "recovery", 0.85, "Zona 1"),

    /**
     * Intervalado VO2max: Zona 5
     * Ritmo: 3-5min em Z5 (95-100% FCmax) com recuperação
     * Recuperação: 48-72h
     * Fator Impacto: 1.4 (alta fadiga neuromuscular)
     */
    INTERVALADO("INTERVALADO", "Intervalado", "Treino com intervalos de alta e baixa intensidade", "#FF5722", "intervals", 1.4, "Zona 5 (VO2max)"),

    /**
     * Contínuo Moderado: Zona 2-3
     * Ritmo: Conversa possível mas requer esforço, 75-85% FCmax
     * Recuperação: 24-36h
     * Fator Impacto: 1.1
     */
    CONTINUO("CONTINUO", "Contínuo", "Treino em ritmo constante", "#2196F3", "steady", 1.1, "Zona 2-3"),

    /**
     * Longo: Zona 2, duração > 90min
     * Ritmo: Base aeróbica, mas duração causa fadiga metabólica
     * Recuperação: 36-48h
     * Fator Impacto: 1.15 (fadiga por duração)
     */
    LONGO("LONGO", "Longo", "Treino de longa duração", "#9C27B0", "long", 1.15, "Zona 2"),

    /**
     * Tiros de Velocidade: Zona 5+
     * Ritmo: <1min em intensidade máxima (>100% FCmax)
     * Recuperação: 48-72h (fadiga neuromuscular alta)
     * Fator Impacto: 1.5 (máximo impacto neuromuscular)
     */
    TIRO("TIRO", "Tiro", "Treino de velocidade", "#FF9800", "speed", 1.5, "Zona 5+ (Anaeróbico)"),

    /**
     * Fartlek: Zonas variadas (2-4)
     * Ritmo: Mudanças livres de intensidade
     * Recuperação: 36-48h
     * Fator Impacto: 1.2
     */
    FARTLEK("FARTLEK", "Fartlek", "Treino com mudanças de ritmo livres", "#607D8B", "fartlek", 1.2, "Zonas 2-4"),

    /**
     * Tempo Run: Zona 4 (Limiar Anaeróbico)
     * Ritmo: Esforço controlado mas alto, 85-90% FCmax
     * Recuperação: 48h
     * Fator Impacto: 1.25 (esforço sustentado no limiar)
     */
    TEMPO_RUN("TEMPO_RUN", "Tempo Run", "Treino em ritmo de limiar", "#F44336", "tempo", 1.25, "Zona 4 (Limiar)"),

    /**
     * Fácil/Base: Zona 2, base aeróbica
     * Ritmo: Conversa confortável, 70-75% FCmax
     * Recuperação: 24h
     * Fator Impacto: 1.0 (baseline)
     */
    FACIL("FACIL", "Fácil", "Treino de base aeróbica em ritmo confortável", "#8BC34A", "run", 1.0, "Zona 2"),

    /**
     * Tiros de Subida: Zona 4-5 + componente muscular
     * Ritmo: Subidas curtas/médias em alta intensidade
     * Recuperação: 48-72h (fadiga muscular + cardiovascular)
     * Fator Impacto: 1.6 (maior impacto muscular)
     */
    SUBIDA("SUBIDA", "Tiros de Subida", "Treino de força e potência em subidas", "#795548", "mountain", 1.6, "Zona 4-5 + Força"),

    /**
     * Prova/Simulado: Zona 3-4 (race pace)
     * Ritmo: Intensidade de prova (varia por distância)
     * Recuperação: 48-96h (esforço máximo sustentado)
     * Fator Impacto: 1.3 (esforço máximo + componente mental)
     */
    PROVA("PROVA", "Prova/Simulado", "Prova ou simulado em ritmo de competição", "#E91E63", "race", 1.3, "Zona 3-4 (Race Pace)"),

    /**
     * Descanso: dia sem treino, recuperação passiva
     * Ritmo: N/A
     * Recuperação: N/A
     * Fator Impacto: 0.0 (nenhuma carga de treino)
     */
    DESCANSO("DESCANSO", "Descanso", "Dia sem treino - recuperação passiva", "#9E9E9E", "rest", 0.0, "N/A");

    @JsonProperty("value")
    private final String value;

    @JsonProperty("label")
    private final String label;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("color")
    private final String color;

    @JsonProperty("icon")
    private final String icon;

    /**
     * Fator de impacto fisiológico do treino
     * Multiplica o TSS para refletir a real carga de treinamento
     *
     * <p>Valores:
     * <ul>
     *   <li>0.85: Regenerativo (menor impacto)</li>
     *   <li>1.0: Fácil/Base (referência)</li>
     *   <li>1.1-1.2: Moderado/Fartlek</li>
     *   <li>1.15: Longo (fadiga metabólica)</li>
     *   <li>1.25: Tempo Run (esforço no limiar)</li>
     *   <li>1.3: Prova (máximo esforço)</li>
     *   <li>1.4: Intervalado (fadiga neuromuscular)</li>
     *   <li>1.5: Tiros (máximo impacto velocidade)</li>
     *   <li>1.6: Subida (máximo impacto muscular)</li>
     * </ul>
     */
    @JsonProperty("fatorImpacto")
    private final double fatorImpacto;

    /**
     * Zona de frequência cardíaca alvo para o treino
     * Usado pelo LLM para gerar recomendações mais precisas
     */
    @JsonProperty("zonaFcAlvo")
    private final String zonaFcAlvo;

    TipoTreino(String value, String label, String description, String color, String icon,
               double fatorImpacto, String zonaFcAlvo) {
        this.value = value;
        this.label = label;
        this.description = description;
        this.color = color;
        this.icon = icon;
        this.fatorImpacto = fatorImpacto;
        this.zonaFcAlvo = zonaFcAlvo;
    }

    @JsonValue
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

    public String getIcon() {
        return icon;
    }

    public double getFatorImpacto() {
        return fatorImpacto;
    }

    public String getZonaFcAlvo() {
        return zonaFcAlvo;
    }

    @JsonCreator
    public static TipoTreino fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        for (TipoTreino tipo : TipoTreino.values()) {
            if (tipo.value.equals(value) || tipo.name().equals(value)) {
                return tipo;
            }
        }

        // Se não encontrar por value, tenta por name (para compatibilidade)
        try {
            return TipoTreino.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de treino inválido: " + value);
        }
    }
}