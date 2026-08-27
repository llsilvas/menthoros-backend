package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.FeedbackTreinoInputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.AtletaTreinoFeedbackService;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * <p><b>Isolamento:</b> o realizado é buscado por {@code id + tenantId} e depois confirmado como
 * do atleta autenticado — as duas checagens juntas são o gate; nenhuma leitura seguinte confia
 * só na primeira.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtletaTreinoFeedbackServiceImpl implements AtletaTreinoFeedbackService {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;
    private final TreinoMapper treinoMapper;
    private final Clock clock;

    /**
     * Idempotent: YES — último envio vence. Side Effects: UPDATE em tb_treino_realizado (e,
     * via {@code reprocessar}, possível recálculo de tssCalculado e da carga do dia).
     * Tenant-aware: YES.
     */
    @Override
    @Transactional
    public TreinoRealizadoOutputDto registrarFeedback(UUID atletaId, UUID treinoRealizadoId, FeedbackTreinoInputDto input) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        TreinoRealizado treino = treinoRealizadoRepository.findByIdAndTenantId(treinoRealizadoId, tenantId)
                .filter(tr -> tr.getAtleta() != null && atletaId.equals(tr.getAtleta().getId()))
                .orElseThrow(() -> new DomainNotFoundException("Treino realizado não encontrado"));

        treino.setPercepcaoEsforco(input.percepcaoEsforco());
        treino.setSensacoes(input.sensacoes() == null ? null : new java.util.HashSet<>(input.sensacoes()));
        treino.setFeedbackAtleta(input.comentario());
        treino.setFeedbackRegistradoEm(LocalDateTime.now(clock));
        treinoRealizadoRepository.save(treino);

        // O RPE pode ser a única fonte de TSS deste treino (sync sem FC/pace) — reprocessar
        // recalcula tssCalculado e a carga do dia com o valor recém-gravado (D9).
        ingestaoTreinoRealizadoService.reprocessar(treinoRealizadoId, null);

        log.info("Feedback registrado: treinoRealizadoId={}, atletaId={}, rpe={}",
                treinoRealizadoId, atletaId, input.percepcaoEsforco());
        return treinoMapper.toOutputDto(treino);
    }
}
