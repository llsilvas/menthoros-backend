package br.com.menthoros.backend.services;


import br.com.menthoros.backend.dto.input.TreinoManualInputDto;
import br.com.menthoros.backend.dto.input.TreinoRealizadoInputDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.output.ResumoSemanalTreinoDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.TreinoRealizado;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TreinoService {

    TreinoRealizado addTreino(UUID treinoPlanejadoId, TreinoRealizadoInputDto treinoRealizadoInputDto);

    TreinoRealizadoOutputDto updateTreino(UUID id, TreinoRealizadoInputDto treinoRealizadoInputDto);

    void deleteTreino(UUID id);

    TreinoRealizadoOutputDto getTreinoById(UUID id);

    void gravarTreino(UUID atletaId, TreinoPlanejadoLlmDto planoSemanalOutputDto);

    TreinoRealizadoOutputDto lancarTreino(UUID atletaId, @Valid TreinoRealizadoInputDto treinoRealizadoInputDto);

    void marcarTreinoPerdido(UUID treinoPlanejadoId);

    ResumoSemanalTreinoDto getResumoSemanal(UUID atletaId, LocalDate targetDate);

    /**
     * Registra treino executado manualmente pelo próprio atleta.
     * Idempotent: NO. Side Effects: Database insert + TreinoRegistradoEvent + TSB update. Tenant-aware: YES.
     *
     * @param atletaId ID do atleta (resolvido do JWT no controller)
     * @param input    dados do treino manual
     * @return treino salvo como DTO
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException      se atletaId não pertence ao tenant
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException se data anterior a 7 dias
     */
    TreinoRealizadoOutputDto registrarTreinoManualAtleta(UUID atletaId, TreinoManualInputDto input);

    /**
     * Lista treinos realizados recentes do atleta, ordenados por data DESC.
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     *
     * @param atletaId ID do atleta
     * @param dias     janela de consulta (limitado a 30 internamente)
     * @return lista de treinos realizados no período
     */
    List<TreinoRealizadoOutputDto> listarTreinosRecentes(UUID atletaId, int dias);
}
