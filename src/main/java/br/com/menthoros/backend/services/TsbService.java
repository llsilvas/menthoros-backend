package br.com.menthoros.backend.services;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Serviço de cálculo de TSB (Training Stress Balance).
 *
 * <p>Responsável por calcular CTL, ATL, TSB e Ramp Rate
 * usando médias móveis exponenciais.
 */
public interface TsbService {
    void recalcularHistoricoCompleto(UUID id);

    /**
     * Atualiza TSB para um dia específico baseado nos treinos realizados
     *
     * @param atletaId ID do atleta
     * @param data Data para atualizar
     */
    void atualizarTsbDia(UUID atletaId, LocalDate data);
}
