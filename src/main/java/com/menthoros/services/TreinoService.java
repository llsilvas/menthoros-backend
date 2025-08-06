package com.menthoros.services;


import com.menthoros.dto.input.TreinoRealizadoInputDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.TreinoRealizado;

import java.util.UUID;

public interface TreinoService {

    TreinoRealizado addTreino(TreinoRealizadoInputDto treinoRealizadoInputDto);

    TreinoRealizado updateTreino(UUID id, TreinoRealizadoInputDto treinoRealizadoInputDto);

    void deleteTreino(UUID id);

    TreinoRealizadoOutputDto getTreinoById(UUID id);

    void gravarTreino(PlanoSemanalOutputDto planoSemanalOutputDto);
}
