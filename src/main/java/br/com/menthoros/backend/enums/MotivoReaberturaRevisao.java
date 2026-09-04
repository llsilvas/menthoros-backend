package br.com.menthoros.backend.enums;

/**
 * Por que um {@link br.com.menthoros.backend.entity.PlanoSemanal} aprovado voltou a
 * {@code AGUARDANDO_REVISAO} sem o atleta ter mexido no plano diretamente — a prova é a única
 * causa hoje (prova-no-plano-semanal, D4). Limpo quando o coach aprova ou rejeita de novo.
 */
public enum MotivoReaberturaRevisao {

    /** Uma prova passou a cair na semana e o treino PROVA foi inserido no dia. */
    PROVA_INSERIDA,

    /** A prova que estava na semana foi cancelada ou movida para outra data. */
    PROVA_REMOVIDA
}
