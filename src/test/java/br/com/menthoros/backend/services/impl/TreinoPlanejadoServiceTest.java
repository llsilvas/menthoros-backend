package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.EtapaInputDto;
import br.com.menthoros.backend.dto.input.TreinoPlanejadoAddDto;
import br.com.menthoros.backend.dto.input.TreinoPlanejadoPatchDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.EtapaMapper;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreinoPlanejadoServiceTest {

    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private TssCalculatorService tssCalculatorService;
    @Mock private TreinoMapper treinoMapper;
    @Mock private EtapaMapper etapaMapper;

    @InjectMocks private TreinoPlanejadoServiceImpl service;

    private UUID tenantId;
    private UUID planoId;
    private UUID treinoId;

    private static final LocalDate SEMANA_INICIO = LocalDate.of(2026, 7, 1);
    private static final LocalDate SEMANA_FIM = LocalDate.of(2026, 7, 7);
    private static final LocalDate DATA_SEXTA = LocalDate.of(2026, 7, 3);
    private static final LocalDate DATA_QUINTA = LocalDate.of(2026, 7, 2);

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        planoId = UUID.randomUUID();
        treinoId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("adicionarTreino")
    class AdicionarTreino {

        @Test
        @DisplayName("treino simples criado com adicionadoPeloCoach=true, PENDENTE e MANUAL")
        void treinoSimplesCriadoComFlagsCorretos() {
            PlanoSemanal plano = planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>());
            stubPlanoFound(plano);
            TreinoPlanejado saved = new TreinoPlanejado();
            when(treinoPlanejadoRepository.save(any())).thenReturn(saved);
            when(treinoMapper.toOutputDto(saved)).thenReturn(outputStub());

            service.adicionarTreino(planoId, dtoSimples(DATA_SEXTA));

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            TreinoPlanejado treino = captor.getValue();
            assertThat(treino.isAdicionadoPeloCoach()).isTrue();
            assertThat(treino.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.PENDENTE);
            assertThat(treino.getFonteDados()).isEqualTo(FonteDados.MANUAL);
        }

        @Test
        @DisplayName("etapas adicionadas com ordem=1 e ordem=2")
        void treinoComDuasEtapasPersistidoNaOrdemCorreta() {
            PlanoSemanal plano = planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>());
            stubPlanoFound(plano);
            TreinoPlanejado saved = new TreinoPlanejado();
            when(treinoPlanejadoRepository.save(any())).thenReturn(saved);
            when(treinoMapper.toOutputDto(saved)).thenReturn(outputStub());
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> new EtapaTreino());

            List<EtapaInputDto> etapas = List.of(
                    new EtapaInputDto("AQUECIMENTO", null, 10, null, null, null, null, null, null),
                    new EtapaInputDto("PRINCIPAL", null, 60, null, null, null, null, null, null)
            );
            TreinoPlanejadoAddDto dto = new TreinoPlanejadoAddDto(
                    "CONTINUO", DATA_SEXTA, null, null, 70, null, null, null, null, etapas
            );

            service.adicionarTreino(planoId, dto);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            List<EtapaTreino> etapasSalvas = captor.getValue().getEtapas();
            assertThat(etapasSalvas).hasSize(2);
            assertThat(etapasSalvas.get(0).getOrdem()).isEqualTo(1);
            assertThat(etapasSalvas.get(1).getOrdem()).isEqualTo(2);
        }

        @Test
        @DisplayName("TSS calculado quando duracaoMin informado e tssPlanejado ausente")
        void tssCalculadoQuandoDuracaoMinInformadaETssPlanejadoAusente() {
            PlanoSemanal plano = planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>());
            stubPlanoFound(plano);
            when(tssCalculatorService.calcularTssEstimado(Duration.ofMinutes(45), 6)).thenReturn(18);
            TreinoPlanejado saved = new TreinoPlanejado();
            when(treinoPlanejadoRepository.save(any())).thenReturn(saved);
            when(treinoMapper.toOutputDto(saved)).thenReturn(outputStub());

            TreinoPlanejadoAddDto dto = new TreinoPlanejadoAddDto(
                    "CONTINUO", DATA_SEXTA, null, null, 45, null, 6, null, null, null
            );

            service.adicionarTreino(planoId, dto);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            assertThat(captor.getValue().getTssPlanejado()).isEqualTo(18);
            verify(tssCalculatorService).calcularTssEstimado(Duration.ofMinutes(45), 6);
        }

        @Test
        @DisplayName("duracaoMin ausente: duracaoMin=Duration.ZERO e tssPlanejado=null")
        void duracaoMinAusenteResultaNuloETssNulo() {
            PlanoSemanal plano = planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>());
            stubPlanoFound(plano);
            TreinoPlanejado saved = new TreinoPlanejado();
            when(treinoPlanejadoRepository.save(any())).thenReturn(saved);
            when(treinoMapper.toOutputDto(saved)).thenReturn(outputStub());

            service.adicionarTreino(planoId, dtoSimples(DATA_SEXTA));

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            assertThat(captor.getValue().getDuracaoMin()).isEqualTo(Duration.ZERO);
            assertThat(captor.getValue().getTssPlanejado()).isNull();
            verify(tssCalculatorService, never()).calcularTssEstimado(any(), any());
        }

        @Test
        @DisplayName("tssPlanejado explícito do coach prevalece sobre o cálculo automático")
        void tssPlanejadoExplicitoPrevaleceSobreCalculo() {
            PlanoSemanal plano = planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>());
            stubPlanoFound(plano);
            TreinoPlanejado saved = new TreinoPlanejado();
            when(treinoPlanejadoRepository.save(any())).thenReturn(saved);
            when(treinoMapper.toOutputDto(saved)).thenReturn(outputStub());

            TreinoPlanejadoAddDto dto = new TreinoPlanejadoAddDto(
                    "CONTINUO", DATA_SEXTA, null, null, 45, null, 6, 99, null, null
            );

            service.adicionarTreino(planoId, dto);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            assertThat(captor.getValue().getTssPlanejado()).isEqualTo(99);
            verify(tssCalculatorService, never()).calcularTssEstimado(any(), any());
        }

        @Test
        @DisplayName("plano não encontrado lança DomainNotFoundException")
        void planoNaoEncontradoLancaNotFoundException() {
            when(planoSemanalRepository.findByIdWithDependenciesAndTenant(planoId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.adicionarTreino(planoId, dtoSimples(DATA_SEXTA)))
                    .isInstanceOf(DomainNotFoundException.class)
                    .hasMessageContaining(planoId.toString());
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("plano APROVADO lança DomainRuleViolationException")
        void planoAprovadoLancaRuleViolation() {
            stubPlanoFound(planoStub(PlanoReviewStatus.APROVADO, new ArrayList<>()));

            assertThatThrownBy(() -> service.adicionarTreino(planoId, dtoSimples(DATA_SEXTA)))
                    .isInstanceOf(DomainRuleViolationException.class);
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("plano REJEITADO lança DomainRuleViolationException")
        void planoRejeitadoLancaRuleViolation() {
            stubPlanoFound(planoStub(PlanoReviewStatus.REJEITADO, new ArrayList<>()));

            assertThatThrownBy(() -> service.adicionarTreino(planoId, dtoSimples(DATA_SEXTA)))
                    .isInstanceOf(DomainRuleViolationException.class);
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("dataTreino após semanaFim lança DomainRuleViolationException")
        void dataTreinoAposSemanaFimLancaRuleViolation() {
            stubPlanoFound(planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>()));
            LocalDate foraDaJanela = SEMANA_FIM.plusDays(1);

            assertThatThrownBy(() -> service.adicionarTreino(planoId, dtoSimples(foraDaJanela)))
                    .isInstanceOf(DomainRuleViolationException.class);
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("dataTreino antes de semanaInicio lança DomainRuleViolationException")
        void dataTreinoAntesSemanaInicioLancaRuleViolation() {
            stubPlanoFound(planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>()));
            LocalDate antesDoInicio = SEMANA_INICIO.minusDays(1);

            assertThatThrownBy(() -> service.adicionarTreino(planoId, dtoSimples(antesDoInicio)))
                    .isInstanceOf(DomainRuleViolationException.class);
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("14 treinos existentes lança DomainRuleViolationException com mensagem de limite")
        void limiteQuatorzeTreinosLancaRuleViolation() {
            PlanoSemanal plano = planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>());
            stubPlanoFound(plano);
            when(treinoPlanejadoRepository.countByPlanoSemanalIdAndTenantId(plano.getId(), tenantId)).thenReturn(14L);

            assertThatThrownBy(() -> service.adicionarTreino(planoId, dtoSimples(DATA_SEXTA)))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("14");
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("plano de outro tenant lança DomainNotFoundException (cross-tenant)")
        void crossTenantLancaNotFoundException() {
            when(planoSemanalRepository.findByIdWithDependenciesAndTenant(planoId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.adicionarTreino(planoId, dtoSimples(DATA_SEXTA)))
                    .isInstanceOf(DomainNotFoundException.class);
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("diaSemana derivado de dataTreino=quinta-feira resulta em QUINTA")
        void diaSemanaDerivadoDeDataTreinoQuinta() {
            PlanoSemanal plano = planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>());
            stubPlanoFound(plano);
            TreinoPlanejado saved = new TreinoPlanejado();
            when(treinoPlanejadoRepository.save(any())).thenReturn(saved);
            when(treinoMapper.toOutputDto(saved)).thenReturn(outputStub());

            service.adicionarTreino(planoId, dtoSimples(DATA_QUINTA));

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            assertThat(captor.getValue().getDiaSemana()).isEqualTo(DiaSemana.QUINTA);
        }

        @Test
        @DisplayName("tipoTreino inválido lança DomainRuleViolationException")
        void tipoTreinoInvalidoLancaRuleViolation() {
            stubPlanoFound(planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>()));

            TreinoPlanejadoAddDto dto = new TreinoPlanejadoAddDto(
                    "TIPO_INEXISTENTE", DATA_SEXTA, null, null, null, null, null, null, null, null
            );

            assertThatThrownBy(() -> service.adicionarTreino(planoId, dto))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("TIPO_INEXISTENTE");
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("planoId nulo lança IllegalArgumentException")
        void planoIdNuloLancaIllegalArgument() {
            assertThatThrownBy(() -> service.adicionarTreino(null, dtoSimples(DATA_SEXTA)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("dto nulo lança IllegalArgumentException")
        void dtoNuloLancaIllegalArgument() {
            assertThatThrownBy(() -> service.adicionarTreino(planoId, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("adicionarTreino — blocos repetidos")
    class AdicionarTreinoBlocos {

        @Test
        @DisplayName("bloco com 3 repetições e 2 sub-etapas gera 6 etapas com mesma blocoId")
        void blocoTresRepsGeraSeisEtapasComMesmaBlocoId() {
            PlanoSemanal plano = planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>());
            stubPlanoFound(plano);
            TreinoPlanejado saved = new TreinoPlanejado();
            when(treinoPlanejadoRepository.save(any())).thenReturn(saved);
            when(treinoMapper.toOutputDto(saved)).thenReturn(outputStub());
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> new EtapaTreino());

            List<EtapaInputDto> subs = List.of(
                    new EtapaInputDto("INTERVALADO", null, 3, null, null, null, null, null, null),
                    new EtapaInputDto("RECUPERACAO", null, 1, null, null, null, null, null, null)
            );
            TreinoPlanejadoAddDto dto = new TreinoPlanejadoAddDto(
                    "INTERVALADO", DATA_SEXTA, null, null, null, null, null, null, null,
                    List.of(new EtapaInputDto("BLOCO", null, null, null, null, null, null, 3, subs))
            );

            service.adicionarTreino(planoId, dto);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            List<EtapaTreino> etapas = captor.getValue().getEtapas();
            assertThat(etapas).hasSize(6);
            UUID blocoId = etapas.get(0).getBlocoId();
            assertThat(blocoId).isNotNull();
            assertThat(etapas).allSatisfy(e -> assertThat(e.getBlocoId()).isEqualTo(blocoId));
            assertThat(etapas).allSatisfy(e -> assertThat(e.getBlocoRepeticoes()).isEqualTo(3));
            for (int i = 0; i < 6; i++) assertThat(etapas.get(i).getOrdem()).isEqualTo(i + 1);
        }

        @Test
        @DisplayName("bloco com blocoRepeticoes nulo usa 1 repetição por default")
        void blocoComRepeticoesNuloUsaDefaultUm() {
            PlanoSemanal plano = planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>());
            stubPlanoFound(plano);
            TreinoPlanejado saved = new TreinoPlanejado();
            when(treinoPlanejadoRepository.save(any())).thenReturn(saved);
            when(treinoMapper.toOutputDto(saved)).thenReturn(outputStub());
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> new EtapaTreino());

            TreinoPlanejadoAddDto dto = new TreinoPlanejadoAddDto(
                    "CONTINUO", DATA_SEXTA, null, null, null, null, null, null, null,
                    List.of(new EtapaInputDto("BLOCO", null, null, null, null, null, null, null,
                            List.of(new EtapaInputDto("PRINCIPAL", null, 30, null, null, null, null, null, null))))
            );

            service.adicionarTreino(planoId, dto);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            List<EtapaTreino> etapas = captor.getValue().getEtapas();
            assertThat(etapas).hasSize(1);
            assertThat(etapas.get(0).getBlocoRepeticoes()).isEqualTo(1);
        }

        @Test
        @DisplayName("bloco com subEtapas vazias lança DomainRuleViolationException")
        void blocoComSubEtapasVaziasLancaRuleViolation() {
            stubPlanoFound(planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>()));

            TreinoPlanejadoAddDto dto = new TreinoPlanejadoAddDto(
                    "CONTINUO", DATA_SEXTA, null, null, null, null, null, null, null,
                    List.of(new EtapaInputDto("BLOCO", null, null, null, null, null, null, 3, List.of()))
            );

            assertThatThrownBy(() -> service.adicionarTreino(planoId, dto))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("sub-etapa");
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("bloco intercalado com etapas simples mantém ordem global contínua")
        void blocoIntercaladoMantemOrdemGlobal() {
            PlanoSemanal plano = planoStub(PlanoReviewStatus.AGUARDANDO_REVISAO, new ArrayList<>());
            stubPlanoFound(plano);
            TreinoPlanejado saved = new TreinoPlanejado();
            when(treinoPlanejadoRepository.save(any())).thenReturn(saved);
            when(treinoMapper.toOutputDto(saved)).thenReturn(outputStub());
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> new EtapaTreino());

            List<EtapaInputDto> subs = List.of(
                    new EtapaInputDto("INTERVALADO", null, 3, null, null, null, null, null, null),
                    new EtapaInputDto("RECUPERACAO", null, 1, null, null, null, null, null, null)
            );
            TreinoPlanejadoAddDto dto = new TreinoPlanejadoAddDto(
                    "INTERVALADO", DATA_SEXTA, null, null, null, null, null, null, null,
                    List.of(
                            new EtapaInputDto("AQUECIMENTO", null, 10, null, null, null, null, null, null),
                            new EtapaInputDto("BLOCO", null, null, null, null, null, null, 2, subs),
                            new EtapaInputDto("DESAQUECIMENTO", null, 10, null, null, null, null, null, null)
                    )
            );

            service.adicionarTreino(planoId, dto);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            List<EtapaTreino> etapas = captor.getValue().getEtapas();
            assertThat(etapas).hasSize(6);
            assertThat(etapas.get(0).getOrdem()).isEqualTo(1);
            assertThat(etapas.get(0).getBlocoId()).isNull();
            assertThat(etapas.get(5).getOrdem()).isEqualTo(6);
            assertThat(etapas.get(5).getBlocoId()).isNull();
            UUID blocoId = etapas.get(1).getBlocoId();
            assertThat(blocoId).isNotNull();
            for (int i = 1; i <= 4; i++) {
                assertThat(etapas.get(i).getBlocoId()).isEqualTo(blocoId);
            }
        }
    }

    @Nested
    @DisplayName("editarTreino")
    class EditarTreino {

        @Test
        @DisplayName("patch com BLOCO grava blocoId compartilhado e blocoRepeticoes")
        void patchComBlocoGravaBlocoId() {
            // O caminho de adição já fazia isso; o de patch chamava toEntity direto e deixava
            // blocoId nulo, então editar um treino desfazia o agrupamento da série.
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setEtapas(new ArrayList<>());

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> new EtapaTreino());

            List<EtapaInputDto> subs = List.of(
                    new EtapaInputDto("INTERVALADO", null, 1, null, null, null, null, null, null),
                    new EtapaInputDto("RECUPERACAO", null, 2, null, null, null, null, null, null)
            );
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null,
                    List.of(new EtapaInputDto("BLOCO", null, null, null, null, null, null, 4, subs))
            );

            service.editarTreino(planoId, treinoId, patch);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            List<EtapaTreino> etapas = captor.getValue().getEtapas();

            assertThat(etapas).hasSize(8);
            UUID blocoId = etapas.getFirst().getBlocoId();
            assertThat(blocoId).isNotNull();
            assertThat(etapas).allSatisfy(e -> assertThat(e.getBlocoId()).isEqualTo(blocoId));
            assertThat(etapas).allSatisfy(e -> assertThat(e.getBlocoRepeticoes()).isEqualTo(4));
            for (int i = 0; i < 8; i++) assertThat(etapas.get(i).getOrdem()).isEqualTo(i + 1);
        }

        @Test
        @DisplayName("dois blocos no mesmo patch recebem blocoId distintos")
        void doisBlocosRecebemBlocoIdDistintos() {
            // Série heterogênea: 2× (1min Z4 + 2min Z1) seguida de 2× (2min Z5 + 3min Z1).
            // É o caso que o editor colapsava e que motivou a change.
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setEtapas(new ArrayList<>());

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> new EtapaTreino());

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null,
                    List.of(
                            new EtapaInputDto("BLOCO", null, null, null, null, null, null, 2, List.of(
                                    new EtapaInputDto("INTERVALADO", null, 1, null, null, null, null, null, null),
                                    new EtapaInputDto("RECUPERACAO", null, 2, null, null, null, null, null, null))),
                            new EtapaInputDto("BLOCO", null, null, null, null, null, null, 2, List.of(
                                    new EtapaInputDto("INTERVALADO", null, 2, null, null, null, null, null, null),
                                    new EtapaInputDto("RECUPERACAO", null, 3, null, null, null, null, null, null)))
                    )
            );

            service.editarTreino(planoId, treinoId, patch);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            List<EtapaTreino> etapas = captor.getValue().getEtapas();

            assertThat(etapas).hasSize(8);
            UUID primeiro = etapas.get(0).getBlocoId();
            UUID segundo  = etapas.get(4).getBlocoId();
            assertThat(primeiro).isNotNull();
            assertThat(segundo).isNotNull().isNotEqualTo(primeiro);
            assertThat(etapas.subList(0, 4)).allSatisfy(e -> assertThat(e.getBlocoId()).isEqualTo(primeiro));
            assertThat(etapas.subList(4, 8)).allSatisfy(e -> assertThat(e.getBlocoId()).isEqualTo(segundo));
        }

        @Test
        @DisplayName("patch preserva o ritmo por etapa — antes a edição apagava o que o planner prescreveu")
        void patchPreservaRitmoPorEtapa() {
            // O planner prescreve ritmo por etapa (o schema do structured output exige ritmoAlvo,
            // nullable, em cada etapa). Como aplicarEtapasPatch limpa as etapas e as reconstrói a
            // partir do EtapaInputDto, um campo ausente do DTO nascia nulo: editar qualquer coisa no
            // treino apagava o ritmo de todas as etapas, sem erro e sem aviso.
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setEtapas(new ArrayList<>());

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any(TreinoPlanejado.class))).thenAnswer(inv -> inv.getArgument(0));
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> etapaDe(inv.getArgument(0)));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null,
                    List.of(new EtapaInputDto("PRINCIPAL", "Tiro", 5, null,
                            "140-150 bpm", "5:00-5:15/km", 1, null, null))
            );

            service.editarTreino(planoId, treinoId, patch);

            assertThat(treino.getEtapas()).hasSize(1);
            assertThat(treino.getEtapas().get(0).getRitmoAlvo()).isEqualTo("5:00-5:15/km");
            assertThat(treino.getEtapas().get(0).getFcAlvoEtapa()).isEqualTo("140-150 bpm");
        }

        @Test
        @DisplayName("bloco expandido preserva o ritmo em cada cópia da série")
        void blocoExpandidoPreservaRitmo() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setEtapas(new ArrayList<>());

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any(TreinoPlanejado.class))).thenAnswer(inv -> inv.getArgument(0));
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> etapaDe(inv.getArgument(0)));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null,
                    List.of(new EtapaInputDto("BLOCO", null, null, null, null, null, null, 3, List.of(
                            new EtapaInputDto("INTERVALADO", "Tiro", 1, null, null, "4:00-4:10/km", null, null, null))))
            );

            service.editarTreino(planoId, treinoId, patch);

            // comRepeticoes() reconstrói o DTO de cada cópia: sem o campo ali, a série perdia o
            // ritmo mesmo com o DTO de entrada carregando-o.
            assertThat(treino.getEtapas()).hasSize(3);
            assertThat(treino.getEtapas()).allSatisfy(e ->
                    assertThat(e.getRitmoAlvo()).isEqualTo("4:00-4:10/km"));
        }

        @Test
        @DisplayName("patch com BLOCO sem sub-etapas é rejeitado — antes o bloco sumia calado")
        void patchComBlocoVazioRejeitado() {
            // Mudança de contrato do PATCH, deliberada: o caminho antigo iterava uma lista vazia e
            // descartava o bloco sem erro. Perder etapa em silêncio é pior que 422.
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setEtapas(new ArrayList<>());

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null,
                    List.of(new EtapaInputDto("BLOCO", null, null, null, null, null, null, 3, List.of()))
            );

            assertThatThrownBy(() -> service.editarTreino(planoId, treinoId, patch))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("ao menos uma sub-etapa");
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("patch com blocoRepeticoes acima de 20 é rejeitado — antes não havia limite")
        void patchComRepeticoesAcimaDoLimiteRejeitado() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setEtapas(new ArrayList<>());

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null,
                    List.of(new EtapaInputDto("BLOCO", null, null, null, null, null, null, 21, List.of(
                            new EtapaInputDto("INTERVALADO", null, 1, null, null, null, null, null, null))))
            );

            assertThatThrownBy(() -> service.editarTreino(planoId, treinoId, patch))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("exceder 20");
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("patch com sub-etapa aninhada é rejeitado")
        void patchComSubEtapaAninhadaRejeitado() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setEtapas(new ArrayList<>());

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));

            EtapaInputDto aninhada = new EtapaInputDto("INTERVALADO", null, 1, null, null, null, null, null,
                    List.of(new EtapaInputDto("RECUPERACAO", null, 1, null, null, null, null, null, null)));
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null,
                    List.of(new EtapaInputDto("BLOCO", null, null, null, null, null, null, 2, List.of(aninhada)))
            );

            assertThatThrownBy(() -> service.editarTreino(planoId, treinoId, patch))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("aninhadas");
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("patch de treino simples não cria bloco — etapas sem blocoId")
        void patchSimplesNaoCriaBloco() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setEtapas(new ArrayList<>());

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> new EtapaTreino());

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null,
                    List.of(
                            new EtapaInputDto("AQUECIMENTO", null, 10, null, null, null, null, null, null),
                            new EtapaInputDto("PRINCIPAL", null, 30, null, null, null, null, null, null),
                            new EtapaInputDto("DESAQUECIMENTO", null, 5, null, null, null, null, null, null)
                    )
            );

            service.editarTreino(planoId, treinoId, patch);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            List<EtapaTreino> etapas = captor.getValue().getEtapas();

            assertThat(etapas).hasSize(3);
            assertThat(etapas).allSatisfy(e -> assertThat(e.getBlocoId()).isNull());
            for (int i = 0; i < 3; i++) assertThat(etapas.get(i).getOrdem()).isEqualTo(i + 1);
        }

        @Test
        @DisplayName("atualiza campos não-nulos e seta editadoPeloCoach true")
        void atualizaCamposNaoNulosESetaEditadoPeloCoach() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            TreinoPlanejadoOutputDto outputEsperado = outputStub(treinoId, true);

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId)).thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputEsperado);

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, BigDecimal.valueOf(18.0), null, null, null, null, "Reduzido após prova", null
            );

            TreinoPlanejadoOutputDto result = service.editarTreino(planoId, treinoId, patch);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            TreinoPlanejado salvo = captor.getValue();

            assertThat(salvo.getDistanciaKm()).isEqualByComparingTo(BigDecimal.valueOf(18.0));
            assertThat(salvo.getObservacao()).isEqualTo("Reduzido após prova");
            assertThat(salvo.isEditadoPeloCoach()).isTrue();
            assertThat(result).isEqualTo(outputEsperado);
        }

        @Test
        @DisplayName("ignora campos null — patch semântico preserva valores existentes")
        void ignoraCamposNullPatchSemantico() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setTipoTreino(TipoTreino.LONGO);
            treino.setZonaAlvo("z2");

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId)).thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, BigDecimal.valueOf(15.0), null, null, null, null, null, null
            );

            service.editarTreino(planoId, treinoId, patch);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            TreinoPlanejado salvo = captor.getValue();

            assertThat(salvo.getTipoTreino()).isEqualTo(TipoTreino.LONGO);
            assertThat(salvo.getZonaAlvo()).isEqualTo("z2");
            assertThat(salvo.getDistanciaKm()).isEqualByComparingTo(BigDecimal.valueOf(15.0));
        }

        @Test
        @DisplayName("recalcula TSS quando duracaoMin muda sem tssPlanejado explícito")
        void recalculaTssQuandoDuracaoMudaSemTssExplicito() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setDuracaoMin(Duration.ofMinutes(60));
            treino.setPercepcaoEsforcoEsperada(7);
            treino.setTssPlanejado(55);

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId)).thenReturn(Optional.of(treino));
            // 122 = o que o calculador real devolve para 90min RPE 7. Este teste é de wiring, então
            // o valor é stub — mas usar 49 aqui (o resultado da fórmula ANTIGA) plantava a escala
            // errada na cara de quem lê. A escala em si é afirmada em tssRecalculadoSaiNaEscalaDoRealizado.
            when(tssCalculatorService.calcularTssEstimado(Duration.ofMinutes(90), 7)).thenReturn(122);
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, Duration.ofMinutes(90), null, null, null, null, null
            );

            service.editarTreino(planoId, treinoId, patch);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            assertThat(captor.getValue().getTssPlanejado()).isEqualTo(122);
        }

        @Test
        @DisplayName("TSS recalculado sai na escala do realizado, não na da fórmula antiga (CA2)")
        void tssRecalculadoSaiNaEscalaDoRealizado() {
            // Calculador REAL de propósito. Com o mock, o valor recalculado é um stub arbitrário e a
            // asserção não prova escala nenhuma — e escala é exatamente o que o BUG-CONF-001 quebrava.
            TreinoPlanejadoServiceImpl servicoComCalculadorReal = new TreinoPlanejadoServiceImpl(
                    planoSemanalRepository, treinoPlanejadoRepository, new TssCalculatorService(),
                    treinoMapper, etapaMapper);

            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setDuracaoMin(Duration.ofMinutes(60));
            treino.setPercepcaoEsforcoEsperada(7);
            treino.setTssPlanejado(81); // veio do gerador de plano, já na escala certa

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId)).thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, Duration.ofMinutes(90), null, null, null, null, null
            );

            servicoComCalculadorReal.editarTreino(planoId, treinoId, patch);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            int recalculado = captor.getValue().getTssPlanejado();

            // 1,5h × IF(7)=0,90² × 100 = 121,5 -> 122. A fórmula antiga daria 90 × 7² / 90 = 49,
            // ou seja, um treino MAIS longo valendo MENOS que os 81 que o gerador tinha posto.
            assertThat(recalculado).isEqualTo(122);

            // O CA2 propriamente dito: o recalculado é o mesmo que o caminho realizado produz para
            // a mesma duração e esforço (RPE puro, sem tipo de treino — sem fator de impacto).
            TreinoRealizado realizadoMesmoEsforco = new TreinoRealizado();
            realizadoMesmoEsforco.setDuracaoMin(Duration.ofMinutes(90));
            realizadoMesmoEsforco.setPercepcaoEsforco(7);
            assertThat(recalculado)
                    .as("recalculado (%d) deve bater com o realizado equivalente", recalculado)
                    .isEqualTo(new TssCalculatorService().calcularTss(realizadoMesmoEsforco));
        }

        @Test
        @DisplayName("usa TSS do coach quando informado explicitamente")
        void usaTssDoCoachQuandoInformadoExplicitamente() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setDuracaoMin(Duration.ofMinutes(60));

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId)).thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, Duration.ofMinutes(90), null, 65, null, null, null
            );

            service.editarTreino(planoId, treinoId, patch);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            assertThat(captor.getValue().getTssPlanejado()).isEqualTo(65);
            verify(tssCalculatorService, never()).calcularTssEstimado(any(), any());
        }

        @Test
        @DisplayName("não recalcula TSS quando distanciaKm e duracaoMin não mudam")
        void naoRecalculaTssQuandoVolumeNaoMuda() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setTssPlanejado(55);

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId)).thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, "z3", null, null, null, null
            );

            service.editarTreino(planoId, treinoId, patch);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            assertThat(captor.getValue().getTssPlanejado()).isEqualTo(55);
            verify(tssCalculatorService, never()).calcularTssEstimado(any(), any());
        }

        @Test
        @DisplayName("lança DomainRuleViolationException se plano não está AGUARDANDO_REVISAO")
        void lancaExcecaoSePlanoNaoEstaAguardandoRevisao() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.APROVADO);

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, null
            );

            assertThatThrownBy(() -> service.editarTreino(planoId, treinoId, patch))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("revisão");

            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança DomainNotFoundException se plano pertence a outro tenant")
        void lancaExcecaoSePlanoDeOutroTenant() {
            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.empty());

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, null
            );

            assertThatThrownBy(() -> service.editarTreino(planoId, treinoId, patch))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança DomainNotFoundException se treino não existe no tenant ou não pertence ao plano")
        void lancaExcecaoSeTreinoNaoEncontrado() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.empty());

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, null
            );

            assertThatThrownBy(() -> service.editarTreino(planoId, treinoId, patch))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando planoId é nulo")
        void lancaExcecaoParaPlanoIdNulo() {
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, null
            );
            assertThatThrownBy(() -> service.editarTreino(null, treinoId, patch))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando treinoId é nulo")
        void lancaExcecaoParaTreinoIdNulo() {
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, null
            );
            assertThatThrownBy(() -> service.editarTreino(planoId, null, patch))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando patch é nulo")
        void lancaExcecaoParaPatchNulo() {
            assertThatThrownBy(() -> service.editarTreino(planoId, treinoId, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("recalcula TSS quando distanciaKm muda sem tssPlanejado explícito")
        void recalculaTssQuandoDistanciaMudaSemTssExplicito() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);
            treino.setDistanciaKm(BigDecimal.valueOf(10.0));
            treino.setPercepcaoEsforcoEsperada(6);
            treino.setTssPlanejado(45);

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId)).thenReturn(Optional.of(treino));
            when(tssCalculatorService.calcularTssEstimado(any(), any())).thenReturn(38);
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, BigDecimal.valueOf(20.0), null, null, null, null, null, null
            );

            service.editarTreino(planoId, treinoId, patch);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            assertThat(captor.getValue().getTssPlanejado()).isEqualTo(38);
        }
    }

    @Nested
    @DisplayName("expandirBlocos via editarTreino")
    class ExpandirBlocos {

        @Test
        @DisplayName("BLOCO com 2 reps e 1 sub-etapa gera 2 etapas com blocoRepeticoes=2")
        void expandeBlocoEmEtapasSimples() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);

            EtapaInputDto subEtapa = new EtapaInputDto("INTERVALADO", null, 5, null, null, null, null, null, null);
            EtapaInputDto bloco = new EtapaInputDto("BLOCO", null, null, null, null, null, null, 2, List.of(subEtapa));
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, List.of(bloco)
            );

            treino.setEtapas(new ArrayList<>());
            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));
            when(etapaMapper.toEntity(any(EtapaInputDto.class)))
                    .thenAnswer(inv -> etapaComTipo(inv.getArgument(0)));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            try (MockedStatic<Hibernate> ignored = mockStatic(Hibernate.class)) {
                service.editarTreino(planoId, treinoId, patch);
            }

            // Asserção na ENTIDADE, não no DTO passado ao mapper: a expansão em DTO intermediário
            // foi removida justamente porque EtapaInputDto não tem como carregar o blocoId — era a
            // causa de o patch desfazer o agrupamento da série.
            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            List<EtapaTreino> etapas = captor.getValue().getEtapas();

            assertThat(etapas).hasSize(2);
            assertThat(etapas).allSatisfy(e -> {
                assertThat(e.getTipoEtapa()).isEqualTo("INTERVALADO");
                assertThat(e.getBlocoRepeticoes()).isEqualTo(2);
                assertThat(e.getBlocoId()).isNotNull();
            });
        }

        @Test
        @DisplayName("BLOCO com 3 reps e 2 sub-etapas gera 6 etapas no total")
        void expandeBlocoComMultiplasSubEtapas() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);

            List<EtapaInputDto> subEtapas = List.of(
                    new EtapaInputDto("INTERVALADO", null, 3, null, null, null, null, null, null),
                    new EtapaInputDto("RECUPERACAO", null, 2, null, null, null, null, null, null)
            );
            EtapaInputDto bloco = new EtapaInputDto("BLOCO", null, null, null, null, null, null, 3, subEtapas);
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, List.of(bloco)
            );

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> new EtapaTreino());
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            try (MockedStatic<Hibernate> ignored = mockStatic(Hibernate.class)) {
                service.editarTreino(planoId, treinoId, patch);
            }

            verify(etapaMapper, times(6)).toEntity(any(EtapaInputDto.class));
        }

        @Test
        @DisplayName("comRepeticoes preserva blocoRepeticoes ao expandir INTERVALADO")
        void comRepeticoesPreservaBlocoRepeticoes() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);

            EtapaInputDto intervalado = new EtapaInputDto("INTERVALADO", null, 4, null, null, null, 3, 3, null);
            EtapaInputDto recuperacao = new EtapaInputDto("RECUPERACAO", null, 2, null, null, null, null, null, null);
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, List.of(intervalado, recuperacao)
            );

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> new EtapaTreino());
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            try (MockedStatic<Hibernate> ignored = mockStatic(Hibernate.class)) {
                service.editarTreino(planoId, treinoId, patch);
            }

            ArgumentCaptor<EtapaInputDto> captor = ArgumentCaptor.forClass(EtapaInputDto.class);
            verify(etapaMapper, times(6)).toEntity(captor.capture());
            assertThat(captor.getAllValues())
                    .filteredOn(e -> "INTERVALADO".equals(e.tipoEtapa()))
                    .allSatisfy(e -> assertThat(e.blocoRepeticoes()).isEqualTo(3));
        }
    }

    @Nested
    @DisplayName("expandirRepeticoes via editarTreino")
    class ExpandirRepeticoes {

        @Test
        @DisplayName("INTERVALADO(reps=3) sem RECUPERACAO expande em 3 singletons com reps=1")
        void intervaldoSemRecuperacaoExpandeEmSingletons() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);

            EtapaInputDto intervalado = new EtapaInputDto("INTERVALADO", null, 4, null, null, null, 3, null, null);
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, List.of(intervalado)
            );

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> new EtapaTreino());
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            try (MockedStatic<Hibernate> ignored = mockStatic(Hibernate.class)) {
                service.editarTreino(planoId, treinoId, patch);
            }

            ArgumentCaptor<EtapaInputDto> captor = ArgumentCaptor.forClass(EtapaInputDto.class);
            verify(etapaMapper, times(3)).toEntity(captor.capture());
            assertThat(captor.getAllValues()).allSatisfy(e -> {
                assertThat(e.tipoEtapa()).isEqualTo("INTERVALADO");
                assertThat(e.repeticoes()).isEqualTo(1);
            });
        }

        @Test
        @DisplayName("INTERVALADO(reps=1) é passado diretamente sem expansão")
        void intervaldoComUmaRepNaoExpande() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);

            EtapaInputDto intervalado = new EtapaInputDto("INTERVALADO", null, 4, null, null, null, 1, null, null);
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, List.of(intervalado)
            );

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenAnswer(inv -> new EtapaTreino());
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            try (MockedStatic<Hibernate> ignored = mockStatic(Hibernate.class)) {
                service.editarTreino(planoId, treinoId, patch);
            }

            verify(etapaMapper, times(1)).toEntity(any(EtapaInputDto.class));
        }

        @Test
        @DisplayName("lista de etapas vazia não chama o mapper")
        void listaVaziaNaoChamaMapper() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, List.of()
            );

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            try (MockedStatic<Hibernate> ignored = mockStatic(Hibernate.class)) {
                service.editarTreino(planoId, treinoId, patch);
            }

            verify(etapaMapper, never()).toEntity(any(EtapaInputDto.class));
        }
    }

    private void stubPlanoFound(PlanoSemanal plano) {
        when(planoSemanalRepository.findByIdWithDependenciesAndTenant(planoId, tenantId))
                .thenReturn(Optional.of(plano));
    }

    private PlanoSemanal planoStub(PlanoReviewStatus reviewStatus, List<TreinoPlanejado> treinos) {
        return PlanoSemanal.builder()
                .id(UUID.randomUUID())
                .semanaInicio(SEMANA_INICIO)
                .semanaFim(SEMANA_FIM)
                .reviewStatus(reviewStatus)
                .treinosPlanejados(treinos)
                .build();
    }

    private TreinoPlanejadoAddDto dtoSimples(LocalDate dataTreino) {
        return new TreinoPlanejadoAddDto(
                "CONTINUO", dataTreino, null, null, null, null, null, null, null, null
        );
    }

    private PlanoSemanal criarPlano(PlanoReviewStatus reviewStatus) {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setId(planoId);
        plano.setReviewStatus(reviewStatus);
        return plano;
    }

    /** Entidade com o tipoEtapa do DTO — o mapper real faz isso; aqui só o suficiente para asserir. */
    private EtapaTreino etapaComTipo(EtapaInputDto dto) {
        EtapaTreino etapa = new EtapaTreino();
        etapa.setTipoEtapa(dto.tipoEtapa());
        etapa.setDuracaoMin(dto.duracaoMin());
        return etapa;
    }

    private TreinoPlanejado criarTreino(PlanoSemanal plano) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setId(treinoId);
        treino.setTenantId(tenantId);
        treino.setPlanoSemanal(plano);
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(TipoTreino.CONTINUO);
        treino.setDuracaoMin(Duration.ofMinutes(60));
        treino.setDistanciaKm(BigDecimal.valueOf(10.0));
        treino.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
        treino.setDataTreino(LocalDate.now().plusDays(1));
        return treino;
    }

    /** Espelha o {@link EtapaMapper} real nos campos que o teste observa. */
    private EtapaTreino etapaDe(EtapaInputDto dto) {
        EtapaTreino etapa = new EtapaTreino();
        etapa.setTipoEtapa(dto.tipoEtapa());
        etapa.setDescricaoEtapa(dto.descricaoEtapa());
        etapa.setDuracaoMin(dto.duracaoMin());
        etapa.setFcAlvoEtapa(dto.fcAlvoEtapa());
        etapa.setRitmoAlvo(dto.ritmoAlvo());
        return etapa;
    }

    private TreinoPlanejadoOutputDto outputStub() {
        return new TreinoPlanejadoOutputDto(
                UUID.randomUUID(), null, null, null, TipoTreino.CONTINUO, null, null, null, null,
                null, null, null, null, null, null, null, null, null, false,
                false, null, TreinoExecucaoStatus.PENDENTE, null, null, null, null,
                null, null
        );
    }

    private TreinoPlanejadoOutputDto outputStub(UUID id, boolean editadoPeloCoach) {
        return new TreinoPlanejadoOutputDto(
                id, null, null, null, TipoTreino.CONTINUO, null, null, "60:00", 10.0,
                null, null, null, null, null, null, 55, null, null, editadoPeloCoach,
                false, null, TreinoExecucaoStatus.PENDENTE, null, null, null, null,
                null, null
        );
    }
}
