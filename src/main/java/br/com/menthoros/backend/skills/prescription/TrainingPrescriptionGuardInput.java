package br.com.menthoros.backend.skills.prescription;

import java.util.List;

/**
 * Input da skill de guarda de prescrição de treino.
 *
 * <p>Contém o plano semanal proposto pelo LLM e as métricas de carga atuais do atleta
 * para validação de segurança antes da geração final da prescrição.</p>
 *
 * @param treinosPropostos    lista de sessões de treino propostas pelo LLM para a semana
 * @param ctlAtual            Chronic Training Load atual (carga crônica, "fitness")
 * @param tsbProntidaoAtual   Training Stress Balance pré-treino (prontidão)
 * @param rampRateAtual       taxa de variação semanal do CTL (pontos/semana)
 * @param metaTssSemanal      TSS semanal alvo para o atleta nesta fase
 * @param mediaVolumeSemanas4 média de volume em km das últimas 4 semanas
 * @param diasConsecutivos    número de dias consecutivos treinando sem descanso
 * @param temLesaoAtiva       indica se o atleta possui lesão ativa em acompanhamento
 * @param fasePeriodizacao    fase atual de periodização (ex: BASE, ESPECIFICO, TAPER, RECOVERY)
 * @param nivelExperiencia    nível de experiência do atleta (INICIANTE, INTERMEDIARIO, AVANCADO, ELITE)
 */
public record TrainingPrescriptionGuardInput(
        List<TreinoPlanejadoResumo> treinosPropostos,
        double ctlAtual,
        double tsbProntidaoAtual,
        double rampRateAtual,
        double metaTssSemanal,
        double mediaVolumeSemanas4,
        Integer diasConsecutivos,
        boolean temLesaoAtiva,
        String fasePeriodizacao,
        String nivelExperiencia
) {}
