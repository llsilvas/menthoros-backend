package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.AtletaHomeDto;
import br.com.menthoros.backend.dto.output.PmcPontoDto;
import br.com.menthoros.backend.dto.output.ReadinessDto;
import br.com.menthoros.backend.dto.output.RecordeDto;
import br.com.menthoros.backend.dto.output.ZonaDistribuicaoDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AtletaProgressServiceImpl")
class AtletaProgressServiceImplTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private MetricasDiariasRepository metricasDiariasRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private PlanoMetadadosRepository planoMetadadosRepository;
    @Mock private ZonaTreinoService zonaTreinoService;
    @Mock private AuthenticatedPrincipalResolver principalResolver;

    @InjectMocks private AtletaProgressServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        atleta = Atleta.builder().id(atletaId).nome("Teste").build();
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getHistoricoPmc")
    class GetHistoricoPmc {

        @Test
        @DisplayName("mapeia a série PMC do intervalo")
        void mapeiaSerie() {
            when(metricasDiariasRepository.findByAtletaIdAndDataBetweenOrderByDataAsc(eq(atletaId), any(), any()))
                    .thenReturn(List.of(
                            metrica(LocalDate.of(2026, 6, 1), 50.0, 60.0, -10.0, 80),
                            metrica(LocalDate.of(2026, 6, 2), 51.0, 58.0, -7.0, 0)));

            List<PmcPontoDto> serie = service.getHistoricoPmc(atletaId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));

            assertThat(serie).hasSize(2);
            assertThat(serie.get(0)).isEqualTo(new PmcPontoDto(LocalDate.of(2026, 6, 1), 50.0, 60.0, -10.0, 80));
        }

        @Test
        @DisplayName("default = últimos 90 dias quando from/to ausentes")
        void defaultNoventaDias() {
            when(metricasDiariasRepository.findByAtletaIdAndDataBetweenOrderByDataAsc(eq(atletaId), any(), any()))
                    .thenReturn(List.of());

            service.getHistoricoPmc(atletaId, null, null);

            ArgumentCaptor<LocalDate> fromCap = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> toCap = ArgumentCaptor.forClass(LocalDate.class);
            verify(metricasDiariasRepository).findByAtletaIdAndDataBetweenOrderByDataAsc(eq(atletaId), fromCap.capture(), toCap.capture());
            assertThat(toCap.getValue()).isEqualTo(LocalDate.now());
            assertThat(fromCap.getValue()).isEqualTo(LocalDate.now().minusDays(90));
        }

        @Test
        @DisplayName("sem dados retorna lista vazia")
        void semDadosVazio() {
            when(metricasDiariasRepository.findByAtletaIdAndDataBetweenOrderByDataAsc(eq(atletaId), any(), any()))
                    .thenReturn(List.of());
            assertThat(service.getHistoricoPmc(atletaId, null, null)).isEmpty();
        }

        @Test
        @DisplayName("from depois de to lança DomainRuleViolationException")
        void fromDepoisDeTo() {
            assertThatThrownBy(() -> service.getHistoricoPmc(atletaId, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1)))
                    .isInstanceOf(DomainRuleViolationException.class);
        }

        @Test
        @DisplayName("atleta de outro tenant → not found")
        void crossTenantNotFound() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getHistoricoPmc(atletaId, null, null))
                    .isInstanceOf(DomainNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getDistribuicaoZonas")
    class GetDistribuicaoZonas {

        @Test
        @DisplayName("soma das zonas = duração total")
        void somaIgualTotal() {
            TreinoRealizado treino = new TreinoRealizado();
            treino.setEtapasRealizadas(List.of(etapa(140, 600), etapa(170, 300)));
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(atletaId), any(), any()))
                    .thenReturn(List.of(treino));
            when(zonaTreinoService.identificarZonaFC(140, atleta)).thenReturn(2);
            when(zonaTreinoService.identificarZonaFC(170, atleta)).thenReturn(4);

            ZonaDistribuicaoDto z = service.getDistribuicaoZonas(atletaId, null, null);

            assertThat(z.z2()).isEqualTo(600);
            assertThat(z.z4()).isEqualTo(300);
            assertThat(z.duracaoTotalSegundos()).isEqualTo(900);
            assertThat(z.z1() + z.z2() + z.z3() + z.z4() + z.z5()).isEqualTo(z.duracaoTotalSegundos());
        }

        @Test
        @DisplayName("período sem treinos → zeros")
        void semTreinosZeros() {
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(atletaId), any(), any()))
                    .thenReturn(List.of());
            ZonaDistribuicaoDto z = service.getDistribuicaoZonas(atletaId, null, null);
            assertThat(z.duracaoTotalSegundos()).isZero();
            assertThat(z.z1()).isZero();
        }

        @Test
        @DisplayName("etapa sem FC é ignorada")
        void etapaSemFcIgnorada() {
            TreinoRealizado treino = new TreinoRealizado();
            treino.setEtapasRealizadas(List.of(etapa(null, 600), etapa(150, 300)));
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(atletaId), any(), any()))
                    .thenReturn(List.of(treino));
            when(zonaTreinoService.identificarZonaFC(150, atleta)).thenReturn(3);

            ZonaDistribuicaoDto z = service.getDistribuicaoZonas(atletaId, null, null);

            assertThat(z.z3()).isEqualTo(300);
            assertThat(z.duracaoTotalSegundos()).isEqualTo(300);
        }
    }

    @Nested
    @DisplayName("getRecordes")
    class GetRecordes {

        @Test
        @DisplayName("retorna o melhor tempo por distância (banda 10k)")
        void melhorTempoPorDistancia() {
            TreinoRealizado lento = treinoDist("10.0", Duration.ofMinutes(50), LocalDate.of(2026, 5, 1));
            TreinoRealizado rapido = treinoDist("10.1", Duration.ofMinutes(45), LocalDate.of(2026, 5, 8));
            when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId))
                    .thenReturn(List.of(lento, rapido));

            List<RecordeDto> recordes = service.getRecordes(atletaId);

            assertThat(recordes).extracting(RecordeDto::distancia).contains("10k");
            RecordeDto rec10k = recordes.stream().filter(r -> r.distancia().equals("10k")).findFirst().orElseThrow();
            assertThat(rec10k.tempo()).isEqualTo(Duration.ofMinutes(45));
            assertThat(rec10k.treinoRealizadoId()).isEqualTo(rapido.getId());
        }

        @Test
        @DisplayName("sem treinos → lista vazia")
        void semTreinosVazio() {
            when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(List.of());
            assertThat(service.getRecordes(atletaId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getReadinessAtual")
    class GetReadinessAtual {

        @Test
        @DisplayName("compõe score a partir do TSB de prontidão")
        void scoreComSinais() {
            when(planoMetadadosRepository.findByAtletaId(atletaId)).thenReturn(Optional.of(meta(10.0, 50.0, 44.0)));
            when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId))
                    .thenReturn(List.of(treinoRpe(6)));

            ReadinessDto r = service.getReadinessAtual(atletaId);

            assertThat(r.score()).isEqualTo(75); // 60 + 1.5*10
            assertThat(r.classificacao()).isEqualTo("BOM");
            assertThat(r.fatores().tsbProntidao()).isEqualTo(10.0);
            assertThat(r.fatores().ultimoRpe()).isEqualTo(6);
        }

        @Test
        @DisplayName("RPE alto do último treino reduz o score")
        void rpeAltoReduz() {
            when(planoMetadadosRepository.findByAtletaId(atletaId)).thenReturn(Optional.of(meta(10.0, 50.0, 44.0)));
            when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId))
                    .thenReturn(List.of(treinoRpe(9)));

            assertThat(service.getReadinessAtual(atletaId).score()).isEqualTo(70); // 75 - 5
        }

        @Test
        @DisplayName("sem sinais → score nulo, sem erro")
        void semSinaisDefault() {
            when(planoMetadadosRepository.findByAtletaId(atletaId)).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(List.of());

            ReadinessDto r = service.getReadinessAtual(atletaId);

            assertThat(r.score()).isNull();
            assertThat(r.classificacao()).isEqualTo("INDISPONIVEL");
            assertThat(r.nota()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("getHome")
    class GetHome {

        @Test
        @DisplayName("inclui próximo treino e métricas-chave")
        void comProximoTreino() {
            TreinoPlanejado tp = new TreinoPlanejado();
            tp.setDataTreino(LocalDate.now().plusDays(1));
            tp.setTipoTreino(TipoTreino.INTERVALADO);
            tp.setDescricao("6x800m");
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(eq(atletaId), any(), any()))
                    .thenReturn(List.of(tp));
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId))
                    .thenReturn(Optional.of(metrica(LocalDate.now(), 52.0, 44.0, 8.0, 0)));

            AtletaHomeDto home = service.getHome(atletaId);

            assertThat(home.proximoTreino()).isNotNull();
            assertThat(home.proximoTreino().tipoTreino()).isEqualTo("INTERVALADO");
            assertThat(home.metricasChave().ctl()).isEqualTo(52.0);
        }

        @Test
        @DisplayName("sem próximo treino → omite, mantém métricas")
        void semProximoTreino() {
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(eq(atletaId), any(), any())).thenReturn(List.of());
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId))
                    .thenReturn(Optional.of(metrica(LocalDate.now(), 52.0, 44.0, 8.0, 0)));

            AtletaHomeDto home = service.getHome(atletaId);

            assertThat(home.proximoTreino()).isNull();
            assertThat(home.metricasChave().tsb()).isEqualTo(8.0);
        }
    }

    @Nested
    @DisplayName("resolverAtletaIdAtual")
    class ResolverAtletaIdAtual {

        @Test
        @DisplayName("resolve o atleta vinculado ao usuário do token")
        void resolveDoToken() {
            UUID usuarioId = UUID.randomUUID();
            Usuario usuario = new Usuario();
            usuario.setId(usuarioId);
            when(principalResolver.getCurrentSubject()).thenReturn("sub-123");
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id("sub-123", tenantId)).thenReturn(Optional.of(usuario));
            when(atletaRepository.findByUsuario_IdAndAssessoria_Id(usuarioId, tenantId)).thenReturn(Optional.of(atleta));

            assertThat(service.resolverAtletaIdAtual()).isEqualTo(atletaId);
        }

        @Test
        @DisplayName("usuário sem atleta vinculado → not found")
        void semAtletaVinculado() {
            UUID usuarioId = UUID.randomUUID();
            Usuario usuario = new Usuario();
            usuario.setId(usuarioId);
            when(principalResolver.getCurrentSubject()).thenReturn("sub-123");
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id("sub-123", tenantId)).thenReturn(Optional.of(usuario));
            when(atletaRepository.findByUsuario_IdAndAssessoria_Id(usuarioId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolverAtletaIdAtual()).isInstanceOf(DomainNotFoundException.class);
        }
    }

    // ===== helpers =====

    private MetricasDiarias metrica(LocalDate data, Double ctl, Double atl, Double tsb, Integer tss) {
        MetricasDiarias m = new MetricasDiarias();
        m.setData(data);
        m.setCtl(ctl);
        m.setAtl(atl);
        m.setTsb(tsb);
        m.setTss(tss);
        m.setVolumeKm(BigDecimal.TEN);
        return m;
    }

    private EtapaRealizada etapa(Integer fcMedia, long duracaoSeg) {
        EtapaRealizada e = new EtapaRealizada();
        e.setFcMedia(fcMedia);
        e.setDuracao(Duration.ofSeconds(duracaoSeg));
        return e;
    }

    private TreinoRealizado treinoDist(String km, Duration tempo, LocalDate data) {
        TreinoRealizado t = new TreinoRealizado();
        t.setId(UUID.randomUUID());
        t.setDistanciaKm(new BigDecimal(km));
        t.setDuracaoMin(tempo);
        t.setDataTreino(data);
        return t;
    }

    private TreinoRealizado treinoRpe(int rpe) {
        TreinoRealizado t = new TreinoRealizado();
        t.setPercepcaoEsforco(rpe);
        return t;
    }

    private PlanoMetaDados meta(Double tsbProntidao, Double ctl, Double atl) {
        return PlanoMetaDados.builder()
                .tsbProntidaoAtual(tsbProntidao)
                .ctlAtual(ctl)
                .atlAtual(atl)
                .build();
    }
}
