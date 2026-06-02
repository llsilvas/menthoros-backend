package br.com.menthoros.backend.skills.prescription;

import java.util.List;
import java.util.Map;

/**
 * Payload do resultado da skill de distribuição semanal de sessões de treino.
 *
 * <p>Contém a distribuição final das sessões pelos dias da semana,
 * os movimentos realizados e a indicação se alguma sessão foi reordenada.</p>
 *
 * @param distribuicao mapa de DiaSemana → tipoTreino com a distribuição final
 * @param movimentos   lista descritiva dos movimentos realizados (ex: "LONGO movido de QUARTA para SABADO")
 * @param reordenado   true se alguma sessão foi movida em relação à proposta original
 */
public record WeeklyDistributionPayload(
        Map<String, String> distribuicao,
        List<String> movimentos,
        boolean reordenado
) {}
