package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto.ZonaDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.FonteLimiar;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.dto.input.AthleteZoneAnchorsInputDto;
import br.com.menthoros.backend.exception.InvalidZoneAnchorsException;
import br.com.menthoros.backend.services.helper.AthleteZoneAnchorsValidator;
import br.com.menthoros.backend.services.helper.AtletaHojeResolver;
import br.com.menthoros.backend.services.helper.IntervalsIcuFcSyncer;
import br.com.menthoros.backend.services.helper.PaceZoneCalculator;
import jakarta.validation.Validation;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CoachAthleteZonesServiceImpl")
class CoachAthleteZonesServiceImplTest {

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
                Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZoneOffset.UTC));
        service = new CoachAthleteZonesServiceImpl(atletaRepository, planoMetadadosRepository,
                zonaTreinoService, new PaceZoneCalculator(zonaTreinoService),
                new AthleteZoneAnchorsValidator(Validation.buildDefaultValidatorFactory().getValidator(), hoje),
                intervalsIcuFcSyncer);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Atleta atleta(BigDecimal paceLimiar, Integer fcLimiar, Integer fcMaxima) {
        Atleta atleta = Atleta.builder().id(atletaId).paceLimiar(paceLimiar).fcLimiar(fcLimiar).fcMaxima(fcMaxima)
                .dataUltimoTestePace(LocalDate.of(2026, 9, 12)).dataUltimoTesteFc(LocalDate.of(2026, 9, 12))
                .build();
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        return atleta;
    }

    private void tsbProntidao(Double tsb) {
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setTsbProntidaoAtual(tsb);
        lenient().when(planoMetadadosRepository.findLatestByAtletaIdAndTenantId(atletaId, tenantId))
                .thenReturn(Optional.of(meta));
    }

    private static ZonaDto zona(AthleteTrainingZonesOutputDto dto, int numero) {
        return dto.zonas().get(numero - 1);
    }

    @Nested
    @DisplayName("limiares completos")
    class LimiaresCompletos {

        @Test
        @DisplayName("Z2 em valores absolutos e Z6 sem FC, com motivo")
        void valoresAbsolutos() {
            atleta(new BigDecimal("5.00"), 168, 185);
            tsbProntidao(5.0);

            AthleteTrainingZonesOutputDto dto = service.buscarZonas(atletaId);

            assertThat(dto.zonas()).hasSize(6);
            assertThat(zona(dto, 2).pace().minSegKm()).isEqualTo(315);
            assertThat(zona(dto, 2).pace().maxSegKm()).isEqualTo(345);
            assertThat(zona(dto, 2).fc().min()).isEqualTo(143);
            assertThat(zona(dto, 2).fc().max()).isEqualTo(150);
            assertThat(zona(dto, 6).pace().minSegKm()).isEqualTo(246);
            assertThat(zona(dto, 6).pace().maxSegKm()).isEqualTo(270);
            assertThat(zona(dto, 6).fc()).isNull();
            assertThat(zona(dto, 6).motivoFc()).isEqualTo("FC_NAO_ESTABILIZA");
            assertThat(dto.disponibilidade().pace()).isTrue();
            assertThat(dto.disponibilidade().fc()).isTrue();
            assertThat(dto.ancoras().paceLimiarMinKm()).isEqualByComparingTo("5.00");
            assertThat(dto.ancoras().fcLimiar()).isEqualTo(168);
            assertThat(dto.ancoras().fcLimiarEstimada()).isFalse();
            assertThat(dto.fcMaxima().bpm()).isEqualTo(185);
            assertThat(dto.fcMaxima().estimada()).isFalse();
        }
    }

    @Nested
    @DisplayName("origem do limiar")
    class OrigemDoLimiar {

        @Test
        @DisplayName("expõe fonteLimiarFc e fonteLimiarPace gravadas")
        void expoeFontes() {
            Atleta atleta = atleta(new BigDecimal("5.00"), 168, 185);
            atleta.setFonteLimiarFc(FonteLimiar.TESTE_CAMPO);
            atleta.setFonteLimiarPace(FonteLimiar.PROVA);
            tsbProntidao(0.0);

            AthleteTrainingZonesOutputDto dto = service.buscarZonas(atletaId);

            assertThat(dto.ancoras().fonteLimiarFc()).isEqualTo(FonteLimiar.TESTE_CAMPO);
            assertThat(dto.ancoras().fonteLimiarPace()).isEqualTo(FonteLimiar.PROVA);
        }

        @Test
        @DisplayName("atleta sem origem: ambas nulas")
        void semOrigem() {
            atleta(new BigDecimal("5.00"), 168, 185);
            tsbProntidao(0.0);

            AthleteTrainingZonesOutputDto dto = service.buscarZonas(atletaId);

            assertThat(dto.ancoras().fonteLimiarFc()).isNull();
            assertThat(dto.ancoras().fonteLimiarPace()).isNull();
        }
    }

    @Nested
    @DisplayName("ajuste por fadiga")
    class AjusteFadiga {

        @Test
        @DisplayName("TSB -12: informa 7 s/km e deixa as faixas na base")
        void tsbNegativoNaoSomaNasFaixas() {
            atleta(new BigDecimal("5.00"), 168, 185);
            tsbProntidao(-12.0);

            AthleteTrainingZonesOutputDto dto = service.buscarZonas(atletaId);

            assertThat(dto.ajusteFadiga().segundosPorKm()).isEqualTo(7);
            assertThat(dto.ajusteFadiga().tsbProntidao()).isEqualTo(-12.0);
            assertThat(zona(dto, 2).pace().minSegKm()).isEqualTo(315);
            assertThat(zona(dto, 2).pace().maxSegKm()).isEqualTo(345);
        }

        @Test
        @DisplayName("TSB >= 0: ajuste zero")
        void tsbPositivo() {
            atleta(new BigDecimal("5.00"), 168, 185);
            tsbProntidao(3.0);

            assertThat(service.buscarZonas(atletaId).ajusteFadiga().segundosPorKm()).isZero();
        }

        @Test
        @DisplayName("sem metadados: ajuste zero e TSB ausente")
        void semMetadados() {
            atleta(new BigDecimal("5.00"), 168, 185);
            lenient().when(planoMetadadosRepository.findLatestByAtletaIdAndTenantId(atletaId, tenantId))
                    .thenReturn(Optional.empty());

            AthleteTrainingZonesOutputDto dto = service.buscarZonas(atletaId);

            assertThat(dto.ajusteFadiga().segundosPorKm()).isZero();
            assertThat(dto.ajusteFadiga().tsbProntidao()).isNull();
        }
    }

    @Nested
    @DisplayName("sem pace de limiar")
    class SemPaceLimiar {

        @Test
        @DisplayName("paceLimiar nulo: pace ausente, motivo SEM_PACE_LIMIAR, nunca 0:00 nem 5,83")
        void paceNulo() {
            atleta(null, 168, 185);
            tsbProntidao(-12.0);

            AthleteTrainingZonesOutputDto dto = service.buscarZonas(atletaId);

            assertThat(dto.zonas()).allSatisfy(z -> assertThat(z.pace()).isNull());
            assertThat(dto.disponibilidade().pace()).isFalse();
            assertThat(dto.disponibilidade().motivoPace()).isEqualTo("SEM_PACE_LIMIAR");
            assertThat(dto.ajusteFadiga().segundosPorKm()).isZero();
            assertThat(dto.ancoras().paceLimiarMinKm()).isNull();
            assertThat(zona(dto, 2).fc()).isNotNull();
        }

        @Test
        @DisplayName("paceLimiar zero é tratado como ausente")
        void paceZero() {
            atleta(BigDecimal.ZERO, 168, 185);
            tsbProntidao(0.0);

            AthleteTrainingZonesOutputDto dto = service.buscarZonas(atletaId);

            assertThat(dto.zonas()).allSatisfy(z -> assertThat(z.pace()).isNull());
            assertThat(dto.disponibilidade().motivoPace()).isEqualTo("SEM_PACE_LIMIAR");
        }
    }

    @Nested
    @DisplayName("estimativas de FC")
    class EstimativasFc {

        @Test
        @DisplayName("fcLimiar nulo: zonas pelo fallback e fcLimiarEstimada=true; fcMaxima nula: estimada=true")
        void fcEstimada() {
            atleta(new BigDecimal("5.00"), null, null);
            tsbProntidao(0.0);

            AthleteTrainingZonesOutputDto dto = service.buscarZonas(atletaId);

            assertThat(dto.ancoras().fcLimiarEstimada()).isTrue();
            assertThat(dto.ancoras().fcLimiar()).isPositive();
            assertThat(dto.fcMaxima().estimada()).isTrue();
            assertThat(dto.fcMaxima().bpm()).isPositive();
            assertThat(zona(dto, 2).fc().min()).isPositive();
        }
    }

    @Nested
    @DisplayName("salvarAncoras")
    class SalvarAncoras {

        private final LocalDate teste = LocalDate.of(2026, 10, 12);

        private void semMetadados() {
            lenient().when(planoMetadadosRepository.findLatestByAtletaIdAndTenantId(atletaId, tenantId))
                    .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("só pace: grava pace em minutos decimais (290 s -> 4.83) e não toca a FC")
        void soPace() {
            atleta(null, 168, 185);
            semMetadados();
            when(atletaRepository.atualizarLimiarPace(atletaId, tenantId, new BigDecimal("4.83"), teste,
                    FonteLimiar.TESTE_CAMPO)).thenReturn(1);

            service.salvarAncoras(atletaId, new AthleteZoneAnchorsInputDto(null,
                    new AthleteZoneAnchorsInputDto.Pace(290, teste, FonteLimiar.TESTE_CAMPO)));

            verify(atletaRepository, never()).atualizarLimiarFc(any(), any(), any(), any(), any());
            verify(atletaRepository, never()).atualizarLimiarFcEMaxima(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("só FC sem máxima: grava limiar sem tocar fc_maxima nem o pace")
        void fcSemMaxima() {
            atleta(new BigDecimal("5.00"), null, null);
            semMetadados();
            when(atletaRepository.atualizarLimiarFc(atletaId, tenantId, 168, teste, FonteLimiar.PROVA)).thenReturn(1);

            service.salvarAncoras(atletaId, new AthleteZoneAnchorsInputDto(
                    new AthleteZoneAnchorsInputDto.Fc(168, null, teste, FonteLimiar.PROVA), null));

            verify(atletaRepository, never()).atualizarLimiarFcEMaxima(any(), any(), any(), any(), any(), any());
            verify(atletaRepository, never()).atualizarLimiarPace(any(), any(), any(), any(), any());
            verify(intervalsIcuFcSyncer).sincronizarSeConectado(any(Atleta.class), eq(tenantId));
        }

        @Test
        @DisplayName("só pace: não reenvia FC ao intervals.icu")
        void paceNaoReenviaFc() {
            atleta(null, 168, 185);
            semMetadados();
            when(atletaRepository.atualizarLimiarPace(atletaId, tenantId, new BigDecimal("4.83"), teste, null))
                    .thenReturn(1);

            service.salvarAncoras(atletaId, new AthleteZoneAnchorsInputDto(null,
                    new AthleteZoneAnchorsInputDto.Pace(290, teste, null)));

            verifyNoInteractions(intervalsIcuFcSyncer);
        }

        @Test
        @DisplayName("FC com máxima: grava limiar e máxima")
        void fcComMaxima() {
            atleta(new BigDecimal("5.00"), null, null);
            semMetadados();
            when(atletaRepository.atualizarLimiarFcEMaxima(atletaId, tenantId, 168, 186, teste,
                    FonteLimiar.TESTE_CAMPO)).thenReturn(1);

            service.salvarAncoras(atletaId, new AthleteZoneAnchorsInputDto(
                    new AthleteZoneAnchorsInputDto.Fc(168, 186, teste, FonteLimiar.TESTE_CAMPO), null));

            verify(atletaRepository, never()).atualizarLimiarFc(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("os dois grupos: grava os dois eixos")
        void doisGrupos() {
            atleta(null, null, null);
            semMetadados();
            when(atletaRepository.atualizarLimiarFc(atletaId, tenantId, 168, teste, null)).thenReturn(1);
            when(atletaRepository.atualizarLimiarPace(atletaId, tenantId, new BigDecimal("5.00"), teste, null))
                    .thenReturn(1);

            service.salvarAncoras(atletaId, new AthleteZoneAnchorsInputDto(
                    new AthleteZoneAnchorsInputDto.Fc(168, null, teste, null),
                    new AthleteZoneAnchorsInputDto.Pace(300, teste, null)));

            verify(atletaRepository).atualizarLimiarFc(atletaId, tenantId, 168, teste, null);
            verify(atletaRepository).atualizarLimiarPace(atletaId, tenantId, new BigDecimal("5.00"), teste, null);
        }

        @Test
        @DisplayName("âncora inválida: nada é gravado")
        void invalidaNaoGrava() {
            atleta(null, null, null);

            assertThatThrownBy(() -> service.salvarAncoras(atletaId, new AthleteZoneAnchorsInputDto(
                    new AthleteZoneAnchorsInputDto.Fc(168, 169, teste, null), null)))
                    .isInstanceOf(InvalidZoneAnchorsException.class);
            verifyNoInteractions(intervalsIcuFcSyncer);

            verify(atletaRepository).findByIdAndTenantId(atletaId, tenantId);
            verifyNoMoreInteractions(atletaRepository);
        }

        @Test
        @DisplayName("atleta de outro tenant: DomainNotFoundException e nada gravado")
        void outroTenantNaoGrava() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.salvarAncoras(atletaId, new AthleteZoneAnchorsInputDto(null,
                    new AthleteZoneAnchorsInputDto.Pace(290, teste, null))))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoMoreInteractions(atletaRepository);
        }

        @Test
        @DisplayName("UPDATE sem linha afetada (atleta sumiu no meio): DomainNotFoundException")
        void updateSemLinha() {
            atleta(null, null, null);
            when(atletaRepository.atualizarLimiarPace(atletaId, tenantId, new BigDecimal("4.83"), teste, null))
                    .thenReturn(0);

            assertThatThrownBy(() -> service.salvarAncoras(atletaId, new AthleteZoneAnchorsInputDto(null,
                    new AthleteZoneAnchorsInputDto.Pace(290, teste, null))))
                    .isInstanceOf(DomainNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("simular")
    class Simular {

        private final LocalDate teste = LocalDate.of(2026, 10, 12);

        private AthleteZoneAnchorsInputDto paceInput(int segKm) {
            return new AthleteZoneAnchorsInputDto(null,
                    new AthleteZoneAnchorsInputDto.Pace(segKm, teste, FonteLimiar.TESTE_CAMPO));
        }

        @Test
        @DisplayName("pace 290 em atleta sem pace: zonas iguais às de um atleta com pace 4.83 gravado")
        void igualAoQueSeriaGravado() {
            Atleta semPace = atleta(null, 168, 185);
            tsbProntidao(0.0);
            AthleteTrainingZonesOutputDto simulado = service.simular(atletaId, paceInput(290));

            semPace.setPaceLimiar(new BigDecimal("4.83"));
            semPace.setDataUltimoTestePace(teste);
            semPace.setFonteLimiarPace(FonteLimiar.TESTE_CAMPO);
            AthleteTrainingZonesOutputDto gravado = service.buscarZonas(atletaId);

            assertThat(simulado).isEqualTo(gravado);
            assertThat(simulado.disponibilidade().pace()).isTrue();
            assertThat(zona(simulado, 2).pace().minSegKm()).isEqualTo(304);
        }

        @Test
        @DisplayName("não grava nada")
        void naoGrava() {
            atleta(null, 168, 185);
            tsbProntidao(0.0);

            service.simular(atletaId, paceInput(290));

            verify(atletaRepository).findByIdAndTenantId(atletaId, tenantId);
            verifyNoMoreInteractions(atletaRepository);
        }

        @Test
        @DisplayName("grupo ausente mantém o eixo: simular só pace preserva a FC e sua origem")
        void mantemOutroEixo() {
            Atleta a = atleta(null, 168, 185);
            a.setFonteLimiarFc(FonteLimiar.PROVA);
            tsbProntidao(0.0);

            AthleteTrainingZonesOutputDto dto = service.simular(atletaId, paceInput(290));

            assertThat(dto.ancoras().fcLimiar()).isEqualTo(168);
            assertThat(dto.ancoras().fonteLimiarFc()).isEqualTo(FonteLimiar.PROVA);
            assertThat(dto.fcMaxima().bpm()).isEqualTo(185);
            assertThat(dto.ancoras().fonteLimiarPace()).isEqualTo(FonteLimiar.TESTE_CAMPO);
        }

        @Test
        @DisplayName("FC sem máxima: máxima estimada continua estimada")
        void maximaEstimadaNaoViraInformada() {
            atleta(new BigDecimal("5.00"), null, null);
            tsbProntidao(0.0);

            AthleteTrainingZonesOutputDto dto = service.simular(atletaId, new AthleteZoneAnchorsInputDto(
                    new AthleteZoneAnchorsInputDto.Fc(168, null, teste, null), null));

            assertThat(dto.ancoras().fcLimiar()).isEqualTo(168);
            assertThat(dto.ancoras().fcLimiarEstimada()).isFalse();
            assertThat(dto.fcMaxima().estimada()).isTrue();
        }

        @Test
        @DisplayName("FC com máxima: máxima passa a informada")
        void maximaInformada() {
            atleta(new BigDecimal("5.00"), null, null);
            tsbProntidao(0.0);

            AthleteTrainingZonesOutputDto dto = service.simular(atletaId, new AthleteZoneAnchorsInputDto(
                    new AthleteZoneAnchorsInputDto.Fc(168, 186, teste, null), null));

            assertThat(dto.fcMaxima().bpm()).isEqualTo(186);
            assertThat(dto.fcMaxima().estimada()).isFalse();
        }

        @Test
        @DisplayName("âncora inválida: mesma rejeição do PUT")
        void invalida() {
            atleta(null, null, null);

            assertThatThrownBy(() -> service.simular(atletaId, paceInput(100)))
                    .isInstanceOf(InvalidZoneAnchorsException.class);
        }

        @Test
        @DisplayName("atleta de outro tenant: DomainNotFoundException")
        void outroTenant() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.simular(atletaId, paceInput(290)))
                    .isInstanceOf(DomainNotFoundException.class);
        }
    }

    @Test
    @DisplayName("atleta de outro tenant (ou inexistente): DomainNotFoundException")
    void outroTenant() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarZonas(atletaId)).isInstanceOf(DomainNotFoundException.class);
    }
}
