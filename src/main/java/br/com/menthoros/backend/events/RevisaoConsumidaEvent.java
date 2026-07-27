package br.com.menthoros.backend.events;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Publicado quando a revisão semanal entra como insumo na geração de um plano — proxy de adoção
 * da métrica de sucesso da change.
 *
 * <p>Carrega {@code revisaoId} e {@code planoId} de propósito: sem eles o evento registraria que
 * "houve consumo", mas não *qual* revisão foi consumida por *qual* plano — que é o vínculo de que
 * a heurística de desfecho (D9) depende.
 *
 * @param tenantId      assessoria dona do plano
 * @param atletaId      atleta do plano gerado
 * @param semanaInicio  início da semana do plano que consumiu a revisão
 * @param revisaoId     revisão consumida
 * @param planoId       plano gerado (nulo antes da primeira persistência do plano)
 */
public record RevisaoConsumidaEvent(
        UUID tenantId,
        UUID atletaId,
        LocalDate semanaInicio,
        UUID revisaoId,
        UUID planoId
) {
}
