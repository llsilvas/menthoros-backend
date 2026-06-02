package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.DadosPlanoDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.output.MetricasSemanaisMedias;
import br.com.menthoros.backend.dto.output.PadroesTreino;
import br.com.menthoros.backend.dto.output.ResultadoAnalise;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.*;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.mapper.AtletaMapper;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.EmbeddingService;
import br.com.menthoros.backend.services.IaService;
import br.com.menthoros.backend.services.helper.RedistribuicaoTreinoHelper;
import br.com.menthoros.backend.services.helper.RegraGeracaoTreino;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.multitenancy.TenantContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * This class tests the PlanoServiceImpl class using JUnit 5 and Mockito.
 * <p>
 * The @ExtendWith(MockitoExtension.class) annotation is used to enable
 * Mockito's annotations (@Mock, @InjectMocks, etc.) in JUnit 5 tests.
 */
@ExtendWith(MockitoExtension.class)
class PlanoServiceImplTest {

    @Mock
    private IaService iaService;
    @Mock
    private AtletaRepository atletaRepository;
    @Mock
    private AtletaMapper atletaMapper;
    @Mock
    private TreinoMapper treinoMapper;
    @Mock
    private PlanoSemanalMapper planoSemanalMapper;
    @Mock
    private PlanoMetadadosRepository planoMetadadosRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private PlanoSemanalRepository planoSemanalRepository;
    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock
    private RedistribuicaoTreinoHelper redistribuicaoHelper;
    @Mock
    private RegraGeracaoTreino regraGeracaoTreino;
    @Mock
    private br.com.menthoros.backend.services.PlanoMetadadosService planoMetadadosService;
    @Mock
    private MetricasAlertaService metricasAlertaService;
    @Mock
    private MetricasAgregadasServiceImpl metricasAgregadasService;

    @InjectMocks
    private PlanoServiceImpl planoService;

    private UUID tenantId;

    @BeforeEach
    void setUpTenant() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDownTenant() {
        TenantContext.clear();
    }

    private void mockMetricasAgregadasEAlertas(PlanoMetaDados metaDados) {
        when(metricasAgregadasService.calcularMetricasSemanais(any(), anyInt()))
                .thenReturn(new MetricasSemanaisMedias(BigDecimal.valueOf(30.0), 300, 4.0));
        when(metricasAgregadasService.calcularPadroesTreino(any()))
                .thenReturn(new PadroesTreino(0, 0));

        when(metricasAlertaService.analisarMetricas(any()))
                .thenReturn(new ResultadoAnalise(
                        "NORMAL",
                        "Continuar treinamento normalmente, respeitando os princípios de progressão.",
                        null,
                        false, false, false, false,
                        Collections.emptyList()
                ));
    }

    @Test
    @DisplayName("Deve gerar plano com atleta válido e modo de geração válido")
    void deveGerarPlanoComAtletaValidoEModoGeracaoValido() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.PROXIMA_SEMANA;

        Atleta atleta = criarAtletaMock(atletaId);
        PlanoMetaDados metaDados = criarPlanoMetaDadosMock();
        DadosPlanoDto dadosPlano = new DadosPlanoDto(atleta, LocalDate.now(), null, Collections.emptyList(), metaDados);

        PlanoSemanalLlmDto planoDto = criarPlanoSemanalLlmDto();
        List<TreinoPlanejadoLlmDto> treinos = criarTreinosCompletos();
        PlanoSemanal planoSalvo = criarPlanoSemanalMock();
        TreinoPlanejado treinoPlanejado = criarTreinoPlanejadoMock();

        mockMetricasAgregadasEAlertas(metaDados);

        // Mock dependencies
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Collections.emptyList());
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any(), eq(modoGeracao))).thenReturn(planoDto);
        when(planoMetadadosRepository.findByIdAndTenantId(any(), any())).thenReturn(Optional.of(metaDados));

        when(planoSemanalMapper.toEntity(planoDto)).thenReturn(planoSalvo);
        when(treinoMapper.toEntity(any(TreinoPlanejadoLlmDto.class))).thenReturn(treinoPlanejado);
        when(planoSemanalRepository.save(any(PlanoSemanal.class))).thenReturn(planoSalvo);
        when(planoMetadadosRepository.save(any(PlanoMetaDados.class))).thenReturn(metaDados);

        try (MockedStatic<Hibernate> hibernateMock = mockStatic(Hibernate.class)) {
            hibernateMock.when(() -> Hibernate.initialize(any())).thenAnswer(invocation -> null);

            // When
            PlanoSemanal resultado = planoService.gerarPlanoTreino(atletaId, modoGeracao);

            // Then
            assertNotNull(resultado);
            assertEquals(planoSalvo, resultado);

            verify(atletaRepository).findByIdAndTenantId(atletaId, tenantId);
            verify(iaService).geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any(), eq(modoGeracao));
            verify(planoSemanalRepository).save(any(PlanoSemanal.class));
            verify(planoMetadadosRepository, times(1)).save(any(PlanoMetaDados.class));
        }
    }

    @Test
    @DisplayName("Deve lançar exceção quando LLM retorna plano nulo")
    void deveLancarExcecaoQuandoLlmRetornaPlanoNulo() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.PROXIMA_SEMANA;

        Atleta atleta = criarAtletaMock(atletaId);
        PlanoMetaDados metaDados = criarPlanoMetaDadosMock();

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Collections.emptyList());
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any(), eq(modoGeracao))).thenReturn(null);

        try (MockedStatic<Hibernate> hibernateMock = mockStatic(Hibernate.class)) {
            hibernateMock.when(() -> Hibernate.initialize(any())).thenAnswer(invocation -> null);

            // When & Then
            assertThrows(LLMException.class, () ->
                    planoService.gerarPlanoTreino(atletaId, modoGeracao));

            verify(iaService).geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any(), eq(modoGeracao));
            verify(redistribuicaoHelper, never()).redistribuirTreinos(any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("Deve lançar exceção quando não há treinos redistribuídos após redistribuição")
    void deveLancarExcecaoQuandoNaoHaTreinosRedistribuidosAposRedistribuicao() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.SEMANA_ATUAL;

        Atleta atleta = criarAtletaMock(atletaId);
        PlanoMetaDados metaDados = criarPlanoMetaDadosMock();
        PlanoSemanalLlmDto planoDto = criarPlanoSemanalLlmDto();

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Collections.emptyList());
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any(), eq(modoGeracao))).thenReturn(planoDto);
        when(redistribuicaoHelper.redistribuirTreinos(any(), any(), any(), any(), any(), eq(modoGeracao), any()))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<Hibernate> hibernateMock = mockStatic(Hibernate.class)) {
            hibernateMock.when(() -> Hibernate.initialize(any())).thenAnswer(invocation -> null);

            // When & Then
            DomainRuleViolationException exception = assertThrows(DomainRuleViolationException.class, () ->
                    planoService.gerarPlanoTreino(atletaId, modoGeracao));

            assertTrue(exception.getMessage().contains("Não foi possível gerar treinos"));

            verify(iaService).geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any(), eq(modoGeracao));
            verify(redistribuicaoHelper).redistribuirTreinos(any(), any(), any(), any(), any(), eq(modoGeracao), any());
        }
    }

    @Test
    @DisplayName("Deve lançar exceção com ID de atleta inválido")
    void deveLancarExcecaoComIdAtletaInvalido() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.PROXIMA_SEMANA;

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () ->
                planoService.gerarPlanoTreino(atletaId, modoGeracao));

        verify(atletaRepository).findByIdAndTenantId(atletaId, tenantId);
        verify(iaService, never()).geraPlanoSemanalAvancado(any(), any(), any(), eq(modoGeracao));
    }

    @Test
    @DisplayName("Deve lançar exceção quando LLM lança exceção durante geração")
    void deveLancarExcecaoQuandoLlmLancaExcecaoDuranteGeracao() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.PROXIMA_SEMANA;

        Atleta atleta = criarAtletaMock(atletaId);
        PlanoMetaDados metaDados = criarPlanoMetaDadosMock();

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Collections.emptyList());
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any(), eq(modoGeracao)))
                .thenThrow(new LLMException("Erro na IA"));

        try (MockedStatic<Hibernate> hibernateMock = mockStatic(Hibernate.class)) {
            hibernateMock.when(() -> Hibernate.initialize(any())).thenAnswer(invocation -> null);

            // When & Then - should throw LLMException when LLM fails
            LLMException exception = assertThrows(LLMException.class, () ->
                    planoService.gerarPlanoTreino(atletaId, modoGeracao));

            assertEquals("Erro na IA", exception.getMessage());

            verify(iaService).geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any(), eq(modoGeracao));
            verify(redistribuicaoHelper, never()).redistribuirTreinos(any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("Deve funcionar com diferentes modos de geração")
    void deveFuncionarComDiferentesModosGeracao() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.SEMANA_ATUAL;

        Atleta atleta = criarAtletaMock(atletaId);
        PlanoMetaDados metaDados = criarPlanoMetaDadosMock();
        PlanoSemanalLlmDto planoDto = criarPlanoSemanalLlmDto();
        List<TreinoPlanejadoLlmDto> treinosRedistribuidos = criarTreinosRedistribuidosMock();
        PlanoSemanal planoSalvo = criarPlanoSemanalMock();
        TreinoPlanejado treinoPlanejado = criarTreinoPlanejadoMock();

        mockMetricasAgregadasEAlertas(metaDados);

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Collections.emptyList());
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any(), eq(modoGeracao))).thenReturn(planoDto);
        when(redistribuicaoHelper.redistribuirTreinos(any(), any(), any(), any(), any(), eq(modoGeracao), any()))
                .thenReturn(treinosRedistribuidos);
        when(planoMetadadosRepository.findByIdAndTenantId(any(), any())).thenReturn(Optional.of(metaDados));

        when(planoSemanalMapper.toEntity(planoDto)).thenReturn(planoSalvo);
        when(treinoMapper.toEntity(any(TreinoPlanejadoLlmDto.class))).thenReturn(treinoPlanejado);
        when(planoSemanalRepository.save(any(PlanoSemanal.class))).thenReturn(planoSalvo);
        when(planoMetadadosRepository.save(any(PlanoMetaDados.class))).thenReturn(metaDados);

        try (MockedStatic<Hibernate> hibernateMock = mockStatic(Hibernate.class)) {
            hibernateMock.when(() -> Hibernate.initialize(any())).thenAnswer(invocation -> null);

            // When
            PlanoSemanal resultado = planoService.gerarPlanoTreino(atletaId, modoGeracao);

            // Then
            assertNotNull(resultado);
            assertEquals(planoSalvo, resultado);

            verify(redistribuicaoHelper).redistribuirTreinos(any(), any(), any(), any(), any(), eq(ModoGeracaoPlano.SEMANA_ATUAL), any());
        }
    }

    @Test
    @DisplayName("Deve priorizar LONGO com base no histórico recente do atleta")
    void deveUsarHistoricoParaDefinirDiaDoLongo() {
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.SEMANA_ATUAL;

        Atleta atleta = criarAtletaMock(atletaId);
        atleta.setDiaPreferidoLongo(DiaSemana.SABADO);
        atleta.setDiasDisponiveis(List.of(DiaSemana.QUARTA, DiaSemana.DOMINGO));

        PlanoMetaDados metaDados = criarPlanoMetaDadosMock();
        PlanoSemanalLlmDto planoDto = criarPlanoSemanalLlmDto();
        List<TreinoPlanejadoLlmDto> treinosRedistribuidos = criarTreinosRedistribuidosMock();
        PlanoSemanal planoSalvo = criarPlanoSemanalMock();
        TreinoPlanejado treinoPlanejado = criarTreinoPlanejadoMock();

        TreinoRealizado longo1 = new TreinoRealizado();
        longo1.setDataTreino(LocalDate.of(2026, 4, 13));
        longo1.setDiaSemana(DiaSemana.DOMINGO);
        longo1.setTipoTreino(TipoTreino.LONGO);

        TreinoRealizado longo2 = new TreinoRealizado();
        longo2.setDataTreino(LocalDate.of(2026, 4, 6));
        longo2.setDiaSemana(DiaSemana.DOMINGO);
        longo2.setTipoTreino(TipoTreino.LONGO);

        mockMetricasAgregadasEAlertas(metaDados);

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(List.of(longo1, longo2));
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(treinoMapper.toOutputDto(longo1)).thenReturn(treinoRealizadoOutput(longo1));
        when(treinoMapper.toOutputDto(longo2)).thenReturn(treinoRealizadoOutput(longo2));
        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any(), eq(modoGeracao))).thenReturn(planoDto);
        when(redistribuicaoHelper.redistribuirTreinos(any(), any(), any(), any(), any(), eq(modoGeracao), eq(DiaSemana.DOMINGO)))
                .thenReturn(treinosRedistribuidos);
        when(planoMetadadosRepository.findByIdAndTenantId(any(), any())).thenReturn(Optional.of(metaDados));

        when(planoSemanalMapper.toEntity(planoDto)).thenReturn(planoSalvo);
        when(treinoMapper.toEntity(any(TreinoPlanejadoLlmDto.class))).thenReturn(treinoPlanejado);
        when(planoSemanalRepository.save(any(PlanoSemanal.class))).thenReturn(planoSalvo);
        when(planoMetadadosRepository.save(any(PlanoMetaDados.class))).thenReturn(metaDados);

        try (MockedStatic<Hibernate> hibernateMock = mockStatic(Hibernate.class)) {
            hibernateMock.when(() -> Hibernate.initialize(any())).thenAnswer(invocation -> null);

            PlanoSemanal resultado = planoService.gerarPlanoTreino(atletaId, modoGeracao);

            assertNotNull(resultado);
            verify(redistribuicaoHelper).redistribuirTreinos(any(), any(), any(), any(), any(), eq(modoGeracao), eq(DiaSemana.DOMINGO));
        }
    }

    @Test
    @DisplayName("Deve lançar exceção quando lista de treinos da LLM está vazia")
    void deveLancarExcecaoQuandoListaTreinosLlmEstaVazia() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.PROXIMA_SEMANA;

        Atleta atleta = criarAtletaMock(atletaId);
        PlanoMetaDados metaDados = criarPlanoMetaDadosMock();
        PlanoSemanalLlmDto planoDto = new PlanoSemanalLlmDto(
                22, 22, 50.0, 60.0, PlanoStatus.PLANEJADO.getValue(), "Teste Plano", Collections.emptyList()
        );

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Collections.emptyList());
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any(), eq(modoGeracao))).thenReturn(planoDto);

        try (MockedStatic<Hibernate> hibernateMock = mockStatic(Hibernate.class)) {
            hibernateMock.when(() -> Hibernate.initialize(any())).thenAnswer(invocation -> null);

            // When & Then
            assertThrows(LLMException.class, () ->
                    planoService.gerarPlanoTreino(atletaId, modoGeracao));

            verify(iaService).geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any(), eq(modoGeracao));
            verify(redistribuicaoHelper, never()).redistribuirTreinos(any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("Deve lançar exceção quando atleta não tem dias disponíveis")
    void deveLancarExcecaoQuandoAtletaNaoTemDiasDisponiveis() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.SEMANA_ATUAL;

        Atleta atleta = criarAtletaMock(atletaId);
        atleta.setDiasDisponiveis(Collections.emptyList()); // Sem dias disponíveis

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));

        // When & Then
        DomainRuleViolationException exception = assertThrows(DomainRuleViolationException.class, () ->
                planoService.gerarPlanoTreino(atletaId, modoGeracao));

        // Verifica que a mensagem de erro é sobre dias disponíveis
        assertTrue(exception.getMessage().contains("sem dias disponíveis"));

        // Verifica que a validação falhou antes de chamar serviços de IA ou redistribuição
        verify(atletaRepository).findByIdAndTenantId(atletaId, tenantId);
        verify(iaService, never()).geraPlanoSemanalAvancado(any(), any(), any(), eq(modoGeracao));
        verify(redistribuicaoHelper, never()).redistribuirTreinos(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando atleta está inativo")
    void deveLancarExcecaoQuandoAtletaEstaInativo() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.PROXIMA_SEMANA;

        Atleta atleta = criarAtletaMock(atletaId);
        atleta.setAtivo(AtletaStatus.INATIVO); // Atleta inativo

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));

        // When & Then
        DomainRuleViolationException exception = assertThrows(DomainRuleViolationException.class, () ->
                planoService.gerarPlanoTreino(atletaId, modoGeracao));

        // Verifica que a mensagem de erro é sobre atleta inativo
        assertTrue(exception.getMessage().contains("atleta inativo"));

        // Verifica que a validação falhou antes de chamar serviços de IA
        verify(atletaRepository).findByIdAndTenantId(atletaId, tenantId);
        verify(iaService, never()).geraPlanoSemanalAvancado(any(), any(), any(), eq(modoGeracao));
    }

    @Test
    @DisplayName("Deve lançar exceção quando atleta não tem objetivo definido")
    void deveLancarExcecaoQuandoAtletaNaoTemObjetivo() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.PROXIMA_SEMANA;

        Atleta atleta = criarAtletaMock(atletaId);
        atleta.setObjetivo(null); // Sem objetivo

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));

        // When & Then
        DomainRuleViolationException exception = assertThrows(DomainRuleViolationException.class, () ->
                planoService.gerarPlanoTreino(atletaId, modoGeracao));

        // Verifica que a mensagem de erro é sobre objetivo
        assertTrue(exception.getMessage().contains("sem objetivo definido"));

        // Verifica que a validação falhou antes de chamar serviços de IA
        verify(atletaRepository).findByIdAndTenantId(atletaId, tenantId);
        verify(iaService, never()).geraPlanoSemanalAvancado(any(), any(), any(), eq(modoGeracao));
    }

    @Test
    @DisplayName("Deve lançar exceção quando atleta não tem nível de experiência definido")
    void deveLancarExcecaoQuandoAtletaNaoTemNivelExperiencia() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.PROXIMA_SEMANA;

        Atleta atleta = criarAtletaMock(atletaId);
        atleta.setNivelExperiencia(null); // Sem nível de experiência

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));

        // When & Then
        DomainRuleViolationException exception = assertThrows(DomainRuleViolationException.class, () ->
                planoService.gerarPlanoTreino(atletaId, modoGeracao));

        // Verifica que a mensagem de erro é sobre nível de experiência
        assertTrue(exception.getMessage().contains("sem nível de experiência"));

        // Verifica que a validação falhou antes de chamar serviços de IA
        verify(atletaRepository).findByIdAndTenantId(atletaId, tenantId);
        verify(iaService, never()).geraPlanoSemanalAvancado(any(), any(), any(), eq(modoGeracao));
    }

    // Helper methods to create mock objects
    private Atleta criarAtletaMock(UUID atletaId) {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);

        Atleta atleta = new Atleta();
        atleta.setId(atletaId);
        atleta.setNome("João Silva");
        atleta.setEmail("joao@teste.com");
        atleta.setDiasDisponiveis(List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA));
        atleta.setProvas(new ArrayList<>());
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setObjetivo("Teste objetivo");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAssessoria(assessoria);
        return atleta;
    }

    private PlanoMetaDados criarPlanoMetaDadosMock() {
        return PlanoMetaDados.builder()
                .id(UUID.randomUUID())
                .dataCriacao(LocalDateTime.now())
                .volumeSemanalMedio(BigDecimal.valueOf(50.0))
                .build();
    }

    private PlanoSemanalLlmDto criarPlanoSemanalLlmDto() {
        List<TreinoPlanejadoLlmDto> treinos = List.of(
                new TreinoPlanejadoLlmDto("SEGUNDA", "CONTINUO", "140-160% FCmáx", 100, 1.0, 7,
                        "Treino contínuo", "60", 10.0, "5:00-5:30/km", null),
                new TreinoPlanejadoLlmDto("TERCA", "INTERVALADO", "170-180% FCmáx", 120, 1.2, 8,
                        "Treino intervalado", "45", 12.0, "4:30-5:00/km", null),
                new TreinoPlanejadoLlmDto("QUINTA", "CONTINUO", "170-180% FCmáx", 120, 1.2, 8,
                        "Treino intervalado", "45", 12.0, "4:30-5:00/km", null),
                new TreinoPlanejadoLlmDto("SABADO", "LONGO", "170-180% FCmáx", 120, 1.2, 8,
                        "Treino intervalado", "45", 18.0, "4:30-5:00/km", null)
        );

        return new PlanoSemanalLlmDto(
                22, 22, 50.0, 60.0, PlanoStatus.PLANEJADO.getValue(), "Teste Plano", treinos
        );
    }

    private List<TreinoPlanejadoLlmDto> criarTreinosCompletos() {
        return List.of(
                new TreinoPlanejadoLlmDto("SEGUNDA", "CONTINUO", "140-160% FCmáx", 100, 1.0, 7,
                        "Treino contínuo", "60", 10.0, "5:00-5:30/km", null),
                new TreinoPlanejadoLlmDto("TERCA", "INTERVALADO", "170-180% FCmáx", 120, 1.2, 8,
                        "Treino intervalado", "45", 12.0, "4:30-5:00/km", null),
                new TreinoPlanejadoLlmDto("QUINTA", "CONTINUO", "170-180% FCmáx", 120, 1.2, 8,
                        "Treino intervalado", "45", 12.0, "4:30-5:00/km", null),
                new TreinoPlanejadoLlmDto("SABADO", "LONGO", "170-180% FCmáx", 120, 1.2, 8,
                        "Treino intervalado", "45", 18.0, "4:30-5:00/km", null)
        );
    }


    private List<TreinoPlanejadoLlmDto> criarTreinosRedistribuidosMock() {
        return List.of(
                new TreinoPlanejadoLlmDto("SEGUNDA", "CONTINUO", "140-160% FCmáx", 100, 1.0, 7,
                        "Treino contínuo", "60", 10.0, "5:00-5:30/km", null),
                new TreinoPlanejadoLlmDto("QUARTA", "INTERVALADO", "170-180% FCmáx", 120, 1.2, 8,
                        "Treino intervalado", "45", 12.0, "4:30-5:00/km", null)
        );
    }

    private PlanoSemanal criarPlanoSemanalMock() {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setId(UUID.randomUUID());
        plano.setSemanaInicio(LocalDate.now().plusDays(1));
        plano.setSemanaFim(LocalDate.now().plusDays(7));
        plano.setStatus(PlanoStatus.ATIVO);
        plano.setTreinosPlanejados(new ArrayList<>());
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(50.0));
        plano.setVolumeAlvoKm(BigDecimal.valueOf(50.0));
        return plano;
    }

    private TreinoRealizadoOutputDto treinoRealizadoOutput(TreinoRealizado treino) {
        return new TreinoRealizadoOutputDto(
                UUID.randomUUID(),
                treino.getDataTreino(),
                treino.getDiaSemana(),
                treino.getTipoTreino(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private TreinoPlanejado criarTreinoPlanejadoMock() {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setId(UUID.randomUUID());
        treino.setTipoTreino(TipoTreino.CONTINUO);
        treino.setDescricao("Treino contínuo mock");
        treino.setDistanciaKm(BigDecimal.valueOf(10.0));
        treino.setDataTreino(LocalDate.now().plusDays(1));
        return treino;
    }
}
