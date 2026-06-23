package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.EtapaInputDto;
import br.com.menthoros.backend.dto.input.TreinoPlanejadoAddDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreinoPlanejadoAddServiceImplTest {

    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private TssCalculatorService tssCalculatorService;
    @Mock private TreinoMapper treinoMapper;
    @Mock private EtapaMapper etapaMapper;

    @InjectMocks private TreinoPlanejadoAddServiceImpl service;

    private UUID tenantId;
    private UUID planoId;

    // Semana de referência: 2026-07-01 (Qua) a 2026-07-07 (Ter)
    private static final LocalDate SEMANA_INICIO = LocalDate.of(2026, 7, 1);
    private static final LocalDate SEMANA_FIM    = LocalDate.of(2026, 7, 7);
    // 2026-07-03 = Sexta-feira, dentro da semana
    private static final LocalDate DATA_SEXTA    = LocalDate.of(2026, 7, 3);
    // 2026-07-02 = Quinta-feira, dentro da semana
    private static final LocalDate DATA_QUINTA   = LocalDate.of(2026, 7, 2);

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        planoId = UUID.randomUUID();
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
                    new EtapaInputDto("AQUECIMENTO", null, 10, null, null, null, null, null),
                    new EtapaInputDto("PRINCIPAL",   null, 60, null, null, null, null, null)
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

            // 2026-07-02 = quinta-feira
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
                    new EtapaInputDto("INTERVALADO", null, 3, null, null, null, null, null),
                    new EtapaInputDto("RECUPERACAO",  null, 1, null, null, null, null, null)
            );
            TreinoPlanejadoAddDto dto = new TreinoPlanejadoAddDto(
                    "INTERVALADO", DATA_SEXTA, null, null, null, null, null, null, null,
                    List.of(new EtapaInputDto("BLOCO", null, null, null, null, null, 3, subs))
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
                    List.of(new EtapaInputDto("BLOCO", null, null, null, null, null, null,
                            List.of(new EtapaInputDto("PRINCIPAL", null, 30, null, null, null, null, null))))
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
                    List.of(new EtapaInputDto("BLOCO", null, null, null, null, null, 3, List.of()))
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

            // [AQUECIMENTO, BLOCO(2 reps × 2 sub-etapas), DESAQUECIMENTO] → 6 etapas no total
            List<EtapaInputDto> subs = List.of(
                    new EtapaInputDto("INTERVALADO",  null, 3, null, null, null, null, null),
                    new EtapaInputDto("RECUPERACAO",  null, 1, null, null, null, null, null)
            );
            TreinoPlanejadoAddDto dto = new TreinoPlanejadoAddDto(
                    "INTERVALADO", DATA_SEXTA, null, null, null, null, null, null, null,
                    List.of(
                            new EtapaInputDto("AQUECIMENTO",    null, 10, null, null, null, null, null),
                            new EtapaInputDto("BLOCO", null, null, null, null, null, 2, subs),
                            new EtapaInputDto("DESAQUECIMENTO", null, 10, null, null, null, null, null)
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
            for (int i = 1; i <= 4; i++) assertThat(etapas.get(i).getBlocoId()).isEqualTo(blocoId);
        }
    }

    // ===== HELPERS =====

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

    private TreinoPlanejadoOutputDto outputStub() {
        return new TreinoPlanejadoOutputDto(
                UUID.randomUUID(), null, null, null, TipoTreino.CONTINUO, null, null, null, null,
                null, null, null, null, null, null, null, null, null, false,
                false, null, TreinoExecucaoStatus.PENDENTE, null, null, null, null
        );
    }
}
