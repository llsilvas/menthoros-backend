package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.FoundersSlotsOutputDto;

/** Vagas da turma fundadora, calculadas a partir dos convites emitidos. */
public interface FoundersSlotsService {

    FoundersSlotsOutputDto obterVagas();
}
