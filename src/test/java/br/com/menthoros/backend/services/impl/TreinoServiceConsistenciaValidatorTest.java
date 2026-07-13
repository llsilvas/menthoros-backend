package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.TreinoRealizadoInputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.*;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.*;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.SugestaoReclassificacao;
import br.com.menthoros.backend.services.helper.TipoTreinoConsistenciaValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Section 11.5 — TDD: integração do TipoTreinoConsistenciaValidator no TreinoServiceImpl.
 *
 * <p>Verifica que:
 * <ul>
 *   <li>O validator é chamado ao lançar e atualizar treino</li>
 *   <li>A sugestão de reclassificação é logada mas NUNCA altera o tipo automaticamente</li>
 *   <li>Nenhuma exception é lançada quando há inconsistência</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TreinoServiceConsistenciaValidatorTest {

    @Mock private TreinoMapper treinoMapper;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private AtletaRepository atletaRepository;
    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private PlanoSemanalMapper planoSemanalMapper;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private TsbService tsbService;
    @Mock private PlanoMetadadosRepository planoMetaDadosRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TipoTreinoConsistenciaValidator consistenciaValidator;

    @InjectMocks
    private TreinoServiceImpl treinoService;

    private UUID tenantId;
    private UUID atletaId;
    private Assessoria assessoria;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();

        assessoria = new Assessoria();
        assessoria.setId(tenantId);

        atleta = Atleta.builder()
                .id(atletaId)
                .assessoria(assessoria)
                .metricasDiarias(new java.util.ArrayList<>())
                .build();

        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // 11.5a — lancarTreino com tipo inconsistente: nenhuma exception lançada
    // =========================================================================

    @Test
    @DisplayName("11.5a: lancarTreino com tipo inconsistente — nenhuma exception é lançada")
    void lancarTreino_tipoInconsistente_naoLancaException() {
        // ARRANGE
        TreinoRealizado treinoSalvo = buildTreinoRealizado(TipoTreino.REGENERATIVO);
        TreinoRealizadoOutputDto outputDto = buildOutputDto();

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId))
                .thenReturn(Optional.of(atleta));
        when(treinoMapper.toEntity(any(TreinoRealizadoInputDto.class)))
                .thenReturn(treinoSalvo);
        when(treinoRealizadoRepository.save(any()))
                .thenReturn(treinoSalvo);
        when(treinoMapper.toOutputDto(any(TreinoRealizado.class)))
                .thenReturn(outputDto);
        when(consistenciaValidator.validarEstrutura(any()))
                .thenReturn(Optional.of(new SugestaoReclassificacao(
                        TipoTreino.CONTINUO, 0.90,
                        "Etapa com IF=0.88 detectada; IF médio 0.68 sugere CONTINUO")));

        TreinoRealizadoInputDto dto = buildInputDto(atletaId);

        // ACT + ASSERT — nenhuma exception esperada
        assertDoesNotThrow(() -> treinoService.lancarTreino(atletaId, dto),
                "Inconsistência de tipo não deve lançar exception");
    }

    @Test
    @DisplayName("11.5b: lancarTreino com tipo inconsistente — tipo NÃO é alterado automaticamente")
    void lancarTreino_tipoInconsistente_tipoNaoAlterado() {
        // ARRANGE
        TreinoRealizado treinoSalvo = buildTreinoRealizado(TipoTreino.REGENERATIVO);
        TreinoRealizadoOutputDto outputDto = buildOutputDto();

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId))
                .thenReturn(Optional.of(atleta));
        when(treinoMapper.toEntity(any(TreinoRealizadoInputDto.class)))
                .thenReturn(treinoSalvo);
        when(treinoRealizadoRepository.save(any()))
                .thenReturn(treinoSalvo);
        when(treinoMapper.toOutputDto(any(TreinoRealizado.class)))
                .thenReturn(outputDto);
        when(consistenciaValidator.validarEstrutura(any()))
                .thenReturn(Optional.of(new SugestaoReclassificacao(
                        TipoTreino.CONTINUO, 0.90, "Sugestão de reclassificação")));

        TreinoRealizadoInputDto dto = buildInputDto(atletaId);

        // ACT
        treinoService.lancarTreino(atletaId, dto);

        // ASSERT — tipo original REGENERATIVO não deve ter sido alterado
        assertEquals(TipoTreino.REGENERATIVO, treinoSalvo.getTipoTreino(),
                "O tipo declarado pelo coach NÃO deve ser alterado automaticamente");
        // O treino deve ter sido salvo com o tipo original
        verify(treinoRealizadoRepository).save(treinoSalvo);
    }

    @Test
    @DisplayName("11.5c: lancarTreino — validator é chamado após montar o treino")
    void lancarTreino_validatorEChamado() {
        // ARRANGE
        TreinoRealizado treinoSalvo = buildTreinoRealizado(TipoTreino.REGENERATIVO);
        TreinoRealizadoOutputDto outputDto = buildOutputDto();

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId))
                .thenReturn(Optional.of(atleta));
        when(treinoMapper.toEntity(any(TreinoRealizadoInputDto.class)))
                .thenReturn(treinoSalvo);
        when(treinoRealizadoRepository.save(any()))
                .thenReturn(treinoSalvo);
        when(treinoMapper.toOutputDto(any(TreinoRealizado.class)))
                .thenReturn(outputDto);
        when(consistenciaValidator.validarEstrutura(any()))
                .thenReturn(Optional.empty());

        TreinoRealizadoInputDto dto = buildInputDto(atletaId);

        // ACT
        treinoService.lancarTreino(atletaId, dto);

        // ASSERT — validator deve ter sido chamado exatamente uma vez
        verify(consistenciaValidator, times(1)).validarEstrutura(any(TreinoRealizado.class));
    }

    @Test
    @DisplayName("11.5d: updateTreino com tipo inconsistente — nenhuma exception é lançada")
    void updateTreino_tipoInconsistente_naoLancaException() {
        // ARRANGE
        UUID treinoId = UUID.randomUUID();
        TreinoRealizado treinoExistente = buildTreinoRealizado(TipoTreino.REGENERATIVO);
        TreinoRealizadoOutputDto outputDto = buildOutputDto();

        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(treinoExistente));
        when(treinoRealizadoRepository.save(any()))
                .thenReturn(treinoExistente);
        when(treinoMapper.toOutputDto(any(TreinoRealizado.class)))
                .thenReturn(outputDto);
        when(consistenciaValidator.validarEstrutura(any()))
                .thenReturn(Optional.of(new SugestaoReclassificacao(
                        TipoTreino.CONTINUO, 0.85, "Sugestão de reclassificação")));

        TreinoRealizadoInputDto dto = buildInputDto(atletaId);

        // ACT + ASSERT
        assertDoesNotThrow(() -> treinoService.updateTreino(treinoId, dto),
                "Inconsistência de tipo não deve lançar exception no updateTreino");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private TreinoRealizado buildTreinoRealizado(TipoTreino tipo) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setId(UUID.randomUUID());
        treino.setAtleta(atleta);
        treino.setTipoTreino(tipo);
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setDuracaoMin(Duration.ofMinutes(60));
        treino.setTenantId(tenantId);
        treino.setEtapasRealizadas(List.of());
        treino.setStatus(TreinoExecucaoStatus.REALIZADO);
        treino.setFonteDados(FonteDados.MANUAL);
        treino.setFcMedia(120);
        treino.setFcMax(140);
        treino.setPaceMedia(Duration.ofMinutes(5).plusSeconds(30));
        return treino;
    }

    private TreinoRealizadoOutputDto buildOutputDto() {
        // Campos: id, dataTreino, diaSemana, tipoTreino, descricao, zonaAlvo, duracaoMin,
        //         distanciaKm, ritmoAlvo, paceMedia, elevacaoGanhoMetros, elevacaoPerdaMetros,
        //         observacao, fcMedia, fcMax, cadenciaMedia, potenciaMedia, velocidadeMedia,
        //         percepcaoEsforco, tssCalculado, metodoCalculoTss, intensidadeReal,
        //         runningDynamics (V53), decouplingPercentual, decoupling, serieEficiencia,
        //         feedbackAtleta, qualidadeSonoNoiteAnterior, nivelEstresse,
        //         fonteDados, status, externalId, etapasRealizadas, sugestaoReclassificacao
        return new TreinoRealizadoOutputDto(
                UUID.randomUUID(), LocalDate.now(), DiaSemana.SEGUNDA,
                TipoTreino.REGENERATIVO, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null,
                null, // runningDynamics
                null, null, null,
                null, null, null,
                null, null, null, null, null
        );
    }

    private TreinoRealizadoInputDto buildInputDto(UUID atletaId) {
        // Campos: atletaId, planoSemanalId, treinoPlanejadoId, dataTreino, diaSemana, tipoTreino,
        //         descricao, zonaAlvo, duracaoMin, distanciaKm, ritmoAlvo, ritmoMedio,
        //         elevacaoGanhoMetros, elevacaoPerdaMetros, observacao,
        //         fcMedia, fcMax, cadenciaMedia, potenciaMedia, velocidadeMedia, percepcaoEsforco,
        //         feedbackAtleta, qualidadeSonoNoiteAnterior, nivelEstresse,
        //         fonteDados, status, externalId, etapasRealizadas
        return new TreinoRealizadoInputDto(
                atletaId, null, null, LocalDate.now(), DiaSemana.SEGUNDA,
                TipoTreino.REGENERATIVO, null, null, null, null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null,
                null, null, null, null
        );
    }
}
