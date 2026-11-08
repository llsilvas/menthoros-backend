package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.GpsTraceOutputDto;

import java.util.UUID;

public interface AtletaGpsTraceService {

    /**
     * Trajeto GPS de um treino realizado do atleta autenticado (add-activity-gps-trace, D4).
     *
     * <p><b>Idempotent:</b> YES — leitura pura. <b>Side Effects:</b> NONE.
     * <b>Tenant-aware:</b> YES — tenant e atleta no WHERE; um único 404 para os casos "não é seu".
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException realizado inexistente, de
     *         outro atleta, de outro tenant ou sem trajeto ({@code 404})
     */
    GpsTraceOutputDto buscarTrajeto(UUID atletaId, UUID treinoRealizadoId);
}
