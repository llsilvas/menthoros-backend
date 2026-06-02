package br.com.menthoros.backend.skills.recovery;

/**
 * Input da skill de recuperação e carga para análise fisiológica do atleta.
 *
 * <p>Contém apenas os campos necessários para a avaliação do estado de recuperação,
 * seguindo o princípio de input mínimo: o serviço chamador é responsável por mapear
 * as entidades JPA para este record antes de invocar a skill.</p>
 *
 * @param ctlAtual               Chronic Training Load atual (carga crônica, "fitness")
 * @param atlAtual               Acute Training Load atual (carga aguda, "fadiga")
 * @param tsbProntidaoAtual      Training Stress Balance pré-treino (CTL - ATL, "prontidão")
 * @param rampRateAtual          Taxa de variação semanal do CTL (pontos/semana)
 * @param diasConsecutivosTreino Número de dias consecutivos treinando sem descanso
 * @param temLesaoAtiva          Indica se o atleta possui lesão ativa em acompanhamento
 */
public record RecoveryCargaInput(
        double ctlAtual,
        double atlAtual,
        double tsbProntidaoAtual,
        double rampRateAtual,
        Integer diasConsecutivosTreino,
        boolean temLesaoAtiva
) {}
