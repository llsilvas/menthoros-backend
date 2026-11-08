package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.fit.FitGpsSample;
import br.com.menthoros.backend.dto.output.GpsTraceOutputDto;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.projection.GpsTraceView;
import br.com.menthoros.backend.services.helper.GpsTraceCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AtletaGpsTraceServiceImpl")
class AtletaGpsTraceServiceImplTest {

    @Mock private TreinoRealizadoRepository repository;

    private AtletaGpsTraceServiceImpl service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID atletaId = UUID.randomUUID();
    private final UUID treinoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
        service = new AtletaGpsTraceServiceImpl(repository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("devolve o payload decodificado e o deviceName (CA4)")
    void retornaTrajeto() {
        String json = GpsTraceCodec.toJson(List.of(
                new FitGpsSample(100, -23.55, -46.63, 140),
                new FitGpsSample(160, -23.56, -46.63, null),
                new FitGpsSample(220, -23.56, -46.62, 150)));
        when(repository.findGpsTrace(treinoId, atletaId, tenantId)).thenReturn(Optional.of(view(json, "Forerunner 265")));

        GpsTraceOutputDto dto = service.buscarTrajeto(atletaId, treinoId);

        assertThat(dto.n()).isEqualTo(3);
        assertThat(dto.tSeg()).containsExactly(0, 60, 120);
        assertThat(dto.fc()).containsExactly(140, null, 150);
        assertThat(dto.deviceName()).isEqualTo("Forerunner 265");
        assertThat(dto.polyline()).isNotBlank();
    }

    @Test
    @DisplayName("outro atleta, outro tenant ou inexistente: a query não encontra → 404 (CA4)")
    void naoEncontrado() {
        when(repository.findGpsTrace(treinoId, atletaId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarTrajeto(atletaId, treinoId)).isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    @DisplayName("treino do atleta sem trajeto (esteira, manual, anterior à V113) → 404")
    void semTrajeto() {
        when(repository.findGpsTrace(treinoId, atletaId, tenantId)).thenReturn(Optional.of(view(null, "Forerunner 265")));

        assertThatThrownBy(() -> service.buscarTrajeto(atletaId, treinoId)).isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    @DisplayName("gps_trace corrompido no banco → 404, não 400/500")
    void jsonCorrompido() {
        when(repository.findGpsTrace(treinoId, atletaId, tenantId)).thenReturn(Optional.of(view("{nao e json", null)));

        assertThatThrownBy(() -> service.buscarTrajeto(atletaId, treinoId)).isInstanceOf(DomainNotFoundException.class);
    }

    private static GpsTraceView view(String gpsTrace, String deviceName) {
        return new GpsTraceView() {
            @Override public String getGpsTrace() { return gpsTrace; }
            @Override public String getDeviceName() { return deviceName; }
        };
    }
}
