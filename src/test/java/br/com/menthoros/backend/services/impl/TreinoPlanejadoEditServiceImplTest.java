package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.EtapaInputDto;
import br.com.menthoros.backend.dto.input.TreinoPlanejadoPatchDto;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.DiaSemana;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreinoPlanejadoEditServiceImplTest {

    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private TssCalculatorService tssCalculatorService;
    @Mock private TreinoMapper treinoMapper;
    @Mock private EtapaMapper etapaMapper;

    @InjectMocks private TreinoPlanejadoEditServiceImpl editService;

    private UUID tenantId;
    private UUID planoId;
    private UUID treinoId;

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
    @DisplayName("editarTreino")
    class EditarTreino {

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

            TreinoPlanejadoOutputDto result = editService.editarTreino(planoId, treinoId, patch);

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

            // Patch com apenas distanciaKm — não toca tipoTreino nem zonaAlvo
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, BigDecimal.valueOf(15.0), null, null, null, null, null, null
            );

            editService.editarTreino(planoId, treinoId, patch);

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
            when(tssCalculatorService.calcularTssEstimado(Duration.ofMinutes(90), 7)).thenReturn(49);
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, Duration.ofMinutes(90), null, null, null, null, null
            );

            editService.editarTreino(planoId, treinoId, patch);

            ArgumentCaptor<TreinoPlanejado> captor = ArgumentCaptor.forClass(TreinoPlanejado.class);
            verify(treinoPlanejadoRepository).save(captor.capture());
            assertThat(captor.getValue().getTssPlanejado()).isEqualTo(49);
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

            editService.editarTreino(planoId, treinoId, patch);

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

            // Patch muda apenas zonaAlvo — volume não muda
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, "z3", null, null, null, null
            );

            editService.editarTreino(planoId, treinoId, patch);

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

            assertThatThrownBy(() -> editService.editarTreino(planoId, treinoId, patch))
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

            assertThatThrownBy(() -> editService.editarTreino(planoId, treinoId, patch))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança DomainNotFoundException se treino não existe no tenant ou não pertence ao plano")
        void lancaExcecaoSeTreinoNaoEncontrado() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            // Query atômica cobre tanto "não existe" quanto "pertence a outro plano"
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.empty());

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, null
            );

            assertThatThrownBy(() -> editService.editarTreino(planoId, treinoId, patch))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando planoId é nulo")
        void lancaExcecaoParaPlanoIdNulo() {
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, null
            );
            assertThatThrownBy(() -> editService.editarTreino(null, treinoId, patch))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando treinoId é nulo")
        void lancaExcecaoParaTreinoIdNulo() {
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, null
            );
            assertThatThrownBy(() -> editService.editarTreino(planoId, null, patch))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando patch é nulo")
        void lancaExcecaoParaPatchNulo() {
            assertThatThrownBy(() -> editService.editarTreino(planoId, treinoId, null))
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

            editService.editarTreino(planoId, treinoId, patch);

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

            EtapaInputDto subEtapa = new EtapaInputDto("INTERVALADO", null, 5, null, null, null, null, null);
            EtapaInputDto bloco   = new EtapaInputDto("BLOCO", null, null, null, null, null, 2, List.of(subEtapa));
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null, List.of(bloco)
            );

            EtapaTreino etapaEntity = new EtapaTreino();
            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndPlanoSemanalIdAndTenantId(treinoId, planoId, tenantId))
                    .thenReturn(Optional.of(treino));
            when(etapaMapper.toEntity(any(EtapaInputDto.class))).thenReturn(etapaEntity);
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            try (MockedStatic<Hibernate> ignored = mockStatic(Hibernate.class)) {
                editService.editarTreino(planoId, treinoId, patch);
            }

            ArgumentCaptor<EtapaInputDto> captor = ArgumentCaptor.forClass(EtapaInputDto.class);
            verify(etapaMapper, times(2)).toEntity(captor.capture());
            assertThat(captor.getAllValues()).allSatisfy(e -> {
                assertThat(e.tipoEtapa()).isEqualTo("INTERVALADO");
                assertThat(e.blocoRepeticoes()).isEqualTo(2);
            });
        }

        @Test
        @DisplayName("BLOCO com 3 reps e 2 sub-etapas gera 6 etapas no total")
        void expandeBlocoComMultiplasSubEtapas() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);

            List<EtapaInputDto> subEtapas = List.of(
                    new EtapaInputDto("INTERVALADO", null, 3, null, null, null, null, null),
                    new EtapaInputDto("RECUPERACAO", null, 2, null, null, null, null, null)
            );
            EtapaInputDto bloco = new EtapaInputDto("BLOCO", null, null, null, null, null, 3, subEtapas);
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
                editService.editarTreino(planoId, treinoId, patch);
            }

            verify(etapaMapper, times(6)).toEntity(any(EtapaInputDto.class));
        }

        @Test
        @DisplayName("comRepeticoes preserva blocoRepeticoes ao expandir INTERVALADO")
        void comRepeticoesPreservaBlocoRepeticoes() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treino = criarTreino(plano);

            EtapaInputDto intervalado = new EtapaInputDto("INTERVALADO", null, 4, null, null, 3, 3, null);
            EtapaInputDto recuperacao = new EtapaInputDto("RECUPERACAO", null, 2, null, null, null, null, null);
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
                editService.editarTreino(planoId, treinoId, patch);
            }

            // INTERVALADO(reps=3) + RECUPERACAO → 6 etapas (3 pares INT+REC)
            ArgumentCaptor<EtapaInputDto> captor = ArgumentCaptor.forClass(EtapaInputDto.class);
            verify(etapaMapper, times(6)).toEntity(captor.capture());
            // Todas as etapas de INTERVALADO devem ter blocoRepeticoes=3 preservado
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

            EtapaInputDto intervalado = new EtapaInputDto("INTERVALADO", null, 4, null, null, 3, null, null);
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
                editService.editarTreino(planoId, treinoId, patch);
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

            EtapaInputDto intervalado = new EtapaInputDto("INTERVALADO", null, 4, null, null, 1, null, null);
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
                editService.editarTreino(planoId, treinoId, patch);
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
                editService.editarTreino(planoId, treinoId, patch);
            }

            verify(etapaMapper, never()).toEntity(any(EtapaInputDto.class));
        }
    }

    // ===== HELPERS =====

    private PlanoSemanal criarPlano(PlanoReviewStatus reviewStatus) {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setId(planoId); // mesmo id usado no stub do repositório
        plano.setReviewStatus(reviewStatus);
        return plano;
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

    private TreinoPlanejadoOutputDto outputStub(UUID id, boolean editadoPeloCoach) {
        return new TreinoPlanejadoOutputDto(
                id, null, null, null, TipoTreino.CONTINUO, null, null, "60:00", 10.0,
                null, null, null, null, null, null, 55, null, null, editadoPeloCoach,
                false, null, TreinoExecucaoStatus.PENDENTE, null, null, null, null
        );
    }
}
