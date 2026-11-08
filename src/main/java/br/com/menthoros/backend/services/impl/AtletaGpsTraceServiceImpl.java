package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.fit.GpsTracePayload;
import br.com.menthoros.backend.dto.output.GpsTraceOutputDto;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.AtletaGpsTraceService;
import br.com.menthoros.backend.services.helper.GpsTraceCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AtletaGpsTraceServiceImpl implements AtletaGpsTraceService {

    private final TreinoRealizadoRepository treinoRealizadoRepository;

    /**
     * <b>Idempotent:</b> YES — leitura pura.
     * <b>Side Effects:</b> NONE.
     * <b>Tenant-aware:</b> YES — busca por tenant e filtra pelo atleta; os três casos de "não é
     * seu" (outro tenant, outro atleta, sem trajeto) respondem o mesmo 404, para não revelar a
     * existência do treino.
     */
    @Override
    @Transactional(readOnly = true)
    public GpsTraceOutputDto buscarTrajeto(UUID atletaId, UUID treinoRealizadoId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        TreinoRealizado treino = treinoRealizadoRepository.findByIdAndTenantId(treinoRealizadoId, tenantId)
                .filter(tr -> tr.getAtleta() != null && atletaId.equals(tr.getAtleta().getId()))
                .filter(tr -> tr.getGpsTrace() != null)
                .orElseThrow(() -> new DomainNotFoundException("Trajeto não encontrado para este treino"));

        GpsTracePayload p = GpsTraceCodec.fromJson(treino.getGpsTrace());
        return new GpsTraceOutputDto(p.v(), p.n(), p.polyline(), p.tSeg(), p.fc(), treino.getDeviceName());
    }
}
