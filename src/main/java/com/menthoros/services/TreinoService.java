package com.menthoros.services;

import com.menthoros.dto.TreinoRealizadoDto;
import com.menthoros.entity.TreinoRealizado;

public interface TreinoService {

    TreinoRealizado createTreino(TreinoRealizadoDto treinoRealizadoDto);
    TreinoRealizado updateTreino(Long id, TreinoRealizadoDto treinoRealizadoDto);
    void deleteTreino(Long id);
    TreinoRealizadoDto getTreinoById(Long id);
}
