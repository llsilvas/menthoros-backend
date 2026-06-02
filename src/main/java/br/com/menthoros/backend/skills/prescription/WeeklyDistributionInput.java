package br.com.menthoros.backend.skills.prescription;

import java.util.List;

/**
 * Input da skill de distribuição semanal de sessões de treino.
 *
 * <p>Contém as sessões a distribuir e as preferências e disponibilidade do atleta
 * para que a skill organize a semana sem criar conflitos de recuperação.</p>
 *
 * @param sessoes           sessões de treino a distribuir pelos dias disponíveis
 * @param diasDisponiveis   dias da semana disponíveis para treino (como String de DiaSemana)
 * @param diaPreferidoLongo dia preferido do atleta para o treino longo (nullable)
 * @param nivelExperiencia  nível de experiência do atleta (INICIANTE, INTERMEDIARIO, AVANCADO, ELITE)
 */
public record WeeklyDistributionInput(
        List<TreinoPlanejadoResumo> sessoes,
        List<String> diasDisponiveis,
        String diaPreferidoLongo,
        String nivelExperiencia
) {}
