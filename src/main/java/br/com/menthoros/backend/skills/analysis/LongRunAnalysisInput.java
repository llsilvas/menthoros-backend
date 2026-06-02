package br.com.menthoros.backend.skills.analysis;

import java.util.List;

/**
 * Input para a {@link LongRunAnalysisSkill}.
 *
 * <p>Contém os dados do long run (corrida longa) realizado.
 * As etapas são opcionais: quando a lista estiver vazia ou nula,
 * a skill usa IF global e RPE como fallback para classificar a qualidade aeróbia.</p>
 *
 * @param distanciaKm      distância total percorrida em quilômetros
 * @param duracaoMin       duração total do treino em minutos
 * @param tssRealizado     Training Stress Score realizado
 * @param percepcaoEsforco RPE (Rating of Perceived Exertion) de 1 a 10
 * @param fcMediaGlobal    frequência cardíaca média global do treino (bpm)
 * @param fcLimiar         frequência cardíaca no limiar anaeróbio do atleta (bpm)
 * @param paceMediaGlobal  pace médio global realizado em segundos por quilômetro
 * @param paceAlvo         pace alvo planejado em segundos por quilômetro
 * @param etapas           lista de etapas realizadas; vazia ou null ativa o fallback automático
 */
public record LongRunAnalysisInput(
        double distanciaKm,
        int duracaoMin,
        double tssRealizado,
        int percepcaoEsforco,
        double fcMediaGlobal,
        double fcLimiar,
        double paceMediaGlobal,
        double paceAlvo,
        List<EtapaRealizadaResumo> etapas
) {}
