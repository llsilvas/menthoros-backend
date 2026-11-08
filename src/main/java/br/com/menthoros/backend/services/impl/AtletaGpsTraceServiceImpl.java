package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.fit.GpsTracePayload;
import br.com.menthoros.backend.dto.output.GpsTraceOutputDto;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.projection.GpsTraceView;
import br.com.menthoros.backend.services.AtletaGpsTraceService;
import br.com.menthoros.backend.services.helper.GpsTraceCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Leitura do trajeto GPS persistido pelo pipeline FIT (add-activity-gps-trace, D4). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtletaGpsTraceServiceImpl implements AtletaGpsTraceService {

    private final TreinoRealizadoRepository treinoRealizadoRepository;

    /**
     * <b>Idempotent:</b> YES — leitura pura.
     * <b>Side Effects:</b> NONE.
     * <b>Tenant-aware:</b> YES — tenant e atleta no WHERE da projeção; "não é seu" (outro tenant,
     * outro atleta), inexistente e sem trajeto respondem o mesmo 404, sem revelar que o treino
     * existe.
     */
    @Override
    @Transactional(readOnly = true)
    public GpsTraceOutputDto buscarTrajeto(UUID atletaId, UUID treinoRealizadoId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        GpsTraceView view = treinoRealizadoRepository.findGpsTrace(treinoRealizadoId, atletaId, tenantId)
                .filter(v -> v.getGpsTrace() != null)
                .orElseThrow(() -> new DomainNotFoundException("Trajeto não encontrado para este treino"));

        GpsTracePayload p;
        try {
            p = GpsTraceCodec.fromJson(view.getGpsTrace());
        } catch (IllegalArgumentException e) {
            // Coluna corrompida é problema nosso, não do cliente: registra o id (nunca o conteúdo)
            // e responde como "sem trajeto" em vez de 400/500.
            log.error("gps_trace ilegível: treinoRealizadoId={}", treinoRealizadoId);
            throw new DomainNotFoundException("Trajeto não encontrado para este treino");
        }
        return new GpsTraceOutputDto(p.v(), p.n(), p.polyline(), p.tSeg(), p.fc(), view.getDeviceName());
    }
}
