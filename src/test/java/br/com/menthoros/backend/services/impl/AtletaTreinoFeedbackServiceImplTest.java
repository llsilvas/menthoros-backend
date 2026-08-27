package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.FeedbackTreinoInputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.Sensacao;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AtletaTreinoFeedbackServiceImpl")
class AtletaTreinoFeedbackServiceImplTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;
    @Mock private TreinoMapper treinoMapper;

    private AtletaTreinoFeedbackServiceImpl service;
    private SimpleMeterRegistry meterRegistry;
    private UUID tenantId;
    private UUID atletaId;
    private UUID treinoId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        treinoId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        atleta = Atleta.builder().id(atletaId).build();
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC);
        meterRegistry = new SimpleMeterRegistry();
        service = new AtletaTreinoFeedbackServiceImpl(treinoRealizadoRepository, ingestaoTreinoRealizadoService, treinoMapper, clock, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TreinoRealizado realizado() {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setId(treinoId);
        tr.setAtleta(atleta);
        tr.setTenantId(tenantId);
        return tr;
    }

    @Test
    @DisplayName("grava RPE, sensações e comentário, e carimba feedbackRegistradoEm")
    void gravaFeedback() {
        TreinoRealizado tr = realizado();
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(tr));
        when(treinoRealizadoRepository.save(tr)).thenReturn(tr);
        when(treinoMapper.toOutputDto(tr)).thenReturn(mock(TreinoRealizadoOutputDto.class));

        service.registrarFeedback(atletaId, treinoId,
                new FeedbackTreinoInputDto(6, Set.of(Sensacao.PERNAS_PESADAS), "Difícil no final"));

        assertThat(tr.getPercepcaoEsforco()).isEqualTo(6);
        assertThat(tr.getSensacoes()).containsExactly(Sensacao.PERNAS_PESADAS);
        assertThat(tr.getFeedbackAtleta()).isEqualTo("Difícil no final");
        assertThat(tr.getFeedbackRegistradoEm()).isEqualTo(LocalDateTime.of(2026, 8, 27, 12, 0));
    }

    @Test
    @DisplayName("chama reprocessar após salvar — recalcula TSS quando o RPE chega depois (D9)")
    void chamaReprocessar() {
        TreinoRealizado tr = realizado();
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(tr));
        when(treinoRealizadoRepository.save(tr)).thenReturn(tr);
        when(treinoMapper.toOutputDto(tr)).thenReturn(mock(TreinoRealizadoOutputDto.class));

        service.registrarFeedback(atletaId, treinoId, new FeedbackTreinoInputDto(6, null, null));

        verify(ingestaoTreinoRealizadoService).reprocessar(treinoId, null);
    }

    @Test
    @DisplayName("segundo envio substitui RPE, sensações e comentário (último vence)")
    void segundoEnvioSubstitui() {
        TreinoRealizado tr = realizado();
        tr.setPercepcaoEsforco(5);
        tr.setSensacoes(java.util.Set.of(Sensacao.CALOR));
        tr.setFeedbackAtleta("primeiro");
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(tr));
        when(treinoRealizadoRepository.save(tr)).thenReturn(tr);
        when(treinoMapper.toOutputDto(tr)).thenReturn(mock(TreinoRealizadoOutputDto.class));

        service.registrarFeedback(atletaId, treinoId, new FeedbackTreinoInputDto(7, Set.of(), "segundo"));

        assertThat(tr.getPercepcaoEsforco()).isEqualTo(7);
        assertThat(tr.getSensacoes()).isEmpty();
        assertThat(tr.getFeedbackAtleta()).isEqualTo("segundo");
    }

    @Test
    @DisplayName("realizado de outro atleta ou fora do tenant → 404, nada salvo")
    void isolamento() {
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarFeedback(atletaId, treinoId, new FeedbackTreinoInputDto(5, null, null)))
                .isInstanceOf(DomainNotFoundException.class);
        verify(treinoRealizadoRepository, never()).save(any());
        verify(ingestaoTreinoRealizadoService, never()).reprocessar(any(), any());
    }

    @Test
    @DisplayName("incrementa o contador de feedback (métrica de sucesso da change)")
    void incrementaContador() {
        TreinoRealizado tr = realizado();
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(tr));
        when(treinoRealizadoRepository.save(tr)).thenReturn(tr);
        when(treinoMapper.toOutputDto(tr)).thenReturn(mock(TreinoRealizadoOutputDto.class));

        service.registrarFeedback(atletaId, treinoId, new FeedbackTreinoInputDto(6, null, null));

        assertThat(meterRegistry.get("atleta_treino_feedback_total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("realizado de outro atleta no mesmo tenant → 404, nada salvo")
    void isolamentoPorAtleta() {
        TreinoRealizado deOutroAtleta = realizado();
        deOutroAtleta.setAtleta(Atleta.builder().id(UUID.randomUUID()).build());
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(deOutroAtleta));

        assertThatThrownBy(() -> service.registrarFeedback(atletaId, treinoId, new FeedbackTreinoInputDto(5, null, null)))
                .isInstanceOf(DomainNotFoundException.class);
        verify(treinoRealizadoRepository, never()).save(any());
    }
}
