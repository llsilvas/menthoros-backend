package com.menthoros.services;

import com.menthoros.dto.input.ProvaInputDto;
import com.menthoros.dto.output.ProvaOutputDto;

import java.util.List;
import java.util.UUID;

public interface ProvaService {

    ProvaOutputDto criarProva(UUID atletaId, ProvaInputDto dto);

    List<ProvaOutputDto> listarProvas(UUID atletaId);

    ProvaOutputDto buscarProvaPorId(UUID atletaId, UUID provaId);

    ProvaOutputDto atualizarProva(UUID atletaId, UUID provaId, ProvaInputDto dto);

    void deletarProva(UUID atletaId, UUID provaId);
}
