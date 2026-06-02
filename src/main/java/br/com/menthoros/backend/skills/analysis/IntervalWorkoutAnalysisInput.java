package br.com.menthoros.backend.skills.analysis;

import java.util.List;

/**
 * Input para a {@link IntervalWorkoutAnalysisSkill}.
 *
 * <p>Contém os dados do treino intervalado realizado.
 * As etapas são opcionais: quando a lista estiver vazia ou nula,
 * a skill usa {@code fcMediaGlobal / fcLimiar} como Intensity Factor (IF) global (fallback).</p>
 *
 * @param tipoTreino       tipo do treino realizado (ex: "INTERVALADO")
 * @param distanciaKm      distância total percorrida em quilômetros
 * @param duracaoMin       duração total do treino em minutos
 * @param tssRealizado     Training Stress Score realizado
 * @param percepcaoEsforco RPE (Rating of Perceived Exertion) de 1 a 10
 * @param fcMediaGlobal    frequência cardíaca média global do treino (bpm)
 * @param fcLimiar         frequência cardíaca no limiar anaeróbio do atleta (bpm)
 * @param etapas           lista de etapas realizadas; vazia ou null ativa o fallback automático
 */
public record IntervalWorkoutAnalysisInput(
        String tipoTreino,
        double distanciaKm,
        int duracaoMin,
        double tssRealizado,
        int percepcaoEsforco,
        double fcMediaGlobal,
        double fcLimiar,
        List<EtapaRealizadaResumo> etapas
) {}
