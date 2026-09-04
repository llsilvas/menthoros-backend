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

    /** Remoção física — restrita a ADMIN. */
    void deletarProva(UUID atletaId, UUID provaId);

    /** Cancelamento (soft): a prova sai das listagens e do planejamento, mas é preservada. */
    void cancelarProva(UUID atletaId, UUID provaId);

    /** DELETE do recurso: ADMIN remove fisicamente; atleta e treinador cancelam. */
    void removerProva(UUID atletaId, UUID provaId);

    /** Coach registra ciência da última mudança do atleta na prova (idempotente). */
    ProvaOutputDto marcarCiente(UUID atletaId, UUID provaId);

    /** Provas do atleta pendentes de ciência do coach: futuras ou canceladas com a flag zerada. */
    List<ProvaOutputDto> listarPendentesRevisao(UUID atletaId);

    ProvasProximasResponseDto getProvasProximas();
}
