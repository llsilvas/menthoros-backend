package br.com.menthoros.backend.services;


import br.com.menthoros.backend.dto.input.TreinoRealizadoInputDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.output.ResumoSemanalTreinoDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.TreinoRealizado;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.UUID;

public interface TreinoService {

    TreinoRealizado addTreino(UUID treinoPlanejadoId, TreinoRealizadoInputDto treinoRealizadoInputDto);

    TreinoRealizado updateTreino(UUID id, TreinoRealizadoInputDto treinoRealizadoInputDto);

    void deleteTreino(UUID id);

    TreinoRealizadoOutputDto getTreinoById(UUID id);

    void gravarTreino(UUID atletaId, TreinoPlanejadoLlmDto planoSemanalOutputDto);

    TreinoRealizadoOutputDto lancarTreino(UUID atletaId, @Valid TreinoRealizadoInputDto treinoRealizadoInputDto);

    void marcarTreinoPerdido(UUID treinoPlanejadoId);

    ResumoSemanalTreinoDto getResumoSemanal(UUID atletaId, LocalDate targetDate);
}
