package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.AtletaHomeDto;
import br.com.menthoros.backend.dto.output.PmcPontoDto;
import br.com.menthoros.backend.dto.output.ReadinessDto;
import br.com.menthoros.backend.dto.output.RecordeDto;
import br.com.menthoros.backend.dto.output.ZonaDistribuicaoDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.CheckinProntidao;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import org.mapstruct.factory.Mappers;
import br.com.menthoros.backend.mapper.EtapaMapper;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelProntidao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.CheckinProntidaoRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.helper.AtletaHojeResolver;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
@DisplayName("AtletaProgressServiceImpl")
class AtletaProgressServiceImplTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 6, 17);

    @Mock private AtletaRepository atletaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private MetricasDiariasRepository metricasDiariasRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private PlanoMetadadosRepository planoMetadadosRepository;
    @Mock private ZonaTreinoService zonaTreinoService;
    @Mock private AuthenticatedPrincipalResolver principalResolver;
    @Mock private CheckinProntidaoRepository checkinProntidaoRepository;

    private AtletaProgressServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        atleta = Atleta.builder().id(atletaId).nome("Teste").build();
        Clock clock = Clock.fixed(Instant.parse("2026-06-17T12:00:00Z"), ZoneOffset.UTC);
        service = new AtletaProgressServiceImpl(
                atletaRepository, usuarioRepository, metricasDiariasRepository, treinoRealizadoRepository,
                treinoPlanejadoRepository, planoMetadadosRepository, zonaTreinoService, principalResolver,
                checkinProntidaoRepository, Mappers.getMapper(EtapaMapper.class), clock,
                new AtletaHojeResolver(clock));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Stub do gate de tenant — chamado pelos métodos que validam o atleta. */
    private void atletaExisteNoTenant() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
    }

    @Nested
    @DisplayName("getHistoricoPmc")
    class GetHistoricoPmc {

        @BeforeEach
        void stubAtleta() {
            atletaExisteNoTenant();
        }

        @Test
        @DisplayName("mapeia a série PMC do intervalo")
        void mapeiaSerie() {
            when(metricasDiariasRepository.findByAtletaIdAndDataBetweenOrderByDataAsc(eq(atletaId), any(), any()))
                    .thenReturn(List.of(
                            metrica(LocalDate.of(2026, 6, 1), 50.0, 60.0, -10.0, 80),
                            metrica(LocalDate.of(2026, 6, 2), 51.0, 58.0, -7.0, 0)));

            List<PmcPontoDto> serie = service.getHistoricoPmc(atletaId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));

            assertThat(serie).hasSize(2);
            assertThat(serie.get(0)).isEqualTo(new PmcPontoDto(LocalDate.of(2026, 6, 1), 50.0, 60.0, -10.0, 80, "ACUMULANDO_FADIGA"));
            // statusForma resolvido pelo backend a partir do TSB (tsb=-10 → ACUMULANDO_FADIGA; tsb=-7 → FATIGADO)
            assertThat(serie).extracting(PmcPontoDto::statusForma)
                    .containsExactly("ACUMULANDO_FADIGA", "FATIGADO");
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
            assertThat(toCap.getValue()).isEqualTo(HOJE);
            assertThat(fromCap.getValue()).isEqualTo(HOJE.minusDays(90));
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

        @BeforeEach
        void stubAtleta() {
            atletaExisteNoTenant();
        }

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
            assertThat(z.z1() + z.z2() + z.z3() + z.z4() + z.z5()).isEqualTo(z.duracaoTotalSegundos());
            assertThat(z.duracaoTotalSegundos()).isEqualTo(900);
        }

        @Test
        @DisplayName("período sem treinos → zeros")
        void semTreinosZeros() {
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(atletaId), any(), any()))
                    .thenReturn(List.of());
            ZonaDistribuicaoDto z = service.getDistribuicaoZonas(atletaId, null, null);
            assertThat(z.duracaoTotalSegundos()).isZero();
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

        @Test
        @DisplayName("atleta de outro tenant → not found")
        void crossTenantNotFound() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getDistribuicaoZonas(atletaId, null, null))
                    .isInstanceOf(DomainNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getRecordes")
    class GetRecordes {

        @BeforeEach
        void stubAtleta() {
            atletaExisteNoTenant();
        }

        @Test
        @DisplayName("retorna o melhor tempo por distância (banda 10k)")
        void melhorTempoPorDistancia() {
            TreinoRealizado lento = treinoDist("10.0", Duration.ofMinutes(50), LocalDate.of(2026, 5, 1));
            TreinoRealizado rapido = treinoDist("10.1", Duration.ofMinutes(45), LocalDate.of(2026, 5, 8));
            TreinoRealizado foraDaBanda = treinoDist("15.0", Duration.ofMinutes(40), LocalDate.of(2026, 5, 9));
            when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId))
                    .thenReturn(List.of(lento, rapido, foraDaBanda));

            List<RecordeDto> recordes = service.getRecordes(atletaId);

            assertThat(recordes).extracting(RecordeDto::distancia).containsExactly("10k");
            RecordeDto rec10k = recordes.get(0);
            assertThat(rec10k.tempoSegundos()).isEqualTo(45 * 60);
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

        @BeforeEach
        void stubAtleta() {
            atletaExisteNoTenant();
            when(checkinProntidaoRepository.findByAtletaIdAndData(atletaId, HOJE, tenantId))
                    .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("com check-in de hoje: usa readinessScore/nivelProntidao persistidos, sem recalcular")
        void comCheckinDeHoje() {
            when(checkinProntidaoRepository.findByAtletaIdAndData(atletaId, HOJE, tenantId))
                    .thenReturn(Optional.of(checkin(HOJE, new BigDecimal("0.82"), NivelProntidao.PRONTO)));
            stubReadiness(10.0, 6); // fatores objetivos continuam populados no DTO mesmo com check-in

            ReadinessDto r = service.getReadinessAtual(atletaId);

            assertThat(r.score()).isEqualTo(82); // do check-in (0.82 * 100), não 75 (fórmula objetiva)
            assertThat(r.classificacao()).isEqualTo("PRONTO"); // nivelProntidao do check-in, não "BOM"
            assertThat(r.nota()).contains("check-in de hoje");
            assertThat(r.fatores().tsbProntidao()).isEqualTo(10.0); // fatores objetivos preservados como contexto
        }

        @ParameterizedTest(name = "readinessScore={0} → score={1}")
        @DisplayName("com check-in de hoje: score respeita os limites 0 e 100 (BVA)")
        @CsvSource({ "0.00, 0", "1.00, 100", "0.995, 100", "0.004, 0" })
        void comCheckinDeHojeScoreNosLimites(String readinessScore, int scoreEsperado) {
            when(checkinProntidaoRepository.findByAtletaIdAndData(atletaId, HOJE, tenantId))
                    .thenReturn(Optional.of(checkin(HOJE, new BigDecimal(readinessScore), NivelProntidao.PRONTO)));
            stubReadiness(10.0, 6);

            assertThat(service.getReadinessAtual(atletaId).score()).isEqualTo(scoreEsperado);
        }

        @ParameterizedTest(name = "nivelProntidao={0}")
        @DisplayName("com check-in de hoje: classificacao repassa cada valor do enum NivelProntidao")
        @EnumSource(NivelProntidao.class)
        void comCheckinDeHojeClassificacaoPorEnum(NivelProntidao nivel) {
            when(checkinProntidaoRepository.findByAtletaIdAndData(atletaId, HOJE, tenantId))
                    .thenReturn(Optional.of(checkin(HOJE, new BigDecimal("0.82"), nivel)));
            stubReadiness(10.0, 6);

            assertThat(service.getReadinessAtual(atletaId).classificacao()).isEqualTo(nivel.name());
        }

        @Test
        @DisplayName("check-in de outro dia (não hoje) é ignorado — mantém fallback objetivo")
        void checkinDeOutroDiaNaoConta() {
            when(checkinProntidaoRepository.findByAtletaIdAndData(atletaId, HOJE, tenantId))
                    .thenReturn(Optional.empty()); // repository já filtra por data — outro dia não retorna aqui
            stubReadiness(10.0, 6);

            ReadinessDto r = service.getReadinessAtual(atletaId);

            assertThat(r.score()).isEqualTo(75); // fallback objetivo, não o do check-in de outro dia
            assertThat(r.nota()).contains("faça seu check-in de hoje");
        }

        @Test
        @DisplayName("sem check-in de hoje e sem sinais objetivos: nota não nega mais a existência do check-in")
        void semCheckinESemSinaisNotaAjustada() {
            when(planoMetadadosRepository.findByAtletaId(atletaId)).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Optional.empty());

            ReadinessDto r = service.getReadinessAtual(atletaId);

            assertThat(r.nota()).contains("sem check-in subjetivo hoje");
        }

        @Test
        @DisplayName("compõe score a partir do TSB de prontidão (BOM)")
        void scoreComSinais() {
            stubReadiness(10.0, 6);
            ReadinessDto r = service.getReadinessAtual(atletaId);
            assertThat(r.score()).isEqualTo(75); // 60 + 1.5*10
            assertThat(r.classificacao()).isEqualTo("BOM");
            assertThat(r.fatores().tsbProntidao()).isEqualTo(10.0);
            assertThat(r.fatores().ultimoRpe()).isEqualTo(6);
        }

        @Test
        @DisplayName("RPE no limiar (==8) reduz o score")
        void rpeLimiarReduz() {
            stubReadiness(10.0, 8);
            assertThat(service.getReadinessAtual(atletaId).score()).isEqualTo(70); // 75 - 5
        }

        @Test
        @DisplayName("RPE alto (>8) reduz o score")
        void rpeAltoReduz() {
            stubReadiness(10.0, 9);
            assertThat(service.getReadinessAtual(atletaId).score()).isEqualTo(70);
        }

        @Test
        @DisplayName("TSB alto → OTIMO")
        void otimo() {
            stubReadiness(14.0, 5);
            assertThat(service.getReadinessAtual(atletaId).classificacao()).isEqualTo("OTIMO"); // 81
        }

        @Test
        @DisplayName("TSB levemente negativo → MODERADO")
        void moderado() {
            stubReadiness(-10.0, 5);
            assertThat(service.getReadinessAtual(atletaId).classificacao()).isEqualTo("MODERADO"); // 45
        }

        @Test
        @DisplayName("TSB muito negativo → BAIXO")
        void baixo() {
            stubReadiness(-20.0, 5);
            assertThat(service.getReadinessAtual(atletaId).classificacao()).isEqualTo("BAIXO"); // 30
        }

        @Test
        @DisplayName("sem sinais → score nulo, sem erro")
        void semSinaisDefault() {
            when(planoMetadadosRepository.findByAtletaId(atletaId)).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Optional.empty());

            ReadinessDto r = service.getReadinessAtual(atletaId);

            assertThat(r.score()).isNull();
            assertThat(r.classificacao()).isEqualTo("INDISPONIVEL");
            assertThat(r.nota()).isNotBlank();
        }

        private void stubReadiness(Double tsbProntidao, Integer rpe) {
            when(planoMetadadosRepository.findByAtletaId(atletaId)).thenReturn(Optional.of(meta(tsbProntidao, 50.0, 44.0)));
            when(treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(Optional.of(treinoRpe(rpe)));
        }

        private CheckinProntidao checkin(LocalDate data, BigDecimal readinessScore, NivelProntidao nivelProntidao) {
            return CheckinProntidao.builder()
                    .id(UUID.randomUUID())
                    .data(data)
                    .readinessScore(readinessScore)
                    .nivelProntidao(nivelProntidao)
                    .build();
        }
    }

    @Nested
    @DisplayName("getHome")
    class GetHome {

        @BeforeEach
        void stubAtleta() {
            atletaExisteNoTenant();
        }

        @Test
        @DisplayName("inclui próximo treino (janela hoje..+14) e métricas-chave")
        void comProximoTreino() {
            TreinoPlanejado tp = new TreinoPlanejado();
            tp.setDataTreino(HOJE.plusDays(1));
            tp.setTipoTreino(TipoTreino.INTERVALADO);
            tp.setDescricao("6x800m");
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE, HOJE.plusDays(14)))
                    .thenReturn(List.of(tp));
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId))
                    .thenReturn(Optional.of(metrica(HOJE, 52.0, 44.0, 8.0, 0)));

            AtletaHomeDto home = service.getHome(atletaId);

            assertThat(home.proximoTreino()).isNotNull();
            assertThat(home.proximoTreino().tipoTreino()).isEqualTo("INTERVALADO");
            assertThat(home.metricasChave().ctl()).isEqualTo(52.0);
        }

        @Test
        @DisplayName("expõe etapas (com bloco), duração em minutos inteiros e zona alvo do próximo treino")
        void comEtapasDuracaoEZona() {
            TreinoPlanejado tp = new TreinoPlanejado();
            tp.setDataTreino(HOJE);
            tp.setTipoTreino(TipoTreino.INTERVALADO);
            tp.setDescricao("2x(4' forte / 2' leve)");
            tp.setDuracaoMin(Duration.ofMinutes(45));
            tp.setZonaAlvo("Z4");
            tp.setTssPlanejado(70);
            tp.setIntensidadePlanejada(0.95);
            UUID bloco = UUID.randomUUID();
            tp.setEtapas(List.of(
                    EtapaTreino.builder().ordem(1).tipoEtapa("AQUECIMENTO").duracaoMin(10).build(),
                    EtapaTreino.builder().ordem(2).tipoEtapa("ESFORCO").duracaoMin(4).blocoId(bloco).blocoRepeticoes(2).build(),
                    EtapaTreino.builder().ordem(3).tipoEtapa("RECUPERACAO").duracaoMin(2).blocoId(bloco).blocoRepeticoes(2).build(),
                    EtapaTreino.builder().ordem(4).tipoEtapa("ESFORCO").duracaoMin(4).blocoId(bloco).blocoRepeticoes(2).build(),
                    EtapaTreino.builder().ordem(5).tipoEtapa("RECUPERACAO").duracaoMin(2).blocoId(bloco).blocoRepeticoes(2).build()));
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE, HOJE.plusDays(14)))
                    .thenReturn(List.of(tp));
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId)).thenReturn(Optional.empty());

            AtletaHomeDto.ProximoTreino proximo = service.getHome(atletaId).proximoTreino();

            assertThat(proximo.duracaoMin()).isEqualTo(45);
            assertThat(proximo.zonaAlvo()).isEqualTo("Z4");
            assertThat(proximo.tssPlanejado()).isEqualTo(70);
            assertThat(proximo.intensidadePlanejada()).isEqualTo(0.95);
            assertThat(proximo.etapas()).hasSize(5);
            assertThat(proximo.etapas().get(1).blocoId()).isEqualTo(bloco);
            assertThat(proximo.etapas().get(1).blocoRepeticoes()).isEqualTo(2);
            assertThat(proximo.etapas().get(0).blocoId()).isNull();
        }

        @Test
        @DisplayName("Duration.ZERO é a sentinela de 'não prescrita' → duracaoMin omitida, não 0")
        void duracaoZeroEhAusencia() {
            TreinoPlanejado tp = new TreinoPlanejado();
            tp.setDataTreino(HOJE);
            tp.setTipoTreino(TipoTreino.FACIL);
            tp.setDuracaoMin(Duration.ZERO);
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE, HOJE.plusDays(14)))
                    .thenReturn(List.of(tp));
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId)).thenReturn(Optional.empty());

            assertThat(service.getHome(atletaId).proximoTreino().duracaoMin()).isNull();
        }

        @Test
        @DisplayName("treino sem etapas nem duração → campos omitidos (null), sem lista vazia inventada")
        void semEtapas() {
            TreinoPlanejado tp = new TreinoPlanejado();
            tp.setDataTreino(HOJE);
            tp.setTipoTreino(TipoTreino.FACIL);
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE, HOJE.plusDays(14)))
                    .thenReturn(List.of(tp));
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId)).thenReturn(Optional.empty());

            AtletaHomeDto.ProximoTreino proximo = service.getHome(atletaId).proximoTreino();

            assertThat(proximo.etapas()).isNull();
            assertThat(proximo.duracaoMin()).isNull();
            assertThat(proximo.zonaAlvo()).isNull();
        }

        @Test
        @DisplayName("sem próximo treino → omite, mantém métricas")
        void semProximoTreino() {
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(eq(atletaId), any(), any())).thenReturn(List.of());
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId))
                    .thenReturn(Optional.of(metrica(HOJE, 52.0, 44.0, 8.0, 0)));

            AtletaHomeDto home = service.getHome(atletaId);

            assertThat(home.proximoTreino()).isNull();
            assertThat(home.metricasChave().tsb()).isEqualTo(8.0);
        }

        @Test
        @DisplayName("'hoje' vem no fuso do atleta: às 23:50 de Manaus o servidor em UTC já virou o dia")
        void hojeNoFusoDoAtleta() {
            // 03:50Z do dia 18 = 23:50 do dia 17 em Manaus (UTC−4); o clock do setUp é UTC
            Clock madrugadaUtc = Clock.fixed(Instant.parse("2026-06-18T03:50:00Z"), ZoneOffset.UTC);
            service = new AtletaProgressServiceImpl(
                    atletaRepository, usuarioRepository, metricasDiariasRepository, treinoRealizadoRepository,
                    treinoPlanejadoRepository, planoMetadadosRepository, zonaTreinoService, principalResolver,
                    checkinProntidaoRepository, Mappers.getMapper(EtapaMapper.class), madrugadaUtc,
                    new AtletaHojeResolver(madrugadaUtc));
            atleta.setTimezone("America/Manaus");
            TreinoPlanejado deHoje = new TreinoPlanejado();
            deHoje.setDataTreino(HOJE);
            deHoje.setTipoTreino(TipoTreino.FACIL);
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, HOJE, HOJE.plusDays(14)))
                    .thenReturn(List.of(deHoje));
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId)).thenReturn(Optional.empty());

            AtletaHomeDto home = service.getHome(atletaId);

            assertThat(home.hoje()).isEqualTo(HOJE);
            assertThat(home.proximoTreino().data()).isEqualTo(HOJE);
        }

        @Test
        @DisplayName("realizado de hoje entra com origem, RPE e carimbo de feedback; o mais recente vence")
        void comRealizadoHoje() {
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(eq(atletaId), any(), any())).thenReturn(List.of());
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId)).thenReturn(Optional.empty());
            TreinoRealizado antigo = realizado(HOJE, FonteDados.MANUAL, 6, LocalDateTime.of(2026, 6, 17, 7, 0));
            TreinoRealizado recente = realizado(HOJE, FonteDados.INTERVALS_ICU, null, LocalDateTime.of(2026, 6, 17, 9, 0));
            recente.setDistanciaKm(new BigDecimal("12.5"));
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreino(atletaId, HOJE))
                    .thenReturn(List.of(antigo, recente));

            AtletaHomeDto.RealizadoHoje hoje = service.getHome(atletaId).realizadoHoje();

            assertThat(hoje.id()).isEqualTo(recente.getId());
            assertThat(hoje.fonteDados()).isEqualTo("INTERVALS_ICU");
            assertThat(hoje.tipoTreino()).isEqualTo("FACIL");
            assertThat(hoje.duracaoMin()).isEqualTo(50);
            assertThat(hoje.distanciaKm()).isEqualByComparingTo("12.5");
            assertThat(hoje.percepcaoEsforco()).isNull();
            assertThat(hoje.feedbackRegistradoEm()).isNull();
        }

        @Test
        @DisplayName("sem realizado hoje → realizadoHoje omitido")
        void semRealizadoHoje() {
            when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(eq(atletaId), any(), any())).thenReturn(List.of());
            when(metricasDiariasRepository.findLatestByAtletaId(atletaId)).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreino(atletaId, HOJE)).thenReturn(List.of());

            assertThat(service.getHome(atletaId).realizadoHoje()).isNull();
        }

        private TreinoRealizado realizado(LocalDate data, FonteDados fonte, Integer rpe, LocalDateTime criadoEm) {
            TreinoRealizado tr = new TreinoRealizado();
            tr.setId(UUID.randomUUID());
            tr.setDataTreino(data);
            tr.setTipoTreino(TipoTreino.FACIL);
            tr.setFonteDados(fonte);
            tr.setDuracaoMin(Duration.ofMinutes(50));
            tr.setPercepcaoEsforco(rpe);
            tr.setCriadoEm(criadoEm);
            return tr;
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
        @DisplayName("usuário do token não encontrado no tenant → not found")
        void usuarioNaoEncontrado() {
            when(principalResolver.getCurrentSubject()).thenReturn("sub-x");
            when(usuarioRepository.findByKeycloakIdAndAssessoria_Id("sub-x", tenantId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.resolverAtletaIdAtual()).isInstanceOf(DomainNotFoundException.class);
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

    @Nested
    @DisplayName("getAderenciaSemanal")
    class GetAderenciaSemanal {

        // HOJE = 2026-06-17 (quarta), inicioSemanaAtual = 2026-06-15 (seg)
        // semanas=8 → dataInicio = 2026-06-15 - 7 semanas = 2026-04-27
        private final LocalDate DATA_INICIO = LocalDate.of(2026, 4, 27);

        @BeforeEach
        void stubAtleta() {
            atletaExisteNoTenant();
        }

        @Test
        @DisplayName("atleta não existe no tenant → DomainNotFoundException")
        void atletaNaoExiste() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getAderenciaSemanal(atletaId, 8))
                    .isInstanceOf(DomainNotFoundException.class);
        }

        @Test
        @DisplayName("nenhum treino planejado no período → lista vazia")
        void semTreinos() {
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(atletaId, tenantId, DATA_INICIO))
                    .thenReturn(List.of());

            assertThat(service.getAderenciaSemanal(atletaId, 8)).isEmpty();
        }

        @Test
        @DisplayName("treinos na mesma semana — calcula percentual corretamente")
        void calculaPercentualSemanal() {
            // semana 2026-06-15 (seg): Jun 17 (qua) e Jun 18 (qui) → 2 planejados, 1 realizado → 50%
            TreinoPlanejado tp1 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 17), true);
            TreinoPlanejado tp2 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 18), false);
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(atletaId, tenantId, DATA_INICIO))
                    .thenReturn(List.of(tp1, tp2));

            var resultado = service.getAderenciaSemanal(atletaId, 8);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).semanaInicio()).isEqualTo(LocalDate.of(2026, 6, 15));
            assertThat(resultado.get(0).totalPlanejado()).isEqualTo(2);
            assertThat(resultado.get(0).totalRealizado()).isEqualTo(1);
            assertThat(resultado.get(0).percentual()).isEqualTo(50);
        }

        @Test
        @DisplayName("treinos em semanas distintas — resultado ordenado por semanaInicio ASC")
        void multiplas_semanas_ordenadas() {
            // semana A começa 2026-06-08 (seg): Jun 9 (ter), Jun 10 (qua), Jun 11 (qui) — 3 planejados, 2 realizados → 67%
            TreinoPlanejado tpA1 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 9), true);
            TreinoPlanejado tpA2 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 10), true);
            TreinoPlanejado tpA3 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 11), false);
            // semana B começa 2026-06-15 (seg): Jun 16 (ter), Jun 17 (qua) — 2 planejados, 2 realizados → 100%
            TreinoPlanejado tpB1 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 16), true);
            TreinoPlanejado tpB2 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 17), true);
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(atletaId, tenantId, DATA_INICIO))
                    .thenReturn(List.of(tpA1, tpA2, tpA3, tpB1, tpB2));

            var resultado = service.getAderenciaSemanal(atletaId, 8);

            assertThat(resultado).hasSize(2);
            assertThat(resultado.get(0).semanaInicio()).isEqualTo(LocalDate.of(2026, 6, 8));
            assertThat(resultado.get(0).totalPlanejado()).isEqualTo(3);
            assertThat(resultado.get(0).totalRealizado()).isEqualTo(2);
            assertThat(resultado.get(0).percentual()).isEqualTo(67);
            assertThat(resultado.get(1).semanaInicio()).isEqualTo(LocalDate.of(2026, 6, 15));
            assertThat(resultado.get(1).percentual()).isEqualTo(100);
        }

        @Test
        @DisplayName("todos os treinos sem realizado → percentual 0, lista retornada (tem planejados)")
        void semNenhumRealizado() {
            TreinoPlanejado tp = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 16), false);
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(atletaId, tenantId, DATA_INICIO))
                    .thenReturn(List.of(tp));

            var resultado = service.getAderenciaSemanal(atletaId, 8);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).percentual()).isZero();
            assertThat(resultado.get(0).totalRealizado()).isZero();
        }
    }

    // ===== helpers =====

    private TreinoPlanejado treinoPlanejadoComRealizado(LocalDate data, boolean realizado) {
        TreinoPlanejado tp = new TreinoPlanejado();
        tp.setDataTreino(data);
        if (realizado) {
            TreinoRealizado tr = new TreinoRealizado();
            tp.setTreinoRealizado(tr);
        }
        return tp;
    }

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
