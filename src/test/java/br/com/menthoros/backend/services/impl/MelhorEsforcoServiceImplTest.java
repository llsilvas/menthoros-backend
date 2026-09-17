package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.intervalsicu.IcuPaceCurveDto;
import br.com.menthoros.backend.dto.output.MelhorEsforcoDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MelhorEsforcoServiceImplTest {

    @Mock
    private IntervalsIcuConnectionService connectionService;
    @Mock
    private IntervalsIcuClient intervalsIcuClient;

    private MelhorEsforcoServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;

    @BeforeEach
    void setUp() {
        service = new MelhorEsforcoServiceImpl(connectionService, intervalsIcuClient);
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private IntegracaoExterna conexao() {
        IntegracaoExterna integracao = new IntegracaoExterna();
        integracao.setAccessToken("tok-abc");
        integracao.setExternalAthleteId("i641775");
        return integracao;
    }

    @Nested
    @DisplayName("buscar")
    class Buscar {

        @Test
        @DisplayName("extrai as distâncias-alvo com ponto exato no curve (CA1)")
        void extraiDistanciasComPontoExato() {
            when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao()));
            IcuPaceCurveDto curva = new IcuPaceCurveDto(List.of(new IcuPaceCurveDto.Curva(
                    List.of(400.0, 800.0, 5000.0),
                    List.of(117, 272, 1796))));
            when(intervalsIcuClient.buscarPaceCurves("tok-abc", "i641775", "42d")).thenReturn(curva);

            List<MelhorEsforcoDto> marcas = service.buscar(atletaId, "42d");

            assertThat(marcas).hasSize(3);
            assertThat(marcas.get(0)).isEqualTo(new MelhorEsforcoDto("400m", 400.0, 117, "4:53/km"));
            assertThat(marcas.get(1)).isEqualTo(new MelhorEsforcoDto("800m", 800.0, 272, "5:40/km"));
            assertThat(marcas.get(2)).isEqualTo(new MelhorEsforcoDto("5k", 5000.0, 1796, "5:59/km"));
        }

        @Test
        @DisplayName("atleta sem integração ativa retorna lista vazia, sem chamar o client (CA2)")
        void atletaSemIntegracaoRetornaVazio() {
            when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.empty());

            List<MelhorEsforcoDto> marcas = service.buscar(atletaId, "42d");

            assertThat(marcas).isEmpty();
            verify(intervalsIcuClient, never()).buscarPaceCurves(any(), any(), any());
        }

        @Test
        @DisplayName("distância sem ponto dentro da tolerância de 1% não entra na lista (CA3)")
        void distanciaForaDaToleranciaNaoEntra() {
            when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao()));
            // 5300m está a 6% de 5000m — fora da tolerância de 1%.
            IcuPaceCurveDto curva = new IcuPaceCurveDto(List.of(new IcuPaceCurveDto.Curva(
                    List.of(400.0, 5300.0),
                    List.of(117, 1900))));
            when(intervalsIcuClient.buscarPaceCurves(eq("tok-abc"), eq("i641775"), eq("42d")))
                    .thenReturn(curva);

            List<MelhorEsforcoDto> marcas = service.buscar(atletaId, "42d");

            assertThat(marcas).hasSize(1);
            assertThat(marcas.getFirst().distanciaLabel()).isEqualTo("400m");
        }

        @Test
        @DisplayName("curve vazio (list=[]) retorna lista vazia, sem lançar")
        void curveVazioRetornaVazio() {
            when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao()));
            when(intervalsIcuClient.buscarPaceCurves("tok-abc", "i641775", "42d"))
                    .thenReturn(new IcuPaceCurveDto(List.of()));

            assertThat(service.buscar(atletaId, "42d")).isEmpty();
        }
    }
}
