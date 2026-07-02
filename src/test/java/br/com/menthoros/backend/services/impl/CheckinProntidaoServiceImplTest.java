package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.CheckinProntidaoInputDto;
import br.com.menthoros.backend.dto.output.CheckinProntidaoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.CheckinProntidao;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.enums.NivelProntidao;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.CheckinProntidaoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.CheckinProntidaoRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.services.helper.ReadinessService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckinProntidaoServiceImplTest {

    @Mock private CheckinProntidaoRepository checkinProntidaoRepository;
    @Mock private MetricasDiariasRepository metricasDiariasRepository;
    @Mock private AtletaRepository atletaRepository;
    @Mock private ReadinessService readinessService;
    @Mock private CheckinProntidaoMapper mapper;

    @InjectMocks private CheckinProntidaoServiceImpl checkinService;

    private UUID tenantId;
    private UUID atletaId;
    private Atleta atleta;
    private CheckinProntidaoInputDto inputDto;
    private CheckinProntidaoOutputDto outputDto;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        atleta = new Atleta();
        atleta.setId(atletaId);

        inputDto = new CheckinProntidaoInputDto(
                LocalDate.of(2026, 7, 2), 8, 7, 2, 6, 3, "Dormi bem");
        outputDto = new CheckinProntidaoOutputDto(
                UUID.randomUUID(), atletaId, LocalDate.of(2026, 7, 2),
                8, 7, 2, 6, 3, "Dormi bem", new BigDecimal("0.720"), NivelProntidao.CAUTELOSO);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("registrarCheckin")
    class RegistrarCheckin {

        @Test
        @DisplayName("cria novo checkin quando não existe para a data")
        void criaNovoQuandoNaoExiste() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(checkinProntidaoRepository.findByAtletaIdAndData(atletaId, inputDto.data(), tenantId))
                    .thenReturn(Optional.empty());
            when(readinessService.calcularScore(any())).thenReturn(new BigDecimal("0.720"));
            when(readinessService.classificarNivel(new BigDecimal("0.720"))).thenReturn(NivelProntidao.CAUTELOSO);
            when(checkinProntidaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(metricasDiariasRepository.findByAtletaIdAndData(atletaId, inputDto.data()))
                    .thenReturn(Optional.empty());
            when(mapper.toOutputDto(any())).thenReturn(outputDto);

            CheckinProntidaoOutputDto result = checkinService.registrarCheckin(atletaId, inputDto);

            assertThat(result).isEqualTo(outputDto);
            ArgumentCaptor<CheckinProntidao> captor = ArgumentCaptor.forClass(CheckinProntidao.class);
            verify(checkinProntidaoRepository).save(captor.capture());
            assertThat(captor.getValue().getReadinessScore()).isEqualByComparingTo("0.720");
            assertThat(captor.getValue().getNivelProntidao()).isEqualTo(NivelProntidao.CAUTELOSO);
            verify(metricasDiariasRepository, never()).save(any());
        }

        @Test
        @DisplayName("atualiza checkin existente em vez de duplicar (idempotência por data)")
        void atualizaQuandoJaExisteParaData() {
            CheckinProntidao existente = CheckinProntidao.builder()
                    .atleta(atleta).data(inputDto.data()).tenantId(tenantId)
                    .qualidadeSono(3).humor(3).doresMusculares(8).nivelEnergia(3).estresse(8)
                    .build();

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(checkinProntidaoRepository.findByAtletaIdAndData(atletaId, inputDto.data(), tenantId))
                    .thenReturn(Optional.of(existente));
            when(readinessService.calcularScore(any())).thenReturn(new BigDecimal("0.720"));
            when(readinessService.classificarNivel(any())).thenReturn(NivelProntidao.CAUTELOSO);
            when(checkinProntidaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(metricasDiariasRepository.findByAtletaIdAndData(atletaId, inputDto.data()))
                    .thenReturn(Optional.empty());
            when(mapper.toOutputDto(any())).thenReturn(outputDto);

            checkinService.registrarCheckin(atletaId, inputDto);

            verify(checkinProntidaoRepository, never()).save(argThat(c -> c != existente));
            ArgumentCaptor<CheckinProntidao> captor = ArgumentCaptor.forClass(CheckinProntidao.class);
            verify(checkinProntidaoRepository).save(captor.capture());
            assertThat(captor.getValue().getQualidadeSono()).isEqualTo(8);
        }

        @Test
        @DisplayName("propaga readiness para MetricasDiarias quando a linha do dia já existe")
        void propagaParaMetricasDiariasQuandoExiste() {
            MetricasDiarias metricas = new MetricasDiarias();

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(checkinProntidaoRepository.findByAtletaIdAndData(atletaId, inputDto.data(), tenantId))
                    .thenReturn(Optional.empty());
            when(readinessService.calcularScore(any())).thenReturn(new BigDecimal("0.720"));
            when(readinessService.classificarNivel(any())).thenReturn(NivelProntidao.CAUTELOSO);
            when(checkinProntidaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(metricasDiariasRepository.findByAtletaIdAndData(atletaId, inputDto.data()))
                    .thenReturn(Optional.of(metricas));
            when(mapper.toOutputDto(any())).thenReturn(outputDto);

            checkinService.registrarCheckin(atletaId, inputDto);

            ArgumentCaptor<MetricasDiarias> captor = ArgumentCaptor.forClass(MetricasDiarias.class);
            verify(metricasDiariasRepository).save(captor.capture());
            assertThat(captor.getValue().getReadinessScore()).isEqualByComparingTo("0.720");
            assertThat(captor.getValue().getNivelProntidao()).isEqualTo(NivelProntidao.CAUTELOSO);
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando atleta não pertence ao tenant")
        void lancaExcecaoQuandoAtletaDeOutroTenant() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> checkinService.registrarCheckin(atletaId, inputDto))
                    .isInstanceOf(DomainNotFoundException.class)
                    .hasMessageContaining(atletaId.toString());

            verify(checkinProntidaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando dto é null")
        void lancaExcecaoQuandoDtoNull() {
            assertThatThrownBy(() -> checkinService.registrarCheckin(atletaId, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("buscarAtual")
    class BuscarAtual {

        @Test
        @DisplayName("retorna o checkin mais recente quando existe")
        void retornaCheckinQuandoExiste() {
            CheckinProntidao entity = CheckinProntidao.builder().atleta(atleta).build();
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(checkinProntidaoRepository.findTopByAtletaIdOrderByDataDesc(atletaId, tenantId))
                    .thenReturn(Optional.of(entity));
            when(mapper.toOutputDto(entity)).thenReturn(outputDto);

            CheckinProntidaoOutputDto result = checkinService.buscarAtual(atletaId);

            assertThat(result).isEqualTo(outputDto);
        }

        @Test
        @DisplayName("retorna null quando não há checkin registrado")
        void retornaNullQuandoNaoExiste() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(checkinProntidaoRepository.findTopByAtletaIdOrderByDataDesc(atletaId, tenantId))
                    .thenReturn(Optional.empty());

            CheckinProntidaoOutputDto result = checkinService.buscarAtual(atletaId);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("buscarHistorico")
    class BuscarHistorico {

        @Test
        @DisplayName("usa o valor de dias informado dentro do limite")
        void respeitaDiasInformado() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(checkinProntidaoRepository.findByAtletaIdAndDataBetween(any(), any(), any(), any()))
                    .thenReturn(List.of());

            checkinService.buscarHistorico(atletaId, 14);

            verify(checkinProntidaoRepository).findByAtletaIdAndDataBetween(
                    eq(atletaId), eq(LocalDate.now().minusDays(13)), eq(LocalDate.now()), eq(tenantId));
        }

        @Test
        @DisplayName("limita dias a 90 quando valor informado excede o máximo")
        void limitaA90QuandoExcede() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(checkinProntidaoRepository.findByAtletaIdAndDataBetween(any(), any(), any(), any()))
                    .thenReturn(List.of());

            checkinService.buscarHistorico(atletaId, 500);

            verify(checkinProntidaoRepository).findByAtletaIdAndDataBetween(
                    eq(atletaId), eq(LocalDate.now().minusDays(89)), eq(LocalDate.now()), eq(tenantId));
        }

        @Test
        @DisplayName("aplica mínimo de 1 dia quando valor informado é zero ou negativo")
        void aplicaMinimoDeUmDia() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(checkinProntidaoRepository.findByAtletaIdAndDataBetween(any(), any(), any(), any()))
                    .thenReturn(List.of());

            checkinService.buscarHistorico(atletaId, -5);

            verify(checkinProntidaoRepository).findByAtletaIdAndDataBetween(
                    eq(atletaId), eq(LocalDate.now()), eq(LocalDate.now()), eq(tenantId));
        }
    }
}
