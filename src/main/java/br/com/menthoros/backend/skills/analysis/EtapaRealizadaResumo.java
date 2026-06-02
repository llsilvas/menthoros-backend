package br.com.menthoros.backend.skills.analysis;

/**
 * Resumo de uma etapa realizada de um treino.
 *
 * <p>Record sem dependências JPA — usado como input para as skills de análise
 * de treino ({@link IntervalWorkoutAnalysisSkill}, {@link LongRunAnalysisSkill}).
 * O serviço chamador é responsável por mapear entidades JPA para este tipo antes
 * de invocar as skills.</p>
 *
 * @param tipoEtapa   tipo da etapa: AQUECIMENTO, PRINCIPAL, RECUPERACAO, ENCERRAMENTO
 * @param duracaoMin  duração da etapa em minutos
 * @param distanciaKm distância percorrida na etapa em quilômetros
 * @param fcMedia     frequência cardíaca média da etapa (bpm)
 * @param paceMedia   pace médio da etapa em segundos por quilômetro
 */
public record EtapaRealizadaResumo(
        String tipoEtapa,
        int duracaoMin,
        double distanciaKm,
        double fcMedia,
        double paceMedia
) {}
