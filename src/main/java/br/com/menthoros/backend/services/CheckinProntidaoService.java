package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.CheckinProntidaoInputDto;
import br.com.menthoros.backend.dto.output.CheckinProntidaoOutputDto;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface CheckinProntidaoService {

    CheckinProntidaoOutputDto registrarCheckin(UUID atletaId, CheckinProntidaoInputDto dto);

    @Nullable
    CheckinProntidaoOutputDto buscarAtual(UUID atletaId);

    List<CheckinProntidaoOutputDto> buscarHistorico(UUID atletaId, int dias);
}
