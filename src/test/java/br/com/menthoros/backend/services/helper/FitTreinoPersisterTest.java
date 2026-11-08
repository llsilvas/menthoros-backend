package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.fit.FitAmostraFc;
import br.com.menthoros.backend.entity.FaixaZonaManual;
import br.com.menthoros.backend.enums.ModoZonas;
import br.com.menthoros.backend.dto.fit.FitImportResultado;
import br.com.menthoros.backend.dto.fit.FitLapData;
import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FitTreinoPersisterTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private TreinoMapper treinoMapper;
    @Mock private IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;

    private FitTreinoPersister service;

    private UUID tenantId;
    private UUID atletaId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        service = new FitTreinoPersister(atletaRepository, treinoMapper, ingestaoTreinoRealizadoService,
                new ZonaTreinoService());

        atleta = mock(Atleta.class);
        lenient().when(atleta.getId()).thenReturn(atletaId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private FitSessionData sessionCorrida(Long serial, long startEpoch) {
        return new FitSessionData(serial, LocalDate.of(2026, 7, 1), startEpoch,
                Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                null, null, null, null, // sem subida/descida/potência/cadência
                null, null, null, null, null, null, null, null, // sem running dynamics/tempo movimento/calorias
                List.of(lapCorrida(1, Duration.ofMinutes(15), 2.5, 148, 160)));
    }

    /** Lap de corrida sem as métricas novas (elevação/potência/cadência/running dynamics) — evita a fila de nulls posicionais. */
    private static FitLapData lapCorrida(int ordem, Duration duracao, Double distanciaKm, Integer fcMedia, Integer fcMax) {
        return new FitLapData(ordem, duracao, distanciaKm, fcMedia, fcMax, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    /** Lap padrão variando só a cadência — para os testes de sanitização (BVA). */
    private static FitLapData lapComCadencia(int ordem, Integer cadenciaPpm) {
        return new FitLapData(ordem, Duration.ofMinutes(5), 1.0, 148, 160, null, null, null, cadenciaPpm,
                null, null, null, null, null, null, null);
    }

    /** Lap padrão variando elevação/potência — para os testes de sanitização (BVA). */
    private static FitLapData lapComElevacaoPotencia(int ordem, Integer subida, Integer descida, Integer potencia) {
        return new FitLapData(ordem, Duration.ofMinutes(5), 1.0, 148, 160, subida, descida, potencia, 161,
                null, null, null, null, null, null, null);
    }

    /** Lap padrão variando só o GCT médio — para os testes de sanitização (BVA). */
    private static FitLapData lapComGct(int ordem, Integer gctMedioMs) {
        return new FitLapData(ordem, Duration.ofMinutes(5), 1.0, 148, 160, null, null, null, null,
                null, gctMedioMs, null, null, null, null, null);
    }

    /** Lap padrão variando só o equilíbrio de GCT — para os testes de sanitização (BVA). */
    private static FitLapData lapComGctEquilibrio(int ordem, String gctEquilibrioPct) {
        return new FitLapData(ordem, Duration.ofMinutes(5), 1.0, 148, 160, null, null, null, null,
                null, null, new java.math.BigDecimal(gctEquilibrioPct), null, null, null, null);
    }

    /** Lap padrão variando só a passada — para os testes de sanitização (BVA). */
    private static FitLapData lapComPassada(int ordem, String passadaMediaM) {
        return new FitLapData(ordem, Duration.ofMinutes(5), 1.0, 148, 160, null, null, null, null,
                null, null, null, new java.math.BigDecimal(passadaMediaM), null, null, null);
    }

    /** Lap padrão variando só a oscilação vertical — para os testes de sanitização (BVA). */
    private static FitLapData lapComOscilacao(int ordem, String oscilacaoVerticalCm) {
        return new FitLapData(ordem, Duration.ofMinutes(5), 1.0, 148, 160, null, null, null, null,
                null, null, null, null, new java.math.BigDecimal(oscilacaoVerticalCm), null, null);
    }

    /** Lap padrão variando só a proporção vertical — para os testes de sanitização (BVA). */
    private static FitLapData lapComProporcao(int ordem, String proporcaoVerticalPct) {
        return new FitLapData(ordem, Duration.ofMinutes(5), 1.0, 148, 160, null, null, null, null,
                null, null, null, null, null, new java.math.BigDecimal(proporcaoVerticalPct), null);
    }

    @Nested
    @DisplayName("persistir")
    class Persistir {

        @BeforeEach
        void stubComuns() {
            lenient().when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        }

        @Test
        @DisplayName("importa um novo treino e retorna novo=true")
        void importaNovo() {
            FitSessionData dados = sessionCorrida(123456789L, 1751360400L);
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            TreinoRealizadoOutputDto dtoEsperado = mock(TreinoRealizadoOutputDto.class);
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(dtoEsperado);

            FitImportResultado resultado = service.persistir(atletaId, dados, null);

            assertThat(resultado.novo()).isTrue();
            assertThat(resultado.treino()).isSameAs(dtoEsperado);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            TreinoRealizado salvo = captor.getValue();
            assertThat(salvo.getDataTreino()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(salvo.getFcMedia()).isEqualTo(150);
            assertThat(salvo.getTssCalculado()).isEqualTo(62);
            assertThat(salvo.getMetodoCalculoTss()).isEqualTo("DISPOSITIVO");
            assertThat(salvo.getEtapasRealizadas()).hasSize(1);
            assertThat(salvo.getExternalId()).isEqualTo(atletaId + "-123456789-1751360400");

        }

        @Test
        @DisplayName("re-upload do mesmo .fit (mesmo externalId) retorna novo=false, sem persistir de novo")
        void reuploadRetornaExistente() {
            FitSessionData dados = sessionCorrida(123456789L, 1751360400L);
            TreinoRealizado existente = new TreinoRealizado();
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(existente, false));
            TreinoRealizadoOutputDto dtoExistente = mock(TreinoRealizadoOutputDto.class);
            when(treinoMapper.toOutputDto(existente)).thenReturn(dtoExistente);

            FitImportResultado resultado = service.persistir(atletaId, dados, null);

            assertThat(resultado.novo()).isFalse();
            assertThat(resultado.treino()).isSameAs(dtoExistente);
        }

        @Test
        @DisplayName("deviceName do ping vai para o treino novo")
        void deviceNameNoTreinoNovo() {
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, sessionCorrida(1L, 1751360400L), " Forerunner 970 ");

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            assertThat(captor.getValue().getDeviceName()).isEqualTo("Forerunner 970");
        }

        @Test
        @DisplayName("upload manual (deviceName nulo ou vazio) não grava modelo")
        void deviceNameNuloNoUpload() {
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, sessionCorrida(1L, 1751360400L), "  ");

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            assertThat(captor.getValue().getDeviceName()).isNull();
        }

        @Test
        @DisplayName("deviceName acima de 200 caracteres é cortado no limite da coluna")
        void deviceNameCortado() {
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, sessionCorrida(1L, 1751360400L), "x".repeat(300));

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            assertThat(captor.getValue().getDeviceName()).hasSize(200);
        }

        @Test
        @DisplayName("duplicata sem modelo recebe o deviceName do ping; nada mais muda")
        void duplicataPreencheDeviceName() {
            TreinoRealizado existente = new TreinoRealizado();
            existente.setTssCalculado(55);
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(existente, false));
            when(treinoMapper.toOutputDto(existente)).thenReturn(mock(TreinoRealizadoOutputDto.class));

            FitImportResultado resultado = service.persistir(atletaId, sessionCorrida(1L, 1751360400L), "Forerunner 970");

            assertThat(resultado.novo()).isFalse();
            assertThat(existente.getDeviceName()).isEqualTo("Forerunner 970");
            assertThat(existente.getTssCalculado()).isEqualTo(55);
            verify(ingestaoTreinoRealizadoService, times(1)).registrar(any(), anyString());
        }

        @Test
        @DisplayName("upload manual sem ping usa o productName do .fit como deviceName (D1)")
        void deviceNameDoFitNoUploadManual() {
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, sessionCorrida(1L, 1751360400L).withGps(List.of(), "Forerunner 265"), null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            assertThat(captor.getValue().getDeviceName()).isEqualTo("Forerunner 265");
        }

        @Test
        @DisplayName("ping e .fit com modelos diferentes: o do ping prevalece (D1)")
        void pingPrevaleceSobreFit() {
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, sessionCorrida(1L, 1751360400L).withGps(List.of(), "FR265"), "Forerunner 265");

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            assertThat(captor.getValue().getDeviceName()).isEqualTo("Forerunner 265");
        }

        @Test
        @DisplayName("duplicata sem modelo recebe o productName do .fit quando o ping não trouxe")
        void duplicataPreencheDeviceNameDoFit() {
            TreinoRealizado existente = new TreinoRealizado();
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(existente, false));
            when(treinoMapper.toOutputDto(existente)).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, sessionCorrida(1L, 1751360400L).withGps(List.of(), "Forerunner 265"), null);

            assertThat(existente.getDeviceName()).isEqualTo("Forerunner 265");
        }

        @Test
        @DisplayName("trajeto GPS vira JSON em gpsTrace; sem GPS fica nulo (D2)")
        void gpsTracePersistido() {
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));
            List<br.com.menthoros.backend.dto.fit.FitGpsSample> gps = List.of(
                    new br.com.menthoros.backend.dto.fit.FitGpsSample(1751360400L, -23.5500, -46.6300, 140),
                    new br.com.menthoros.backend.dto.fit.FitGpsSample(1751360460L, -23.5520, -46.6300, 150),
                    new br.com.menthoros.backend.dto.fit.FitGpsSample(1751360520L, -23.5520, -46.6280, null));

            service.persistir(atletaId, sessionCorrida(1L, 1751360400L).withGps(gps, null), null);
            service.persistir(atletaId, sessionCorrida(2L, 1751360400L), null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService, times(2)).registrar(captor.capture(), anyString());
            String json = captor.getAllValues().get(0).getGpsTrace();
            assertThat(json).contains("\"v\":1").contains("\"n\":3").contains("\"polyline\":");
            assertThat(GpsTraceCodec.fromJson(json).fc()).containsExactly(140, 150, null);
            assertThat(captor.getAllValues().get(1).getGpsTrace()).isNull();
        }

        @Test
        @DisplayName("duplicata que já tem modelo não é sobrescrita")
        void duplicataMantemDeviceName() {
            TreinoRealizado existente = new TreinoRealizado();
            existente.setDeviceName("Fenix 8");
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(existente, false));
            when(treinoMapper.toOutputDto(existente)).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, sessionCorrida(1L, 1751360400L), "Forerunner 970");

            assertThat(existente.getDeviceName()).isEqualTo("Fenix 8");
        }

        @Test
        @DisplayName("esporte não-corrida usa tipoTreino=CONTINUO e anota o esporte em descricao")
        void esporteNaoCorridaUsaContinuoEDescricao() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofHours(1), 30.0, 140, 165, 80, false, "CYCLING",
                    null, null, null, null, null, null, null, null, null, null, null, null, List.of());
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            assertThat(captor.getValue().getTipoTreino().name()).isEqualTo("CONTINUO");
            assertThat(captor.getValue().getDescricao()).contains("CYCLING");
        }

        @Test
        @DisplayName("TSS ausente no .fit chega null ao ingestor — quem calcula é IngestaoTreinoRealizadoService (D3.1)")
        void tssAusenteNaoCalculaAqui() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, null, true, "RUNNING",
                    null, null, null, null, null, null, null, null, null, null, null, null, List.of());
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            assertThat(captor.getValue().getTssCalculado()).isNull();
            assertThat(captor.getValue().getMetodoCalculoTss()).isNull();
        }

        @Test
        @DisplayName("dados parciais (sem distância/FC) não fabrica valores — persiste null")
        void dadosParciaisNaoFabricaValores() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(20), null, null, null, 40, true, "RUNNING",
                    null, null, null, null, null, null, null, null, null, null, null, null, List.of());
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            assertThat(captor.getValue().getDistanciaKm()).isNull();
            assertThat(captor.getValue().getFcMedia()).isNull();
            assertThat(captor.getValue().getFcMax()).isNull();
        }

        @Test
        @DisplayName("lap com distância e duração deriva velocidadeMedia e paceMedia (insumo do decoupling Pa:HR)")
        void lapDerivaVelocidadeEPace() {
            // 2,5 km em 15 min → 10,00 km/h e pace 6:00 min/km
            FitSessionData dados = sessionCorrida(1L, 1751360400L);
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            var etapa = captor.getValue().getEtapasRealizadas().get(0);
            assertThat(etapa.getVelocidadeMedia()).isEqualByComparingTo(new java.math.BigDecimal("10.00"));
            assertThat(etapa.getPaceMedia()).isEqualTo(Duration.ofMinutes(6));
        }

        @Test
        @DisplayName("lap sem distância ou com duração zero não fabrica velocidade/pace — persiste null")
        void lapSemMetricaNaoFabricaVelocidade() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, null, // sem subida/descida/potência/cadência
                    null, null, null, null, null, null, null, null, // sem running dynamics/tempo movimento/calorias
                    List.of(
                            lapCorrida(1, Duration.ofMinutes(15), null, 148, 160),
                            lapCorrida(2, Duration.ZERO, 0.5, 150, 162),
                            lapCorrida(3, Duration.ofMinutes(10), 0.0, 152, 164)));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            assertThat(captor.getValue().getEtapasRealizadas())
                    .allSatisfy(etapa -> {
                        assertThat(etapa.getVelocidadeMedia()).isNull();
                        assertThat(etapa.getPaceMedia()).isNull();
                    });
        }

        @Test
        @DisplayName("lap e sessão com elevação/potência/cadência persistem os campos na etapa e no treino")
        void persisteElevacaoPotenciaCadencia() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    65, 57, 362, 165,
                    null, null, null, null, null, null, null, null,
                    List.of(new FitLapData(1, Duration.ofMinutes(15), 2.5, 148, 160, 4, 2, 351, 161,
                            null, null, null, null, null, null, null)));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            TreinoRealizado treino = captor.getValue();
            assertThat(treino.getElevacaoGanhoMetros()).isEqualTo(65);
            assertThat(treino.getElevacaoPerdaMetros()).isEqualTo(57);
            assertThat(treino.getPotenciaMedia()).isEqualTo(362);
            assertThat(treino.getCadenciaMedia()).isEqualTo(165);

            var etapa = treino.getEtapasRealizadas().get(0);
            assertThat(etapa.getElevacaoGanhoMetros()).isEqualTo(4);
            assertThat(etapa.getElevacaoPerdaMetros()).isEqualTo(2);
            assertThat(etapa.getPotenciaMedia()).isEqualTo(351);
            assertThat(etapa.getCadenciaMedia()).isEqualTo(161);
        }

        @Test
        @DisplayName("lap e sessão sem elevação/potência/cadência persistem null sem falhar")
        void semMetricasNovasPersisteNull() {
            FitSessionData dados = sessionCorrida(1L, 1751360400L);
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            TreinoRealizado treino = captor.getValue();
            assertThat(treino.getElevacaoGanhoMetros()).isNull();
            assertThat(treino.getElevacaoPerdaMetros()).isNull();
            assertThat(treino.getPotenciaMedia()).isNull();
            assertThat(treino.getCadenciaMedia()).isNull();
            var etapa = treino.getEtapasRealizadas().get(0);
            assertThat(etapa.getElevacaoGanhoMetros()).isNull();
            assertThat(etapa.getPotenciaMedia()).isNull();
            assertThat(etapa.getCadenciaMedia()).isNull();
        }

        @Test
        @DisplayName("cadência fora da faixa fisiológica (60-200 ppm) é descartada — BVA nos limites")
        void cadenciaForaDaFaixaDescartada() {
            // 59 e 201 → null; 60 e 200 → mantidos (mesma regra do import Strava)
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, 59, // cadência de sessão também fora da faixa
                    null, null, null, null, null, null, null, null,
                    List.of(
                            lapComCadencia(1, 59),
                            lapComCadencia(2, 60),
                            lapComCadencia(3, 200),
                            lapComCadencia(4, 201)));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            TreinoRealizado treino = captor.getValue();
            assertThat(treino.getCadenciaMedia()).isNull();
            assertThat(treino.getEtapasRealizadas().get(0).getCadenciaMedia()).isNull();
            assertThat(treino.getEtapasRealizadas().get(1).getCadenciaMedia()).isEqualTo(60);
            assertThat(treino.getEtapasRealizadas().get(2).getCadenciaMedia()).isEqualTo(200);
            assertThat(treino.getEtapasRealizadas().get(3).getCadenciaMedia()).isNull();
        }

        @Test
        @DisplayName("elevação e potência adversariais (sentinel uint16/fora da faixa) são descartadas — BVA nos limites")
        void elevacaoEPotenciaForaDaFaixaDescartadas() {
            // .fit é upload não confiável: 65534 (uint16 máx válido do SDK) não pode virar dado do treino.
            // BVA: elevação 0 e 10000 mantidas, 10001 null; potência 2500 mantida, 2501 e 0 null.
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    65534, 65534, 65534, 165,
                    null, null, null, null, null, null, null, null,
                    List.of(
                            lapComElevacaoPotencia(1, 65534, 65534, 65534),
                            lapComElevacaoPotencia(2, 0, 10_000, 2_500),
                            lapComElevacaoPotencia(3, 10_001, 0, 2_501),
                            lapComElevacaoPotencia(4, null, null, 0)));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            TreinoRealizado treino = captor.getValue();
            assertThat(treino.getElevacaoGanhoMetros()).isNull();
            assertThat(treino.getElevacaoPerdaMetros()).isNull();
            assertThat(treino.getPotenciaMedia()).isNull();

            var etapas = treino.getEtapasRealizadas();
            assertThat(etapas.get(0).getElevacaoGanhoMetros()).isNull();
            assertThat(etapas.get(0).getElevacaoPerdaMetros()).isNull();
            assertThat(etapas.get(0).getPotenciaMedia()).isNull();
            assertThat(etapas.get(1).getElevacaoGanhoMetros()).isZero();
            assertThat(etapas.get(1).getElevacaoPerdaMetros()).isEqualTo(10_000);
            assertThat(etapas.get(1).getPotenciaMedia()).isEqualTo(2_500);
            assertThat(etapas.get(2).getElevacaoGanhoMetros()).isNull();
            assertThat(etapas.get(2).getElevacaoPerdaMetros()).isZero();
            assertThat(etapas.get(2).getPotenciaMedia()).isNull();
            assertThat(etapas.get(3).getPotenciaMedia()).isNull();
        }

        @Test
        @DisplayName("velocidade média e máxima e temperaturas da sessão persistem no treino")
        void persisteVelocidadeRitmoETemperaturas() {
            FitSessionData dados = sessaoComExecucao(12.0, "14.40");
            stubIngestao();

            service.persistir(atletaId, dados, null);

            TreinoRealizado treino = treinoRegistrado();
            assertThat(treino.getVelocidadeMedia()).isEqualTo(12.0);
            assertThat(treino.getPaceMedia()).as("paceMedia alimenta TSS/limiar — não muda nesta change").isNull();
            assertThat(treino.getVelocidadeMaxKmh()).isEqualByComparingTo("14.40");
            assertThat(treino.getTemperaturaMinC()).isEqualByComparingTo("19.0");
            assertThat(treino.getTemperaturaMaxC()).isEqualByComparingTo("27.0");
        }

        @Test
        @DisplayName("velocidade ausente, zero ou acima de 150 km/h fica nula — BVA no teto")
        void velocidadeForaDaFaixaDescartada() {
            stubIngestao();

            service.persistir(atletaId, sessaoComExecucao(150.0, "150.01"), null);
            TreinoRealizado noTeto = treinoRegistrado();
            assertThat(noTeto.getVelocidadeMedia()).isEqualTo(150.0);
            assertThat(noTeto.getVelocidadeMaxKmh()).isNull();

            clearInvocations(ingestaoTreinoRealizadoService);
            service.persistir(atletaId, sessaoComExecucao(0.0, null), null);
            TreinoRealizado zerado = treinoRegistrado();
            assertThat(zerado.getVelocidadeMedia()).isNull();
            assertThat(zerado.getVelocidadeMaxKmh()).isNull();
        }

        private FitSessionData sessaoComExecucao(Double velocidadeMediaKmh, String velocidadeMaxKmh) {
            return new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, null,
                    null, null, null, null, null, null, null, null,
                    List.of(lapCorrida(1, Duration.ofMinutes(15), 2.5, 148, 160)),
                    velocidadeMediaKmh,
                    velocidadeMaxKmh != null ? new java.math.BigDecimal(velocidadeMaxKmh) : null,
                    new java.math.BigDecimal("19.0"), new java.math.BigDecimal("27.0"), List.of());
        }

        private void stubIngestao() {
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));
        }

        private TreinoRealizado treinoRegistrado() {
            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            return captor.getValue();
        }

        @Test
        @DisplayName("running dynamics completos (GCT, equilíbrio, passada, oscilação, proporção, temperatura, tempo em movimento, calorias) persistem no treino e na etapa")
        void persisteRunningDynamicsCompletos() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, null,
                    Duration.ofSeconds(1750), 650, 252, new java.math.BigDecimal("49.3"),
                    new java.math.BigDecimal("1.05"), new java.math.BigDecimal("8.2"), new java.math.BigDecimal("6.8"),
                    new java.math.BigDecimal("22.0"),
                    List.of(new FitLapData(1, Duration.ofMinutes(15), 2.5, 148, 160, null, null, null, null,
                            Duration.ofSeconds(850), 255, new java.math.BigDecimal("48.7"),
                            new java.math.BigDecimal("0.98"), new java.math.BigDecimal("9.0"), new java.math.BigDecimal("7.1"),
                            new java.math.BigDecimal("23.0"))));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            TreinoRealizado treino = captor.getValue();
            assertThat(treino.getTempoMovimento()).isEqualTo(Duration.ofSeconds(1750));
            assertThat(treino.getCalorias()).isEqualTo(650);
            assertThat(treino.getGctMedioMs()).isEqualTo(252);
            assertThat(treino.getGctEquilibrioPct()).isEqualByComparingTo("49.3");
            assertThat(treino.getPassadaMediaM()).isEqualByComparingTo("1.05");
            assertThat(treino.getOscilacaoVerticalCm()).isEqualByComparingTo("8.2");
            assertThat(treino.getProporcaoVerticalPct()).isEqualByComparingTo("6.8");
            assertThat(treino.getTemperaturaMediaC()).isEqualByComparingTo("22.0");

            var etapa = treino.getEtapasRealizadas().get(0);
            assertThat(etapa.getTempoMovimento()).isEqualTo(Duration.ofSeconds(850));
            assertThat(etapa.getGctMedioMs()).isEqualTo(255);
            assertThat(etapa.getGctEquilibrioPct()).isEqualByComparingTo("48.7");
            assertThat(etapa.getPassadaMediaM()).isEqualByComparingTo("0.98");
            assertThat(etapa.getOscilacaoVerticalCm()).isEqualByComparingTo("9.0");
            assertThat(etapa.getProporcaoVerticalPct()).isEqualByComparingTo("7.1");
            assertThat(etapa.getTemperaturaMediaC()).isEqualByComparingTo("23.0");
        }

        @Test
        @DisplayName("dispositivo sem running dynamics: campos ficam null sem falhar (CA4)")
        void semRunningDynamicsPersisteNullSemFalhar() {
            FitSessionData dados = sessionCorrida(1L, 1751360400L);
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            TreinoRealizado treino = captor.getValue();
            assertThat(treino.getTempoMovimento()).isNull();
            assertThat(treino.getCalorias()).isNull();
            assertThat(treino.getGctMedioMs()).isNull();
            assertThat(treino.getGctEquilibrioPct()).isNull();
            assertThat(treino.getPassadaMediaM()).isNull();
            assertThat(treino.getOscilacaoVerticalCm()).isNull();
            assertThat(treino.getProporcaoVerticalPct()).isNull();
            assertThat(treino.getTemperaturaMediaC()).isNull();
        }

        @Test
        @DisplayName("GCT fora de 100-500ms é descartado — BVA nos limites")
        void gctForaDaFaixaDescartado() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    List.of(
                            lapComGct(1, 99),
                            lapComGct(2, 100),
                            lapComGct(3, 500),
                            lapComGct(4, 501)));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            var etapas = captor.getValue().getEtapasRealizadas();
            assertThat(etapas.get(0).getGctMedioMs()).isNull();
            assertThat(etapas.get(1).getGctMedioMs()).isEqualTo(100);
            assertThat(etapas.get(2).getGctMedioMs()).isEqualTo(500);
            assertThat(etapas.get(3).getGctMedioMs()).isNull();
        }

        @Test
        @DisplayName("equilíbrio de GCT fora de 30-70% é descartado — BVA nos limites")
        void gctEquilibrioForaDaFaixaDescartado() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    List.of(
                            lapComGctEquilibrio(1, "29.9"),
                            lapComGctEquilibrio(2, "30.0"),
                            lapComGctEquilibrio(3, "70.0"),
                            lapComGctEquilibrio(4, "70.1")));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            var etapas = captor.getValue().getEtapasRealizadas();
            assertThat(etapas.get(0).getGctEquilibrioPct()).isNull();
            assertThat(etapas.get(1).getGctEquilibrioPct()).isEqualByComparingTo("30.0");
            assertThat(etapas.get(2).getGctEquilibrioPct()).isEqualByComparingTo("70.0");
            assertThat(etapas.get(3).getGctEquilibrioPct()).isNull();
        }

        @Test
        @DisplayName("passada fora de 0,3-3,0m é descartada — BVA nos limites")
        void passadaForaDaFaixaDescartada() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    List.of(
                            lapComPassada(1, "0.29"),
                            lapComPassada(2, "0.30"),
                            lapComPassada(3, "3.0"),
                            lapComPassada(4, "3.01")));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            var etapas = captor.getValue().getEtapasRealizadas();
            assertThat(etapas.get(0).getPassadaMediaM()).isNull();
            assertThat(etapas.get(1).getPassadaMediaM()).isEqualByComparingTo("0.30");
            assertThat(etapas.get(2).getPassadaMediaM()).isEqualByComparingTo("3.0");
            assertThat(etapas.get(3).getPassadaMediaM()).isNull();
        }

        @Test
        @DisplayName("oscilação vertical negativa ou acima do teto de sanidade é descartada — protege contra overflow de NUMERIC(4,1) (achado do QA gate)")
        void oscilacaoForaDaFaixaDescartada() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    List.of(
                            lapComOscilacao(1, "-0.1"),
                            lapComOscilacao(2, "0.0"),
                            lapComOscilacao(3, "50.0"),
                            lapComOscilacao(4, "50.1")));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            var etapas = captor.getValue().getEtapasRealizadas();
            assertThat(etapas.get(0).getOscilacaoVerticalCm()).isNull();
            assertThat(etapas.get(1).getOscilacaoVerticalCm()).isEqualByComparingTo("0.0");
            assertThat(etapas.get(2).getOscilacaoVerticalCm()).isEqualByComparingTo("50.0");
            assertThat(etapas.get(3).getOscilacaoVerticalCm()).isNull();
        }

        @Test
        @DisplayName("proporção vertical negativa ou acima do teto de sanidade é descartada — protege contra overflow de NUMERIC(4,1) (achado do QA gate)")
        void proporcaoForaDaFaixaDescartada() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    List.of(
                            lapComProporcao(1, "-0.1"),
                            lapComProporcao(2, "0.0"),
                            lapComProporcao(3, "50.0"),
                            lapComProporcao(4, "50.1")));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            var etapas = captor.getValue().getEtapasRealizadas();
            assertThat(etapas.get(0).getProporcaoVerticalPct()).isNull();
            assertThat(etapas.get(1).getProporcaoVerticalPct()).isEqualByComparingTo("0.0");
            assertThat(etapas.get(2).getProporcaoVerticalPct()).isEqualByComparingTo("50.0");
            assertThat(etapas.get(3).getProporcaoVerticalPct()).isNull();
        }

        @Test
        @DisplayName("D6/CA7: tempoMovimento menor que duracao corrige velocidade/pace (lap com pausa)")
        void tempoMovimentoMenorCorrigeVelocidade() {
            // 2,5km em 15min elapsed (10,00 km/h) mas só 12min30s em movimento (12,00 km/h real).
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    List.of(new FitLapData(1, Duration.ofMinutes(15), 2.5, 148, 160, null, null, null, null,
                            Duration.ofSeconds(750), null, null, null, null, null, null)));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            var etapa = captor.getValue().getEtapasRealizadas().get(0);
            assertThat(etapa.getVelocidadeMedia()).isEqualByComparingTo("12.00");
            assertThat(etapa.getPaceMedia()).isEqualTo(Duration.ofSeconds(300)); // 5:00/km
            // duracao persistida continua sendo o elapsed — D6 só corrige o cálculo derivado.
            assertThat(etapa.getDuracao()).isEqualTo(Duration.ofMinutes(15));
        }

        @Test
        @DisplayName("D6/CA7: sem tempoMovimento, velocidade/pace usam duracao — comportamento legado preservado")
        void semTempoMovimentoUsaDuracaoLegado() {
            FitSessionData dados = sessionCorrida(1L, 1751360400L); // lapCorrida sem tempoMovimento
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            var etapa = captor.getValue().getEtapasRealizadas().get(0);
            // 2,5km em 15min -> 10,00 km/h, pace 6:00/km (idêntico ao golden anterior a esta change).
            assertThat(etapa.getVelocidadeMedia()).isEqualByComparingTo("10.00");
            assertThat(etapa.getPaceMedia()).isEqualTo(Duration.ofMinutes(6));
        }

        @Test
        @DisplayName("D6/CA7: tempoMovimento >= duracao é defensivo — mantém duracao (dado inconsistente do firmware)")
        void tempoMovimentoMaiorOuIgualMantemDuracao() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    List.of(new FitLapData(1, Duration.ofMinutes(15), 2.5, 148, 160, null, null, null, null,
                            Duration.ofMinutes(15), null, null, null, null, null, null))); // tempoMovimento == duracao
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            var etapa = captor.getValue().getEtapasRealizadas().get(0);
            assertThat(etapa.getVelocidadeMedia()).isEqualByComparingTo("10.00");
            assertThat(etapa.getPaceMedia()).isEqualTo(Duration.ofMinutes(6));
        }

        @Test
        @DisplayName("D6/CA7: teste de regressão — voltas com pausa da fixture real (achado de fit-lap-derived-metrics)")
        void regressaoVoltasComPausaDaFixtureReal() {
            // Reconstrução da volta 10 da fixture corrida-15km-16laps.fit: totalElapsedTime=611s,
            // ~364s de movimento real (achado documentado em fit-lap-derived-metrics/tasks.md 3.1:
            // pace bruto derivado de elapsed diverge do Garmin em até 239 s/km nessa volta).
            // Distância do lap: 1,000 km (volta de 1km da fixture).
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    List.of(new FitLapData(10, Duration.ofSeconds(611), 1.0, 150, 165, null, null, null, null,
                            Duration.ofSeconds(364), null, null, null, null, null, null)));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados, null);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            var etapa = captor.getValue().getEtapasRealizadas().get(0);

            // Pace bruto (elapsed, comportamento pré-D6): 611s/km -> 10:11/km — o que o Garmin
            // reportava como discrepante (o CSV real mostrava ~366s/km nessa volta).
            Duration paceBrutoElapsed = Duration.ofSeconds(611);
            // Pace corrigido (D6, tempoMovimento): 364s/km -> 6:04/km — na faixa do erro sem pausa
            // (~4,8-8 s/km de desvio vs. Garmin), não mais nos 239 s/km documentados.
            Duration paceCorrigido = Duration.ofSeconds(364);

            assertThat(etapa.getPaceMedia()).isEqualTo(paceCorrigido);
            assertThat(etapa.getPaceMedia()).isNotEqualTo(paceBrutoElapsed);
            long desvioSegKm = Math.abs(paceBrutoElapsed.toSeconds() - paceCorrigido.toSeconds());
            assertThat(desvioSegKm).isGreaterThan(200); // prova que o cenário reproduz o desvio grande documentado
        }

        @Test
        @DisplayName("atleta não encontrado no tenant lança DomainNotFoundException")
        void atletaNaoEncontrado() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.persistir(atletaId, sessionCorrida(1L, 1751360400L), null))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(ingestaoTreinoRealizadoService);
        }

        // Corrida de concorrência e re-upload agora são o mesmo caso a este nível: registrar
        // devolve inserted=false e este persister apenas repassa — a distinção (retry sob conflito
        // de constraint vs. externalId já visto) é responsabilidade do ingestor, coberta em
        // IngestaoTreinoRealizadoServiceRegistrarIT (ingestao-treino-realizado).
    }

    @Nested
    @DisplayName("tempo em zonas (coach-ver-treino-realizado, D6)")
    class TempoEmZonasNoImport {

        /** 30 min de amostras a 1 Hz em 150 bpm, mais o tempo em movimento da sessão. */
        private FitSessionData sessaoComAmostras() {
            List<FitAmostraFc> amostras = new java.util.ArrayList<>();
            for (int i = 0; i < 1800; i++) amostras.add(new FitAmostraFc(1_751_360_400L + i, 150));
            return new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                    null, null, null, null,
                    Duration.ofMinutes(30), null, null, null, null, null, null, null,
                    List.of(lapCorrida(1, Duration.ofMinutes(15), 2.5, 148, 160)),
                    null, null, null, null, amostras);
        }

        private void stubIngestaoEAtleta() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));
            lenient().when(atleta.getModoZonasFc()).thenReturn(ModoZonas.LIMIAR);
            lenient().when(atleta.getModoZonasPace()).thenReturn(ModoZonas.LIMIAR);
            lenient().when(atleta.getFcMaximaCalculada()).thenReturn(185);
        }

        private TreinoRealizado treinoRegistrado() {
            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), anyString());
            return captor.getValue();
        }

        @Test
        @DisplayName("atleta com FC de limiar informada: grava as cinco zonas com os limites usados")
        void atletaComFcLimiar() {
            stubIngestaoEAtleta();
            when(atleta.getFcLimiar()).thenReturn(165);
            when(atleta.getFcLimiarCalculada()).thenReturn(165);

            service.persistir(atletaId, sessaoComAmostras(), null);

            var tempo = treinoRegistrado().getTempoEmZonas();
            assertThat(tempo).isNotNull();
            assertThat(tempo.zonas()).hasSize(5);
            assertThat(tempo.zonas().stream().mapToInt(z -> z.segundos()).sum()).isEqualTo(1800);
            // 150 bpm com limiar 165: Z3 vai de 89 % (147) a 94 % (155)
            assertThat(tempo.zonas().get(2).segundos()).isEqualTo(1800);
        }

        @Test
        @DisplayName("atleta com zonas de FC manuais: usa as faixas gravadas")
        void atletaComZonasManuais() {
            stubIngestaoEAtleta();
            when(atleta.getModoZonasFc()).thenReturn(ModoZonas.MANUAL);
            when(atleta.getZonasManuaisFc()).thenReturn(List.of(
                    new FaixaZonaManual(1, 100, 120), new FaixaZonaManual(2, 121, 140),
                    new FaixaZonaManual(3, 141, 145), new FaixaZonaManual(4, 146, 160),
                    new FaixaZonaManual(5, 161, 190)));

            service.persistir(atletaId, sessaoComAmostras(), null);

            var tempo = treinoRegistrado().getTempoEmZonas();
            assertThat(tempo.zonas().get(3)).isEqualTo(
                    new br.com.menthoros.backend.entity.TempoEmZonas.Zona("Z4", 146, 160, 1800));
        }

        @Test
        @DisplayName("atleta só com FC estimada (sem limiar informado, zonas por limiar): tempo em zonas nulo")
        void atletaSoComFcEstimada() {
            stubIngestaoEAtleta();
            when(atleta.getFcLimiar()).thenReturn(null);
            lenient().when(atleta.getFcLimiarCalculada()).thenReturn(157);

            service.persistir(atletaId, sessaoComAmostras(), null);

            assertThat(treinoRegistrado().getTempoEmZonas()).isNull();
        }

        @Test
        @DisplayName(".fit sem amostras: tempo em zonas nulo e o import não falha")
        void semAmostras() {
            stubIngestaoEAtleta();
            lenient().when(atleta.getFcLimiar()).thenReturn(165);
            lenient().when(atleta.getFcLimiarCalculada()).thenReturn(165);

            service.persistir(atletaId, sessionCorrida(1L, 1751360400L), null);

            assertThat(treinoRegistrado().getTempoEmZonas()).isNull();
        }
    }
}
