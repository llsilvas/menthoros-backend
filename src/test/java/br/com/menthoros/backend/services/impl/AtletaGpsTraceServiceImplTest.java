package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.fit.FitGpsSample;
import br.com.menthoros.backend.dto.output.GpsTraceOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
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
import static org.mockito.Mockito.mock;
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
        TreinoRealizado treino = treinoDe(atletaId);
        treino.setGpsTrace(GpsTraceCodec.toJson(List.of(
                new FitGpsSample(100, -23.55, -46.63, 140),
                new FitGpsSample(160, -23.56, -46.63, null),
                new FitGpsSample(220, -23.56, -46.62, 150))));
        treino.setDeviceName("Forerunner 265");
        when(repository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));

        GpsTraceOutputDto dto = service.buscarTrajeto(atletaId, treinoId);

        assertThat(dto.n()).isEqualTo(3);
        assertThat(dto.tSeg()).containsExactly(0, 60, 120);
        assertThat(dto.fc()).containsExactly(140, null, 150);
        assertThat(dto.deviceName()).isEqualTo("Forerunner 265");
        assertThat(dto.polyline()).isNotBlank();
    }

    @Test
    @DisplayName("treino de outro atleta do mesmo tenant → 404 (CA4)")
    void outroAtletaMesmoTenant() {
        TreinoRealizado treino = treinoDe(UUID.randomUUID());
        treino.setGpsTrace("{}");
        when(repository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));

        assertThatThrownBy(() -> service.buscarTrajeto(atletaId, treinoId)).isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    @DisplayName("treino de outro tenant (repositório não encontra) → 404 (CA4)")
    void outroTenant() {
        when(repository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarTrajeto(atletaId, treinoId)).isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    @DisplayName("treino do atleta sem trajeto (esteira, manual, anterior à V113) → 404")
    void semTrajeto() {
        TreinoRealizado treino = treinoDe(atletaId);
        when(repository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));

        assertThatThrownBy(() -> service.buscarTrajeto(atletaId, treinoId)).isInstanceOf(DomainNotFoundException.class);
    }

    private static TreinoRealizado treinoDe(UUID donoId) {
        Atleta atleta = mock(Atleta.class);
        when(atleta.getId()).thenReturn(donoId);
        TreinoRealizado t = new TreinoRealizado();
        t.setAtleta(atleta);
        return t;
    }
}
