package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.dto.output.ProvasProximasResponseDto;

import java.util.List;
import java.util.UUID;

public interface ProvaService {

    ProvaOutputDto criarProva(UUID atletaId, ProvaInputDto dto);

    List<ProvaOutputDto> listarProvas(UUID atletaId);

    ProvaOutputDto buscarProvaPorId(UUID atletaId, UUID provaId);

    ProvaOutputDto atualizarProva(UUID atletaId, UUID provaId, ProvaInputDto dto);

    void deletarProva(UUID atletaId, UUID provaId);

    ProvasProximasResponseDto getProvasProximas();
}
