package com.menthoros.services.impl;

import com.menthoros.dto.input.DadosPlanoDto;
import com.menthoros.dto.llm.PlanoSemanalLlmDto;
import com.menthoros.dto.llm.TreinoPlanejadoLlmDto;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.PlanoSemanal;
import com.menthoros.entity.TreinoPlanejado;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.enums.*;
import com.menthoros.exception.DomainRuleViolationException;
import com.menthoros.exception.LLMException;
import com.menthoros.mapper.AtletaMapper;
import com.menthoros.mapper.PlanoSemanalMapper;
import com.menthoros.mapper.TreinoMapper;
import com.menthoros.repository.AtletaRepository;
import com.menthoros.repository.PlanoMetadadosRepository;
import com.menthoros.repository.PlanoSemanalRepository;
import com.menthoros.repository.TreinoRealizadoRepository;
import com.menthoros.services.EmbeddingService;
import com.menthoros.services.IaService;
import com.menthoros.services.helper.RedistribuicaoTreinoHelper;
import com.menthoros.services.helper.RegraGeracaoTreino;
import org.hibernate.Hibernate;
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
    private com.menthoros.services.PlanoMetadadosService planoMetadadosService;

    @InjectMocks
    private PlanoServiceImpl planoService;

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

        // Mock dependencies
        when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Collections.emptyList());
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any())).thenReturn(planoDto);

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

            verify(atletaRepository).findById(atletaId);
            verify(iaService).geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any());
            verify(planoSemanalRepository).save(any(PlanoSemanal.class));
            verify(planoMetadadosRepository, times(2)).save(any(PlanoMetaDados.class));
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

        when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Collections.emptyList());
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any())).thenReturn(null);

        try (MockedStatic<Hibernate> hibernateMock = mockStatic(Hibernate.class)) {
            hibernateMock.when(() -> Hibernate.initialize(any())).thenAnswer(invocation -> null);

            // When & Then
            assertThrows(LLMException.class, () ->
                    planoService.gerarPlanoTreino(atletaId, modoGeracao));

            verify(iaService).geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any());
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

        when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Collections.emptyList());
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any())).thenReturn(planoDto);
        when(redistribuicaoHelper.redistribuirTreinos(any(), any(), any(), any(), any(), eq(modoGeracao)))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<Hibernate> hibernateMock = mockStatic(Hibernate.class)) {
            hibernateMock.when(() -> Hibernate.initialize(any())).thenAnswer(invocation -> null);

            // When & Then
            DomainRuleViolationException exception = assertThrows(DomainRuleViolationException.class, () ->
                    planoService.gerarPlanoTreino(atletaId, modoGeracao));

            assertTrue(exception.getMessage().contains("Não foi possível gerar treinos"));

            verify(iaService).geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any());
            verify(redistribuicaoHelper).redistribuirTreinos(any(), any(), any(), any(), any(), eq(modoGeracao));
        }
    }

    @Test
    @DisplayName("Deve lançar exceção com ID de atleta inválido")
    void deveLancarExcecaoComIdAtletaInvalido() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.PROXIMA_SEMANA;

        when(atletaRepository.findById(atletaId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () ->
                planoService.gerarPlanoTreino(atletaId, modoGeracao));

        verify(atletaRepository).findById(atletaId);
        verify(iaService, never()).geraPlanoSemanalAvancado(any(), any(), any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando LLM lança exceção durante geração")
    void deveLancarExcecaoQuandoLlmLancaExcecaoDuranteGeracao() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.PROXIMA_SEMANA;

        Atleta atleta = criarAtletaMock(atletaId);
        PlanoMetaDados metaDados = criarPlanoMetaDadosMock();

        when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Collections.emptyList());
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any()))
                .thenThrow(new LLMException("Erro na IA"));

        try (MockedStatic<Hibernate> hibernateMock = mockStatic(Hibernate.class)) {
            hibernateMock.when(() -> Hibernate.initialize(any())).thenAnswer(invocation -> null);

            // When & Then - should throw LLMException when LLM fails
            LLMException exception = assertThrows(LLMException.class, () ->
                    planoService.gerarPlanoTreino(atletaId, modoGeracao));

            assertEquals("Erro na IA", exception.getMessage());

            verify(iaService).geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any());
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

        when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Collections.emptyList());
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any())).thenReturn(planoDto);
        when(redistribuicaoHelper.redistribuirTreinos(any(), any(), any(), any(), any(), eq(modoGeracao)))
                .thenReturn(treinosRedistribuidos);

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

            verify(redistribuicaoHelper).redistribuirTreinos(any(), any(), any(), any(), any(), eq(ModoGeracaoPlano.SEMANA_ATUAL));
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
        PlanoSemanalLlmDto planoDto = criarPlanoSemanalLlmDto();

        when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
        when(planoMetadadosService.buscarOuCriarMetadados(atleta)).thenReturn(metaDados);
        when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Collections.emptyList());
        when(planoSemanalRepository.findTopByAtletaIdOrderBySemanaInicioDesc(atletaId)).thenReturn(Optional.empty());
        when(planoSemanalRepository.findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        when(iaService.geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any())).thenReturn(planoDto);

        try (MockedStatic<Hibernate> hibernateMock = mockStatic(Hibernate.class)) {
            hibernateMock.when(() -> Hibernate.initialize(any())).thenAnswer(invocation -> null);

            // When & Then
            assertThrows(LLMException.class, () ->
                    planoService.gerarPlanoTreino(atletaId, modoGeracao));

            verify(iaService).geraPlanoSemanalAvancado(eq(atleta), eq(metaDados), any());
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

        when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));

        // When & Then
        DomainRuleViolationException exception = assertThrows(DomainRuleViolationException.class, () ->
                planoService.gerarPlanoTreino(atletaId, modoGeracao));

        // Verifica que a mensagem de erro é sobre dias disponíveis
        assertTrue(exception.getMessage().contains("sem dias disponíveis"));

        // Verifica que a validação falhou antes de chamar serviços de IA ou redistribuição
        verify(atletaRepository).findById(atletaId);
        verify(iaService, never()).geraPlanoSemanalAvancado(any(), any(), any());
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

        when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));

        // When & Then
        DomainRuleViolationException exception = assertThrows(DomainRuleViolationException.class, () ->
                planoService.gerarPlanoTreino(atletaId, modoGeracao));

        // Verifica que a mensagem de erro é sobre atleta inativo
        assertTrue(exception.getMessage().contains("atleta inativo"));

        // Verifica que a validação falhou antes de chamar serviços de IA
        verify(atletaRepository).findById(atletaId);
        verify(iaService, never()).geraPlanoSemanalAvancado(any(), any(), any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando atleta não tem objetivo definido")
    void deveLancarExcecaoQuandoAtletaNaoTemObjetivo() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.PROXIMA_SEMANA;

        Atleta atleta = criarAtletaMock(atletaId);
        atleta.setObjetivo(null); // Sem objetivo

        when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));

        // When & Then
        DomainRuleViolationException exception = assertThrows(DomainRuleViolationException.class, () ->
                planoService.gerarPlanoTreino(atletaId, modoGeracao));

        // Verifica que a mensagem de erro é sobre objetivo
        assertTrue(exception.getMessage().contains("sem objetivo definido"));

        // Verifica que a validação falhou antes de chamar serviços de IA
        verify(atletaRepository).findById(atletaId);
        verify(iaService, never()).geraPlanoSemanalAvancado(any(), any(), any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando atleta não tem nível de experiência definido")
    void deveLancarExcecaoQuandoAtletaNaoTemNivelExperiencia() {
        // Given
        UUID atletaId = UUID.randomUUID();
        ModoGeracaoPlano modoGeracao = ModoGeracaoPlano.PROXIMA_SEMANA;

        Atleta atleta = criarAtletaMock(atletaId);
        atleta.setNivelExperiencia(null); // Sem nível de experiência

        when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));

        // When & Then
        DomainRuleViolationException exception = assertThrows(DomainRuleViolationException.class, () ->
                planoService.gerarPlanoTreino(atletaId, modoGeracao));

        // Verifica que a mensagem de erro é sobre nível de experiência
        assertTrue(exception.getMessage().contains("sem nível de experiência"));

        // Verifica que a validação falhou antes de chamar serviços de IA
        verify(atletaRepository).findById(atletaId);
        verify(iaService, never()).geraPlanoSemanalAvancado(any(), any(), any());
    }

    // Helper methods to create mock objects
    private Atleta criarAtletaMock(UUID atletaId) {
        Atleta atleta = new Atleta();
        atleta.setId(atletaId);
        atleta.setNome("João Silva");
        atleta.setEmail("joao@teste.com");
        atleta.setDiasDisponiveis(List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA));
        atleta.setProvas(new ArrayList<>());
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setObjetivo("Teste objetivo");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
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
