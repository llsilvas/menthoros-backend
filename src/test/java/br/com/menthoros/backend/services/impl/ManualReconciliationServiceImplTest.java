package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoReconciliacaoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Migração para o seam de ingestão (task 7.4, {@code ingestao-treino-realizado}): os três gestos
 * de reconciliação manual chamam {@code reprocessar(id, null)} após persistir — por completude
 * (D2/D9), mesmo quando o gesto em si não altera {@code tssCalculado}/carga.
 */
@ExtendWith(MockitoExtension.class)
class ManualReconciliationServiceImplTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private TreinoReconciliacaoRepository treinoReconciliacaoRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;

    private ManualReconciliationServiceImpl service;

    private UUID tenantId;
    private UUID treinoRealizadoId;
    private Atleta atleta;
    private TreinoRealizado realizado;

    @BeforeEach
    void setUp() {
        service = new ManualReconciliationServiceImpl(
                treinoRealizadoRepository, treinoReconciliacaoRepository,
                treinoPlanejadoRepository, ingestaoTreinoRealizadoService);

        tenantId = UUID.randomUUID();
        treinoRealizadoId = UUID.randomUUID();

        atleta = new Atleta();
        atleta.setId(UUID.randomUUID());

        realizado = new TreinoRealizado();
        realizado.setId(treinoRealizadoId);
        realizado.setTenantId(tenantId);
        realizado.setAtleta(atleta);
        realizado.setEtapasRealizadas(java.util.List.of());

        when(treinoRealizadoRepository.findById(treinoRealizadoId)).thenReturn(Optional.of(realizado));
        when(treinoRealizadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(treinoRealizadoRepository.findByIdWithEtapas(treinoRealizadoId)).thenReturn(Optional.of(realizado));
    }

    @Nested
    @DisplayName("linkManually")
    class LinkManually {

        @Test
        @DisplayName("reprocessa com dataAnterior nula após vincular")
        void reprocessaAposVincular() {
            UUID treinoPlanejadoId = UUID.randomUUID();
            TreinoPlanejado planejado = new TreinoPlanejado();
            planejado.setId(treinoPlanejadoId);
            planejado.setAtleta(atleta);
            when(treinoPlanejadoRepository.findById(treinoPlanejadoId)).thenReturn(Optional.of(planejado));

            service.linkManually(treinoRealizadoId, treinoPlanejadoId, tenantId, "coach-1");

            verify(ingestaoTreinoRealizadoService).reprocessar(treinoRealizadoId, null);
        }
    }

    @Nested
    @DisplayName("markAsNotPlanned")
    class MarkAsNotPlanned {

        @Test
        @DisplayName("reprocessa com dataAnterior nula após marcar não planejado")
        void reprocessaAposMarcarNaoPlanejado() {
            service.markAsNotPlanned(treinoRealizadoId, tenantId, "coach-1");

            verify(ingestaoTreinoRealizadoService).reprocessar(treinoRealizadoId, null);
        }
    }

    @Nested
    @DisplayName("unlinkManually")
    class UnlinkManually {

        @Test
        @DisplayName("reprocessa com dataAnterior nula após desfazer o vínculo")
        void reprocessaAposDesfazerVinculo() {
            service.unlinkManually(treinoRealizadoId, tenantId, "coach-1");

            verify(ingestaoTreinoRealizadoService).reprocessar(treinoRealizadoId, null);
        }
    }
}
