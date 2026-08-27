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

        @Test
        @DisplayName("vincular a um planejado pulado reverte o pulo (motivo e carimbo saem)")
        void revertePuloAoVincular() {
            UUID treinoPlanejadoId = UUID.randomUUID();
            TreinoPlanejado planejado = new TreinoPlanejado();
            planejado.setId(treinoPlanejadoId);
            planejado.setAtleta(atleta);
            planejado.setStatusTreino(br.com.menthoros.backend.enums.TreinoExecucaoStatus.PERDIDO);
            planejado.setMotivoPulo(br.com.menthoros.backend.enums.MotivoPulo.SEM_TEMPO);
            planejado.setPuladoEm(java.time.LocalDateTime.of(2026, 8, 27, 7, 0));
            when(treinoPlanejadoRepository.findById(treinoPlanejadoId)).thenReturn(Optional.of(planejado));

            service.linkManually(treinoRealizadoId, treinoPlanejadoId, tenantId, "coach-1");

            org.assertj.core.api.Assertions.assertThat(planejado.getStatusTreino())
                    .isEqualTo(br.com.menthoros.backend.enums.TreinoExecucaoStatus.REALIZADO);
            org.assertj.core.api.Assertions.assertThat(planejado.getMotivoPulo()).isNull();
            org.assertj.core.api.Assertions.assertThat(planejado.getPuladoEm()).isNull();
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
