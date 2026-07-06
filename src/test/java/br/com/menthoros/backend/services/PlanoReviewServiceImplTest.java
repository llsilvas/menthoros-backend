package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.services.impl.PlanoReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanoReviewServiceImplTest {

    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private PlanoSemanalMapper planoSemanalMapper;
    @InjectMocks private PlanoReviewServiceImpl service;

    private UUID tenantId;
    private UUID planoId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        planoId = UUID.randomUUID();
    }

    // =========================================================================
    // listarPlanosPendentes
    // =========================================================================

    @Nested
    @DisplayName("listarPlanosPendentes")
    class ListarPlanosPendentes {

        @Test
        @DisplayName("retorna lista mapeada de planos AGUARDANDO_REVISAO do tenant")
        void retornaListaMapeada() {
            PlanoSemanal plano = planoAguardando();
            PlanoSemanalOutputDto dto = outputDto(PlanoReviewStatus.AGUARDANDO_REVISAO);

            when(planoSemanalRepository.findByAssessoriaIdAndReviewStatusOrderBySemanaInicioAsc(
                    eq(tenantId), eq(PlanoReviewStatus.AGUARDANDO_REVISAO), any(LocalDate.class)))
                    .thenReturn(List.of(plano));
            when(planoSemanalMapper.toOutputDtoSafe(plano)).thenReturn(dto);

            List<PlanoSemanalOutputDto> resultado = service.listarPlanosPendentes(tenantId);

            assertThat(resultado).containsExactly(dto);
        }

        @Test
        @DisplayName("retorna lista vazia quando não há planos pendentes")
        void retornaVazioSemPendentes() {
            when(planoSemanalRepository.findByAssessoriaIdAndReviewStatusOrderBySemanaInicioAsc(
                    eq(tenantId), eq(PlanoReviewStatus.AGUARDANDO_REVISAO), any(LocalDate.class)))
                    .thenReturn(List.of());

            List<PlanoSemanalOutputDto> resultado = service.listarPlanosPendentes(tenantId);

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando tenantId é nulo")
        void lancaExcecaoParaTenantIdNulo() {
            assertThatThrownBy(() -> service.listarPlanosPendentes(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tenantId");

            verifyNoInteractions(planoSemanalRepository);
        }
    }

    // =========================================================================
    // aprovarPlano
    // =========================================================================

    @Nested
    @DisplayName("aprovarPlano")
    class AprovarPlano {

        @Test
        @DisplayName("happy path: AGUARDANDO_REVISAO → APROVADO; reviewComment zerado")
        void aprovaPlanoPendente() {
            PlanoSemanal plano = planoAguardando();
            PlanoSemanalOutputDto dto = outputDto(PlanoReviewStatus.APROVADO);

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId))
                    .thenReturn(Optional.of(plano));
            when(planoSemanalRepository.save(any())).thenReturn(plano);
            when(planoSemanalMapper.toOutputDtoSafe(plano)).thenReturn(dto);

            PlanoSemanalOutputDto resultado = service.aprovarPlano(planoId, tenantId);

            assertThat(resultado).isEqualTo(dto);

            ArgumentCaptor<PlanoSemanal> captor = ArgumentCaptor.forClass(PlanoSemanal.class);
            verify(planoSemanalRepository).save(captor.capture());
            assertThat(captor.getValue().getReviewStatus()).isEqualTo(PlanoReviewStatus.APROVADO);
            assertThat(captor.getValue().getReviewComment()).isNull();
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando plano não existe no tenant")
        void lancaNotFoundQuandoPlanoAusente() {
            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.aprovarPlano(planoId, tenantId))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(planoSemanalRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança DomainRuleViolationException quando plano já está APROVADO")
        void lancaRuleViolationQuandoJaAprovado() {
            PlanoSemanal plano = planoComStatus(PlanoReviewStatus.APROVADO);
            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId))
                    .thenReturn(Optional.of(plano));

            assertThatThrownBy(() -> service.aprovarPlano(planoId, tenantId))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("Aprovado");

            verify(planoSemanalRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança DomainRuleViolationException quando plano está REJEITADO")
        void lancaRuleViolationQuandoRejeitado() {
            PlanoSemanal plano = planoComStatus(PlanoReviewStatus.REJEITADO);
            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId))
                    .thenReturn(Optional.of(plano));

            assertThatThrownBy(() -> service.aprovarPlano(planoId, tenantId))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("Rejeitado");

            verify(planoSemanalRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança IllegalArgumentException para planoId nulo")
        void lancaExcecaoParaPlanoIdNulo() {
            assertThatThrownBy(() -> service.aprovarPlano(null, tenantId))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(planoSemanalRepository);
        }
    }

    // =========================================================================
    // rejeitarPlano
    // =========================================================================

    @Nested
    @DisplayName("rejeitarPlano")
    class RejeitarPlano {

        @Test
        @DisplayName("happy path: AGUARDANDO_REVISAO → REJEITADO; reviewComment persistido")
        void rejeitaPlanoPendente() {
            PlanoSemanal plano = planoAguardando();
            String motivo = "Volume excessivo para a fase atual";
            PlanoSemanalOutputDto dto = outputDto(PlanoReviewStatus.REJEITADO);

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId))
                    .thenReturn(Optional.of(plano));
            when(planoSemanalRepository.save(any())).thenReturn(plano);
            when(planoSemanalMapper.toOutputDtoSafe(plano)).thenReturn(dto);

            PlanoSemanalOutputDto resultado = service.rejeitarPlano(planoId, tenantId, motivo);

            assertThat(resultado).isEqualTo(dto);

            ArgumentCaptor<PlanoSemanal> captor = ArgumentCaptor.forClass(PlanoSemanal.class);
            verify(planoSemanalRepository).save(captor.capture());
            assertThat(captor.getValue().getReviewStatus()).isEqualTo(PlanoReviewStatus.REJEITADO);
            assertThat(captor.getValue().getReviewComment()).isEqualTo(motivo);
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando plano não existe no tenant (cross-tenant)")
        void lancaNotFoundParaCrossTenant() {
            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.rejeitarPlano(planoId, tenantId, "motivo"))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(planoSemanalRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança DomainRuleViolationException quando plano já está REJEITADO")
        void lancaRuleViolationQuandoJaRejeitado() {
            PlanoSemanal plano = planoComStatus(PlanoReviewStatus.REJEITADO);
            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId))
                    .thenReturn(Optional.of(plano));

            assertThatThrownBy(() -> service.rejeitarPlano(planoId, tenantId, "motivo"))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("Rejeitado");

            verify(planoSemanalRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança DomainRuleViolationException quando plano está APROVADO")
        void lancaRuleViolationQuandoAprovado() {
            PlanoSemanal plano = planoComStatus(PlanoReviewStatus.APROVADO);
            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId))
                    .thenReturn(Optional.of(plano));

            assertThatThrownBy(() -> service.rejeitarPlano(planoId, tenantId, "motivo"))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("Aprovado");

            verify(planoSemanalRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando motivo é nulo")
        void lancaExcecaoParaMotivoNulo() {
            assertThatThrownBy(() -> service.rejeitarPlano(planoId, tenantId, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Motivo");
            verifyNoInteractions(planoSemanalRepository);
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando motivo está em branco")
        void lancaExcecaoParaMotivoEmBranco() {
            assertThatThrownBy(() -> service.rejeitarPlano(planoId, tenantId, "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Motivo");
            verifyNoInteractions(planoSemanalRepository);
        }
    }

    // =========================================================================
    // listarPlanosPorStatus
    // =========================================================================

    @Nested
    @DisplayName("listarPlanosPorStatus")
    class ListarPlanosPorStatus {

        @Test
        @DisplayName("retorna planos APROVADOS do tenant")
        void retornaAprovados() {
            PlanoSemanal plano = planoComStatus(PlanoReviewStatus.APROVADO);
            PlanoSemanalOutputDto dto = outputDto(PlanoReviewStatus.APROVADO);

            when(planoSemanalRepository.findByAssessoriaIdAndReviewStatusOrderBySemanaInicioAsc(
                    eq(tenantId), eq(PlanoReviewStatus.APROVADO), any(LocalDate.class)))
                    .thenReturn(List.of(plano));
            when(planoSemanalMapper.toOutputDtoSafe(plano)).thenReturn(dto);

            List<PlanoSemanalOutputDto> resultado = service.listarPlanosPorStatus(tenantId, PlanoReviewStatus.APROVADO);

            assertThat(resultado).containsExactly(dto);
            verify(planoSemanalRepository)
                    .findByAssessoriaIdAndReviewStatusOrderBySemanaInicioAsc(
                            eq(tenantId), eq(PlanoReviewStatus.APROVADO), any(LocalDate.class));
        }

        @Test
        @DisplayName("retorna planos REJEITADOS do tenant")
        void retornaRejeitados() {
            PlanoSemanal plano = planoComStatus(PlanoReviewStatus.REJEITADO);
            PlanoSemanalOutputDto dto = outputDto(PlanoReviewStatus.REJEITADO);

            when(planoSemanalRepository.findByAssessoriaIdAndReviewStatusOrderBySemanaInicioAsc(
                    eq(tenantId), eq(PlanoReviewStatus.REJEITADO), any(LocalDate.class)))
                    .thenReturn(List.of(plano));
            when(planoSemanalMapper.toOutputDtoSafe(plano)).thenReturn(dto);

            List<PlanoSemanalOutputDto> resultado = service.listarPlanosPorStatus(tenantId, PlanoReviewStatus.REJEITADO);

            assertThat(resultado).containsExactly(dto);
        }

        @Test
        @DisplayName("retorna lista vazia quando não há planos com o status")
        void retornaVazioSemPlanos() {
            when(planoSemanalRepository.findByAssessoriaIdAndReviewStatusOrderBySemanaInicioAsc(
                    eq(tenantId), eq(PlanoReviewStatus.APROVADO), any(LocalDate.class)))
                    .thenReturn(List.of());

            List<PlanoSemanalOutputDto> resultado = service.listarPlanosPorStatus(tenantId, PlanoReviewStatus.APROVADO);

            assertThat(resultado).isEmpty();
            verifyNoMoreInteractions(planoSemanalMapper);
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando tenantId é nulo")
        void lancaExcecaoParaTenantIdNulo() {
            assertThatThrownBy(() -> service.listarPlanosPorStatus(null, PlanoReviewStatus.APROVADO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tenantId");
            verifyNoInteractions(planoSemanalRepository);
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando reviewStatus é nulo")
        void lancaExcecaoParaStatusNulo() {
            assertThatThrownBy(() -> service.listarPlanosPorStatus(tenantId, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reviewStatus");
            verifyNoInteractions(planoSemanalRepository);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private PlanoSemanal planoAguardando() {
        return planoComStatus(PlanoReviewStatus.AGUARDANDO_REVISAO);
    }

    private PlanoSemanal planoComStatus(PlanoReviewStatus reviewStatus) {
        return PlanoSemanal.builder()
                .id(planoId)
                .status(PlanoStatus.PLANEJADO)
                .reviewStatus(reviewStatus)
                .semanaInicio(LocalDate.now())
                .semanaFim(LocalDate.now().plusDays(6))
                .build();
    }

    private PlanoSemanalOutputDto outputDto(PlanoReviewStatus reviewStatus) {
        return new PlanoSemanalOutputDto(
                planoId.toString(), LocalDate.now(), LocalDate.now().plusDays(6),
                40.0, 0.0, 40.0, null, null, PlanoStatus.PLANEJADO,
                null, "Semana base", List.of(), reviewStatus, null, null, "Ana Silva");
    }
}
