package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.AthleteZonesManualInputDto;
import br.com.menthoros.backend.dto.input.AthleteZonesManualInputDto.Eixo;
import br.com.menthoros.backend.dto.input.AthleteZonesManualInputDto.Faixa;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.FaixaZonaManual;
import br.com.menthoros.backend.enums.ModoZonas;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.InvalidZoneAnchorsException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.services.helper.AthleteZoneAnchorsValidator;
import br.com.menthoros.backend.services.helper.AthleteZoneManualValidator;
import br.com.menthoros.backend.services.helper.AtletaHojeResolver;
import br.com.menthoros.backend.services.helper.IntervalsIcuFcSyncer;
import br.com.menthoros.backend.services.helper.PaceZoneCalculator;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** coach-zonas-manuais, D3: escrita por eixo e o GET com modo e faixa efetiva. */
@ExtendWith(MockitoExtension.class)
@DisplayName("CoachAthleteZonesServiceImpl — zonas manuais")
class CoachAthleteZonesServiceImplManuaisTest {

    private static final List<FaixaZonaManual> PACE = List.of(
            new FaixaZonaManual(1, 350, 380), new FaixaZonaManual(2, 320, 350),
            new FaixaZonaManual(3, 300, 315), new FaixaZonaManual(4, 285, 300),
            new FaixaZonaManual(5, 270, 285), new FaixaZonaManual(6, 240, 270));

    private static final List<FaixaZonaManual> FC = List.of(
            new FaixaZonaManual(1, 120, 140), new FaixaZonaManual(2, 140, 150),
            new FaixaZonaManual(3, 153, 160), new FaixaZonaManual(4, 160, 168),
            new FaixaZonaManual(5, 168, 178));

    @Mock private AtletaRepository atletaRepository;
    @Mock private PlanoMetadadosRepository planoMetadadosRepository;
    @Mock private IntervalsIcuFcSyncer intervalsIcuFcSyncer;

    private CoachAthleteZonesServiceImpl service;
    private UUID tenantId;
    private UUID atletaId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        ZonaTreinoService zonaTreinoService = new ZonaTreinoService();
        AtletaHojeResolver hoje = new AtletaHojeResolver(
                Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneOffset.UTC));
        service = new CoachAthleteZonesServiceImpl(atletaRepository, planoMetadadosRepository,
                zonaTreinoService, new PaceZoneCalculator(zonaTreinoService),
                new AthleteZoneAnchorsValidator(Validation.buildDefaultValidatorFactory().getValidator(), hoje),
                new AthleteZoneManualValidator(), intervalsIcuFcSyncer);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Atleta atleta(Atleta atleta) {
        atleta.setId(atletaId);
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        return atleta;
    }

    private static List<Faixa> faixas(List<FaixaZonaManual> f) {
        return f.stream().map(x -> new Faixa(x.zona(), x.min(), x.max())).toList();
    }

    @Nested
    @DisplayName("GET com modo")
    class Leitura {

        @Test
        @DisplayName("CA5: modos por eixo; pace manual dá faixa efetiva e a do limiar para Recalcular")
        void paceManual() {
            atleta(Atleta.builder().paceLimiar(new BigDecimal("5.00")).fcLimiar(168).fcMaxima(186)
                    .modoZonasPace(ModoZonas.MANUAL).zonasManuaisPace(PACE).build());

            AthleteTrainingZonesOutputDto dto = service.buscarZonas(atletaId);

            assertThat(dto.modos().fc()).isEqualTo(ModoZonas.LIMIAR);
            assertThat(dto.modos().pace()).isEqualTo(ModoZonas.MANUAL);
            var z2 = dto.zonas().get(1);
            assertThat(z2.pace().minSegKm()).isEqualTo(320);
            assertThat(z2.pace().maxSegKm()).isEqualTo(350);
            assertThat(z2.pacePeloLimiar().minSegKm()).isEqualTo(315);
            assertThat(z2.pacePeloLimiar().maxSegKm()).isEqualTo(345);
            assertThat(z2.fcPeloLimiar()).isNull();
            assertThat(z2.fc().min()).isEqualTo(143);
        }

        @Test
        @DisplayName("pace manual sem paceLimiar: disponível, sem faixa pelo limiar")
        void paceManualSemLimiar() {
            atleta(Atleta.builder().fcLimiar(168).fcMaxima(186)
                    .modoZonasPace(ModoZonas.MANUAL).zonasManuaisPace(PACE).build());

            AthleteTrainingZonesOutputDto dto = service.buscarZonas(atletaId);

            assertThat(dto.disponibilidade().pace()).isTrue();
            assertThat(dto.disponibilidade().motivoPace()).isNull();
            assertThat(dto.zonas().get(1).pace().minSegKm()).isEqualTo(320);
            assertThat(dto.zonas().get(1).pacePeloLimiar()).isNull();
            assertThat(dto.ancoras().paceLimiarMinKm()).isNull();
        }

        @Test
        @DisplayName("FC manual: faixa efetiva, a do limiar ao lado e Z6 continua sem FC")
        void fcManual() {
            atleta(Atleta.builder().fcLimiar(168).fcMaxima(186)
                    .modoZonasFc(ModoZonas.MANUAL).zonasManuaisFc(FC).build());

            AthleteTrainingZonesOutputDto dto = service.buscarZonas(atletaId);

            assertThat(dto.zonas().get(1).fc().min()).isEqualTo(140);
            assertThat(dto.zonas().get(1).fcPeloLimiar().min()).isEqualTo(143);
            assertThat(dto.zonas().get(5).fc()).isNull();
            assertThat(dto.zonas().get(5).fcPeloLimiar()).isNull();
        }

        @Test
        @DisplayName("LIMIAR nos dois eixos: sem campos 'pelo limiar'")
        void limiar() {
            atleta(Atleta.builder().paceLimiar(new BigDecimal("5.00")).fcLimiar(168).build());

            AthleteTrainingZonesOutputDto dto = service.buscarZonas(atletaId);

            assertThat(dto.modos().fc()).isEqualTo(ModoZonas.LIMIAR);
            assertThat(dto.zonas()).allSatisfy(z -> {
                assertThat(z.pacePeloLimiar()).isNull();
                assertThat(z.fcPeloLimiar()).isNull();
            });
        }
    }

    @Nested
    @DisplayName("PUT .../zonas/manuais")
    class Escrita {

        @Test
        @DisplayName("grava só o eixo enviado, com as faixas convertidas")
        void gravaEixoEnviado() {
            atleta(Atleta.builder().fcLimiar(168).build());
            when(atletaRepository.atualizarZonasPace(eq(atletaId), eq(tenantId), eq(ModoZonas.MANUAL), any()))
                    .thenReturn(1);

            service.salvarZonasManuais(atletaId,
                    new AthleteZonesManualInputDto(null, new Eixo(ModoZonas.MANUAL, faixas(PACE))));

            verify(atletaRepository).atualizarZonasPace(atletaId, tenantId, ModoZonas.MANUAL, PACE);
            verify(atletaRepository, never()).atualizarZonasFc(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Q1: LIMIAR apaga as faixas do eixo")
        void limiarApaga() {
            atleta(Atleta.builder().fcLimiar(168).build());
            when(atletaRepository.atualizarZonasFc(eq(atletaId), eq(tenantId), eq(ModoZonas.LIMIAR), isNull()))
                    .thenReturn(1);

            service.salvarZonasManuais(atletaId,
                    new AthleteZonesManualInputDto(new Eixo(ModoZonas.LIMIAR, faixas(FC)), null));

            verify(atletaRepository).atualizarZonasFc(atletaId, tenantId, ModoZonas.LIMIAR, null);
        }

        @Test
        @DisplayName("faixa inválida: 400 sem gravar nada")
        void invalidaNaoGrava() {
            atleta(Atleta.builder().fcLimiar(168).build());

            assertThatThrownBy(() -> service.salvarZonasManuais(atletaId,
                    new AthleteZonesManualInputDto(new Eixo(ModoZonas.MANUAL, List.of()), null)))
                    .isInstanceOf(InvalidZoneAnchorsException.class);
            verify(atletaRepository, never()).atualizarZonasFc(any(), any(), any(), any());
        }

        @Test
        @DisplayName("atleta de outro tenant: 404 sem gravar")
        void outroTenant() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.salvarZonasManuais(atletaId,
                    new AthleteZonesManualInputDto(null, new Eixo(ModoZonas.LIMIAR, null))))
                    .isInstanceOf(DomainNotFoundException.class);
            verifyNoInteractions(intervalsIcuFcSyncer);
        }
    }
}
