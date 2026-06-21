package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.TreinoPlanejadoPatchDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreinoPlanejadoEditServiceImplTest {

    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private TssCalculatorService tssCalculatorService;
    @Mock private TreinoMapper treinoMapper;

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
            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputEsperado);

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, BigDecimal.valueOf(18.0), null, null, null, null, "Reduzido após prova"
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
            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            // Patch com apenas distanciaKm — não toca tipoTreino nem zonaAlvo
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, BigDecimal.valueOf(15.0), null, null, null, null, null
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
            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));
            when(tssCalculatorService.calcularTssEstimado(Duration.ofMinutes(90), 7)).thenReturn(49);
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, Duration.ofMinutes(90), null, null, null, null
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
            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, Duration.ofMinutes(90), null, 65, null, null
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
            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));
            when(treinoPlanejadoRepository.save(any())).thenReturn(treino);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(treinoId, true));

            // Patch muda apenas zonaAlvo — volume não muda
            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, "z3", null, null, null
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
                    null, null, null, null, null, null, null, null
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
                    null, null, null, null, null, null, null, null
            );

            assertThatThrownBy(() -> editService.editarTreino(planoId, treinoId, patch))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança DomainNotFoundException se treino não pertence ao plano")
        void lancaExcecaoSeTreinoNaoPertenceAoPlano() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treinoDeOutroPlano = criarTreino(criarPlanoComOutroId(PlanoReviewStatus.AGUARDANDO_REVISAO));

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treinoDeOutroPlano));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null
            );

            assertThatThrownBy(() -> editService.editarTreino(planoId, treinoId, patch))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança DomainNotFoundException se treinoId pertence ao tenant mas a planoId diferente (intra-tenant cross-plan)")
        void lancaExcecaoSeTreinoIntraTenantCrossPlano() {
            PlanoSemanal planoAlvo = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);
            PlanoSemanal outroPlano = criarPlanoComOutroId(PlanoReviewStatus.AGUARDANDO_REVISAO);
            TreinoPlanejado treinoDeOutroPlano = criarTreino(outroPlano);

            // Treino existe e pertence ao tenant, mas está vinculado a outroPlano (não ao planoAlvo)
            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(planoAlvo));
            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treinoDeOutroPlano));

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null
            );

            assertThatThrownBy(() -> editService.editarTreino(planoId, treinoId, patch))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança DomainNotFoundException se treino não existe no tenant")
        void lancaExcecaoSeTreinoNaoExisteNoTenant() {
            PlanoSemanal plano = criarPlano(PlanoReviewStatus.AGUARDANDO_REVISAO);

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.empty());

            TreinoPlanejadoPatchDto patch = new TreinoPlanejadoPatchDto(
                    null, null, null, null, null, null, null, null
            );

            assertThatThrownBy(() -> editService.editarTreino(planoId, treinoId, patch))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(treinoPlanejadoRepository, never()).save(any());
        }
    }

    // ===== HELPERS =====

    private PlanoSemanal criarPlano(PlanoReviewStatus reviewStatus) {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setId(planoId); // mesmo id usado no stub do repositório
        plano.setReviewStatus(reviewStatus);
        return plano;
    }

    private PlanoSemanal criarPlanoComOutroId(PlanoReviewStatus reviewStatus) {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setId(UUID.randomUUID()); // id diferente de planoId — testes cross-plano
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
                null, TreinoExecucaoStatus.PENDENTE, null, null, null, null
        );
    }
}
