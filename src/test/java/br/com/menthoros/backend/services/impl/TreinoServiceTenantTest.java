package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.TreinoRealizadoInputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.*;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.*;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Section 4 — Service TDD tests for tenant-aware method substitution in TreinoServiceImpl.
 *
 * Tests 4.1–4.3 verify that TreinoServiceImpl uses tenant-aware repository methods
 * to prevent cross-tenant data leakage.
 */
@ExtendWith(MockitoExtension.class)
class TreinoServiceTenantTest {

    @Mock private TreinoMapper treinoMapper;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private AtletaRepository atletaRepository;
    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private PlanoSemanalMapper planoSemanalMapper;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;
    @Mock private PlanoMetadadosRepository planoMetaDadosRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private br.com.menthoros.backend.services.plano.ProvaResultadoSyncer provaResultadoSyncer;

    @InjectMocks
    private TreinoServiceImpl treinoService;

    private UUID tenantA;
    private UUID tenantB;
    private UUID atletaId;
    private UUID treinoPlanejadoId;
    private Assessoria assessoriaA;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        treinoPlanejadoId = UUID.randomUUID();

        assessoriaA = new Assessoria();
        assessoriaA.setId(tenantA);

        atleta = Atleta.builder()
                .id(atletaId)
                .assessoria(assessoriaA)
                .metricasDiarias(new java.util.ArrayList<>())
                .build();

        TenantContext.setTenantId(tenantA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // 4.1 — TreinoServiceImpl.addTreino: atletaRepository.findById → findByIdAndTenantId
    // =========================================================================

    @Test
    @DisplayName("4.1: addTreino — lança DomainNotFoundException quando atleta pertence a outro tenant")
    void addTreino_atletaDeOutroTenant_lancaDomainNotFoundException() {
        // ARRANGE — sem externalId: deduplicação não executa. Atleta não encontrado para tenant A.
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantA))
                .thenReturn(Optional.empty());

        TreinoRealizadoInputDto dto = buildInputDto(atletaId);

        // ACT + ASSERT
        assertThrows(DomainNotFoundException.class,
                () -> treinoService.addTreino(null, dto));

        verify(atletaRepository).findByIdAndTenantId(atletaId, tenantA);
        verify(atletaRepository, never()).findById(any());
    }

    @Test
    @DisplayName("4.1: addTreino — carrega atleta apenas do tenant correto via findByIdAndTenantId")
    void addTreino_atletaDoTenantCorreto_buscaComTenantId() {
        // ARRANGE — sem externalId: deduplicação não executa.
        TreinoRealizado treinoSalvo = buildTreinoRealizado(atleta);

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantA))
                .thenReturn(Optional.of(atleta));
        when(planoSemanalRepository.findPlanoSemanalByAtletaIdAndTreinosPlanejadosDataTreino(any(), any()))
                .thenReturn(Optional.empty());
        when(treinoMapper.toEntity(any(TreinoRealizadoInputDto.class)))
                .thenReturn(treinoSalvo);
        when(ingestaoTreinoRealizadoService.registrar(any(), any()))
                .thenReturn(new TreinoDedupHelper.SaveResult(treinoSalvo, true));

        TreinoRealizadoInputDto dto = buildInputDto(atletaId);

        // ACT — deve não lançar exceção de not found
        treinoService.addTreino(null, dto);

        // ASSERT — chamou com tenant-aware
        verify(atletaRepository).findByIdAndTenantId(atletaId, tenantA);
        verify(atletaRepository, never()).findById(any());
    }

    // =========================================================================
    // 4.2 — TreinoServiceImpl.resolveTreinoPlanejado: findById → findByIdAndTenantId
    // =========================================================================

    @Test
    @DisplayName("4.2: addTreino com treinoPlanejadoId — lança DomainNotFoundException quando planejado pertence a outro tenant")
    void addTreino_treinoPlanejadoDeOutroTenant_lancaDomainNotFoundException() {
        // ARRANGE — atleta encontrado, mas treinoPlanejado não encontrado para tenant A
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantA))
                .thenReturn(Optional.of(atleta));
        when(treinoPlanejadoRepository.findByIdAndTenantId(treinoPlanejadoId, tenantA))
                .thenReturn(Optional.empty());

        TreinoRealizadoInputDto dto = buildInputDto(atletaId);

        // ACT + ASSERT
        assertThrows(DomainNotFoundException.class,
                () -> treinoService.addTreino(treinoPlanejadoId, dto));

        verify(treinoPlanejadoRepository).findByIdAndTenantId(treinoPlanejadoId, tenantA);
        verify(treinoPlanejadoRepository, never()).findById(any());
    }

    @Test
    @DisplayName("4.2: addTreino com treinoPlanejadoId — carrega planejado apenas do tenant correto")
    void addTreino_treinoPlanejadoDoTenantCorreto_buscaComTenantId() {
        // ARRANGE — sem externalId: deduplicação não executa.
        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setAtleta(atleta);
        planejado.setTipoTreino(TipoTreino.FACIL);

        TreinoRealizado treinoSalvo = buildTreinoRealizado(atleta);

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantA))
                .thenReturn(Optional.of(atleta));
        when(treinoPlanejadoRepository.findByIdAndTenantId(treinoPlanejadoId, tenantA))
                .thenReturn(Optional.of(planejado));
        when(planoSemanalRepository.findPlanoSemanalByAtletaIdAndTreinosPlanejadosDataTreino(any(), any()))
                .thenReturn(Optional.empty());
        when(treinoMapper.toEntity(any(TreinoRealizadoInputDto.class)))
                .thenReturn(treinoSalvo);
        when(ingestaoTreinoRealizadoService.registrar(any(), any()))
                .thenReturn(new TreinoDedupHelper.SaveResult(treinoSalvo, true));

        TreinoRealizadoInputDto dto = buildInputDto(atletaId);

        // ACT
        treinoService.addTreino(treinoPlanejadoId, dto);

        // ASSERT
        verify(treinoPlanejadoRepository).findByIdAndTenantId(treinoPlanejadoId, tenantA);
        verify(treinoPlanejadoRepository, never()).findById(any());
    }

    // =========================================================================
    // 4.3 — TreinoServiceImpl.buscarTreinoDuplicado: inclui tenantId na busca
    // =========================================================================

    @Test
    @DisplayName("4.3: addTreino — busca de duplicidade usa externalId + atletaId para isolamento por tenant")
    void addTreino_buscaDuplicidade_usaAtletaIdParaIsolamento() {
        // ARRANGE — duplicado NÃO encontrado (tenant isolado corretamente)
        when(treinoRealizadoRepository.findByExternalIdAndAtletaId(
                eq("ext-123"), eq(atletaId)))
                .thenReturn(Optional.empty());

        // Não chamamos o método antigo sem isolamento
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantA))
                .thenReturn(Optional.empty()); // vai falhar depois — queremos apenas testar a deduplicação

        // atletaId, planoSemanalId, treinoPlanejadoId, dataTreino, diaSemana, tipoTreino,
        // descricao, zonaAlvo, duracaoMin, distanciaKm, ritmoAlvo, ritmoMedio,
        // elevacaoGanhoMetros, elevacaoPerdaMetros, observacao,
        // fcMedia, fcMax, cadenciaMedia, potenciaMedia, velocidadeMedia,
        // percepcaoEsforco, feedbackAtleta, qualidadeSonoNoiteAnterior, nivelEstresse,
        // fonteDados, status, externalId, etapasRealizadas
        TreinoRealizadoInputDto dto = new TreinoRealizadoInputDto(
                atletaId, null, null, LocalDate.now(), null, TipoTreino.FACIL,
                null, null, null, null, null, null,
                null, null, null,
                null, null, null, null, null,
                null, null, null, null,
                FonteDados.STRAVA, null, "ext-123", null);

        // ACT — esperamos DomainNotFoundException (atleta não encontrado pelo tenant A)
        // mas o ponto importante é que o método de deduplicação usou atletaId
        assertThrows(DomainNotFoundException.class,
                () -> treinoService.addTreino(null, dto));

        // ASSERT — verifica que a busca de duplicidade usa atletaId (tenant-aware via atleta)
        verify(treinoRealizadoRepository).findByExternalIdAndAtletaId("ext-123", atletaId);
        // Verifica que NÃO usa o método antigo findByFonteDadosAndExternalId (sem isolamento)
        verify(treinoRealizadoRepository, never()).findByFonteDadosAndExternalId(any(), any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private TreinoRealizadoInputDto buildInputDto(UUID atletaId) {
        // atletaId, planoSemanalId, treinoPlanejadoId, dataTreino, diaSemana, tipoTreino,
        // descricao, zonaAlvo, duracaoMin, distanciaKm, ritmoAlvo, ritmoMedio,
        // elevacaoGanhoMetros, elevacaoPerdaMetros, observacao,
        // fcMedia, fcMax, cadenciaMedia, potenciaMedia, velocidadeMedia,
        // percepcaoEsforco, feedbackAtleta, qualidadeSonoNoiteAnterior, nivelEstresse,
        // fonteDados, status, externalId, etapasRealizadas
        return new TreinoRealizadoInputDto(
                atletaId, null, null, LocalDate.now(), null, TipoTreino.FACIL,
                null, null, "60", 10.0, null, null,
                null, null, null,
                null, null, null, null, null,
                null, null, null, null,
                FonteDados.MANUAL, null, null, null);
    }

    private TreinoRealizado buildTreinoRealizado(Atleta atleta) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setAtleta(atleta);
        tr.setTenantId(tenantA);
        tr.setDataTreino(LocalDate.now());
        tr.setTipoTreino(TipoTreino.FACIL);
        tr.setFonteDados(FonteDados.MANUAL);
        tr.setStatus(TreinoExecucaoStatus.REALIZADO);
        return tr;
    }
}
