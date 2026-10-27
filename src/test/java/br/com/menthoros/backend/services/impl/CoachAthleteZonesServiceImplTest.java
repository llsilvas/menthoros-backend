package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto.ZonaDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.services.helper.PaceZoneCalculator;
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
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CoachAthleteZonesServiceImpl")
class CoachAthleteZonesServiceImplTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private PlanoMetadadosRepository planoMetadadosRepository;

    private CoachAthleteZonesServiceImpl service;
    private UUID tenantId;
    private UUID atletaId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        ZonaTreinoService zonaTreinoService = new ZonaTreinoService();
        service = new CoachAthleteZonesServiceImpl(atletaRepository, planoMetadadosRepository,
                zonaTreinoService, new PaceZoneCalculator(zonaTreinoService));
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

    @Test
    @DisplayName("atleta de outro tenant (ou inexistente): DomainNotFoundException")
    void outroTenant() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarZonas(atletaId)).isInstanceOf(DomainNotFoundException.class);
    }
}
