package br.com.menthoros.backend.services.helper;

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
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.TsbService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private TreinoMapper treinoMapper;
    @Mock private TsbService tsbService;
    @Mock private TssCalculatorService tssCalculatorService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TreinoDedupHelper treinoDedupHelper;

    private FitTreinoPersister service;

    private UUID tenantId;
    private UUID atletaId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        service = new FitTreinoPersister(atletaRepository, treinoRealizadoRepository,
                treinoMapper, tsbService, tssCalculatorService, eventPublisher, treinoDedupHelper);

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

    @Nested
    @DisplayName("persistir")
    class Persistir {

        @BeforeEach
        void stubComuns() {
            lenient().when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            lenient().when(treinoRealizadoRepository.findByExternalIdAndAtletaId(anyString(), eq(atletaId)))
                    .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("importa um novo treino e retorna novo=true")
        void importaNovo() {
            FitSessionData dados = sessionCorrida(123456789L, 1751360400L);
            when(treinoDedupHelper.saveIdempotent(any(), anyString(), any()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            TreinoRealizadoOutputDto dtoEsperado = mock(TreinoRealizadoOutputDto.class);
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(dtoEsperado);

            FitImportResultado resultado = service.persistir(atletaId, dados);

            assertThat(resultado.novo()).isTrue();
            assertThat(resultado.treino()).isSameAs(dtoEsperado);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoDedupHelper).saveIdempotent(captor.capture(), anyString(), any());
            TreinoRealizado salvo = captor.getValue();
            assertThat(salvo.getDataTreino()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(salvo.getFcMedia()).isEqualTo(150);
            assertThat(salvo.getTssCalculado()).isEqualTo(62);
            assertThat(salvo.getMetodoCalculoTss()).isEqualTo("DISPOSITIVO");
            assertThat(salvo.getEtapasRealizadas()).hasSize(1);
            assertThat(salvo.getExternalId()).isEqualTo(atletaId + "-123456789-1751360400");

            verify(eventPublisher).publishEvent(any(br.com.menthoros.backend.events.TreinoRegistradoEvent.class));
            verify(tsbService).atualizarTsbDia(eq(atletaId), eq(LocalDate.of(2026, 7, 1)));
        }

        @Test
        @DisplayName("re-upload do mesmo .fit (mesmo externalId) retorna novo=false, sem persistir de novo")
        void reuploadRetornaExistente() {
            FitSessionData dados = sessionCorrida(123456789L, 1751360400L);
            String externalId = atletaId + "-123456789-1751360400";
            TreinoRealizado existente = new TreinoRealizado();
            when(treinoRealizadoRepository.findByExternalIdAndAtletaId(externalId, atletaId))
                    .thenReturn(Optional.of(existente));
            TreinoRealizadoOutputDto dtoExistente = mock(TreinoRealizadoOutputDto.class);
            when(treinoMapper.toOutputDto(existente)).thenReturn(dtoExistente);

            FitImportResultado resultado = service.persistir(atletaId, dados);

            assertThat(resultado.novo()).isFalse();
            assertThat(resultado.treino()).isSameAs(dtoExistente);
            verifyNoInteractions(treinoDedupHelper, eventPublisher, tsbService);
        }

        @Test
        @DisplayName("esporte não-corrida usa tipoTreino=CONTINUO e anota o esporte em descricao")
        void esporteNaoCorridaUsaContinuoEDescricao() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofHours(1), 30.0, 140, 165, 80, false, "CYCLING",
                    null, null, null, null, null, null, null, null, null, null, null, null, List.of());
            when(treinoDedupHelper.saveIdempotent(any(), anyString(), any()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoDedupHelper).saveIdempotent(captor.capture(), anyString(), any());
            assertThat(captor.getValue().getTipoTreino().name()).isEqualTo("CONTINUO");
            assertThat(captor.getValue().getDescricao()).contains("CYCLING");
        }

        @Test
        @DisplayName("TSS ausente no .fit é calculado via TssCalculatorService (fallback D0.3)")
        void tssAusenteUsaFallback() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, null, true, "RUNNING",
                    null, null, null, null, null, null, null, null, null, null, null, null, List.of());
            when(tssCalculatorService.calcularTss(any())).thenReturn(70);
            when(treinoDedupHelper.saveIdempotent(any(), anyString(), any()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoDedupHelper).saveIdempotent(captor.capture(), anyString(), any());
            assertThat(captor.getValue().getTssCalculado()).isEqualTo(70);
            assertThat(captor.getValue().getMetodoCalculoTss()).isEqualTo("FC");
        }

        @Test
        @DisplayName("dados parciais (sem distância/FC) não fabrica valores — persiste null")
        void dadosParciaisNaoFabricaValores() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(20), null, null, null, 40, true, "RUNNING",
                    null, null, null, null, null, null, null, null, null, null, null, null, List.of());
            when(treinoDedupHelper.saveIdempotent(any(), anyString(), any()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoDedupHelper).saveIdempotent(captor.capture(), anyString(), any());
            assertThat(captor.getValue().getDistanciaKm()).isNull();
            assertThat(captor.getValue().getFcMedia()).isNull();
            assertThat(captor.getValue().getFcMax()).isNull();
        }

        @Test
        @DisplayName("lap com distância e duração deriva velocidadeMedia e paceMedia (insumo do decoupling Pa:HR)")
        void lapDerivaVelocidadeEPace() {
            // 2,5 km em 15 min → 10,00 km/h e pace 6:00 min/km
            FitSessionData dados = sessionCorrida(1L, 1751360400L);
            when(treinoDedupHelper.saveIdempotent(any(), anyString(), any()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoDedupHelper).saveIdempotent(captor.capture(), anyString(), any());
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
            when(treinoDedupHelper.saveIdempotent(any(), anyString(), any()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoDedupHelper).saveIdempotent(captor.capture(), anyString(), any());
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
            when(treinoDedupHelper.saveIdempotent(any(), anyString(), any()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoDedupHelper).saveIdempotent(captor.capture(), anyString(), any());
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
            when(treinoDedupHelper.saveIdempotent(any(), anyString(), any()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoDedupHelper).saveIdempotent(captor.capture(), anyString(), any());
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
            when(treinoDedupHelper.saveIdempotent(any(), anyString(), any()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoDedupHelper).saveIdempotent(captor.capture(), anyString(), any());
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
            when(treinoDedupHelper.saveIdempotent(any(), anyString(), any()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.persistir(atletaId, dados);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoDedupHelper).saveIdempotent(captor.capture(), anyString(), any());
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
        @DisplayName("atleta não encontrado no tenant lança DomainNotFoundException")
        void atletaNaoEncontrado() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.persistir(atletaId, sessionCorrida(1L, 1751360400L)))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(treinoRealizadoRepository, treinoDedupHelper, eventPublisher, tsbService);
        }

        @Test
        @DisplayName("corrida de concorrência: retorna novo=false e o registro que venceu, sem duplicar evento/recálculo de TSB")
        void concorrenciaRetornaRegistroExistente() {
            FitSessionData dados = sessionCorrida(1L, 1751360400L);
            String externalId = atletaId + "-1-1751360400";
            // TreinoDedupHelper já cobre o retry sob conflito em seu próprio teste — aqui simula
            // o resultado dessa corrida com inserted=false (o "vencedor" buscado do banco), que é
            // o sinal explícito usado por FitTreinoPersister.persistir tanto para decidir NÃO
            // publicar evento/recalcular TSB quanto para o campo "novo" retornado ao controller
            // (200, não 201 — esta requisição não inseriu nada).
            TreinoRealizado ganhador = new TreinoRealizado();
            when(treinoDedupHelper.saveIdempotent(any(), eq(externalId), eq(atletaId)))
                    .thenReturn(new TreinoDedupHelper.SaveResult(ganhador, false));
            when(treinoMapper.toOutputDto(ganhador)).thenReturn(mock(TreinoRealizadoOutputDto.class));

            FitImportResultado resultado = service.persistir(atletaId, dados);

            assertThat(resultado.novo()).isFalse();
            verify(eventPublisher, never()).publishEvent(any());
            verify(tsbService, never()).atualizarTsbDia(any(), any());
        }
    }
}
