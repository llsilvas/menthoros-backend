package br.com.menthoros.backend.enums;

/**
 * Sinais de fadiga que o planner avalia antes de gerar a semana
 * (add-descanso-explicito-por-fadiga).
 *
 * <p>Quem libera descanso vem da literatura, não da intensidade do sinal — ver
 * {@code knowledge/coaching/frequencia-e-descanso-por-fadiga.md}. Com corredores recreacionais,
 * prontidão baixa levou a <b>treino leve</b>, não a descanso (Vesterinen 2016; Düking 2021), e a
 * decisão é do dia. Por isso um sinal de {@link Escopo#SEMANA} (TSB, RPE médio) degrada a
 * intensidade mas não autoriza tirar a sessão; só o sinal do dia — o análogo da VFC matinal dos
 * estudos — libera descanso.</p>
 */
public enum FatigueSignalType {

    /** TSB de prontidão abaixo do limiar do nível. Estado da semana → treino leve. */
    TSB_BAIXO(Escopo.SEMANA, false),

    /** RPE médio dos últimos 7 dias no teto. Estado da semana → treino leve. */
    RPE_ALTO(Escopo.SEMANA, false),

    /** Horas desde o último treino intensivo abaixo do mínimo do nível. */
    RECUPERACAO_INSUFICIENTE(Escopo.DIA, true),

    /** Alerta de dias consecutivos de treino sem descanso. */
    DIAS_CONSECUTIVOS_LIMITE(Escopo.DIA, true),

    /** Check-in de prontidão do dia classificado como DESCANSAR. */
    READINESS_DESCANSAR(Escopo.DIA, true),

    /**
     * Os dias disponíveis formam uma sequência maior que o máximo de dias consecutivos do atleta —
     * regra estrutural do produto (atletas de 6-7 dias), não um estado de fadiga.
     */
    SEQUENCIA_ACIMA_DO_MAXIMO(Escopo.SEQUENCIA, true),

    /** CTL abaixo do mínimo do nível. Base baixa pede frequência com treino leve, não descanso. */
    CTL_BAIXO(Escopo.SEMANA, false);

    /** Em que recorte o sinal vale — decide em quais dias ele pode liberar descanso. */
    public enum Escopo {
        /** Descreve a semana inteira. */
        SEMANA,
        /** Descreve o agora: vale para o primeiro dia efetivo do plano da semana em andamento. */
        DIA,
        /** Vale para os dias dentro da sequência longa. */
        SEQUENCIA
    }

    private final Escopo escopo;
    private final boolean liberaDescanso;

    FatigueSignalType(Escopo escopo, boolean liberaDescanso) {
        this.escopo = escopo;
        this.liberaDescanso = liberaDescanso;
    }

    public Escopo escopo() {
        return escopo;
    }

    public boolean liberaDescanso() {
        return liberaDescanso;
    }
}
