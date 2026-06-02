package br.com.menthoros.backend.skills.prescription;

/**
 * Resumo de uma sessão de treino planejada para uso nas skills de prescrição.
 *
 * <p>Record sem dependência JPA — criado pelo serviço chamador a partir da entidade
 * {@code TreinoPlanejado} antes de invocar a skill.</p>
 *
 * @param tipoTreino    tipo do treino (ex: INTERVALADO, LONGO, CONTINUO, REGENERATIVO)
 * @param diaSemana     dia da semana em que o treino ocorre (ex: SEGUNDA, TERCA)
 * @param distanciaKm   distância planejada em quilômetros
 * @param duracaoMin    duração planejada em minutos
 * @param tssEstimado   Training Stress Score estimado para a sessão
 */
public record TreinoPlanejadoResumo(
        String tipoTreino,
        String diaSemana,
        double distanciaKm,
        int duracaoMin,
        double tssEstimado
) {}
