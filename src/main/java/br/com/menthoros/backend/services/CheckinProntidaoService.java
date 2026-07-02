package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.CheckinProntidaoInputDto;
import br.com.menthoros.backend.dto.output.CheckinProntidaoOutputDto;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface CheckinProntidaoService {

    CheckinProntidaoOutputDto registrarCheckin(UUID atletaId, CheckinProntidaoInputDto dto);

    /**
     * @param chamadorEhAdmin quando {@code false}, exige que {@code atletaId} seja o atleta
     *                        autenticado (bloqueia acesso a checkin de outro atleta do tenant)
     */
    @Nullable
    CheckinProntidaoOutputDto buscarAtual(UUID atletaId, boolean chamadorEhAdmin);

    /**
     * @param chamadorEhAdmin quando {@code false}, exige que {@code atletaId} seja o atleta
     *                        autenticado (bloqueia acesso a checkin de outro atleta do tenant)
     */
    List<CheckinProntidaoOutputDto> buscarHistorico(UUID atletaId, int dias, boolean chamadorEhAdmin);
}
