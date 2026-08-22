package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.dto.intervalsicu.IcuActivityIntervalDto;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntervalsIcuActivityMapperTest {

    private final IntervalsIcuActivityMapper mapper = new IntervalsIcuActivityMapper();

    @Nested
    @DisplayName("map")
    class Map {

        @Test
        @DisplayName("happy path: mapeia todos os campos de uma activity completa")
        void happyPath() {
            IcuActivityDto dto = activityCompleta();
            Atleta atleta = atleta();

            TreinoRealizado treino = mapper.map(dto, atleta);

            assertThat(treino.getFonteDados()).isEqualTo(FonteDados.INTERVALS_ICU);
            assertThat(treino.getExternalId()).isEqualTo("i86400275");
            assertThat(treino.getStatus()).isEqualTo(TreinoExecucaoStatus.REALIZADO);
            assertThat(treino.getCriadoPor()).isEqualTo("INTERVALS_ICU");
            assertThat(treino.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.PENDENTE);
            assertThat(treino.getSincronizadoEm()).isNotNull();
            assertThat(treino.getAtleta()).isSameAs(atleta);
            assertThat(treino.getTenantId()).isEqualTo(atleta.getAssessoria().getId());
            assertThat(treino.getDataTreino()).isEqualTo(LocalDate.of(2026, 7, 16));
            assertThat(treino.getDistanciaKm().doubleValue()).isEqualTo(5.0);
            assertThat(treino.getDuracaoMin()).isEqualTo(Duration.ofSeconds(1800));
            assertThat(treino.getElapsedTimeSeg()).isEqualTo(1850);
            assertThat(treino.getFcMedia()).isEqualTo(145);
            assertThat(treino.getFcMax()).isEqualTo(168);
            assertThat(treino.getCadenciaMedia()).isEqualTo(161);
            assertThat(treino.getPercepcaoEsforco()).isEqualTo(6);
            assertThat(treino.getElevacaoGanhoMetros()).isEqualTo(42);
            assertThat(treino.getDeviceName()).isEqualTo("Garmin Forerunner 965");
            assertThat(treino.getVelocidadeMedia()).isEqualTo(10.01); // 2.78 m/s * 3.6 = km/h (achado do QA gate)
            assertThat(treino.getMetadadosSincronizacao())
                    .contains("\"icuTrainingLoad\":55")
                    .contains("\"calories\":420")
                    .contains("\"totalElevationGain\":42.0")
                    .contains("\"deviceName\":\"Garmin Forerunner 965\"");
        }

        @Test
        @DisplayName("deviceName com aspas e barra invertida gera metadadosSincronizacao JSON válido (achado do QA gate)")
        void deviceNameComCaracteresEspeciaisGeraJsonValido() throws com.fasterxml.jackson.core.JsonProcessingException {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00", null,
                    1800, 1850, 5000.0, null, null, null, null, null, null, null,
                    "Garmin \"Watch\" Pro\\v2", null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(treino.getMetadadosSincronizacao());
            assertThat(node.get("deviceName").asText()).isEqualTo("Garmin \"Watch\" Pro\\v2");
        }

        @Test
        @DisplayName("pace derivado de moving_time/distance tem prioridade sobre average_speed")
        void paceComPrioridadeMovingTimeDistance() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00", null,
                    1800, 1850, 5000.0, 3.0, // average_speed diferente do que moving_time/distance dariam
                    null, null, null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            // moving_time/distance: 1800s / 5km = 360s/km = 6min/km
            assertThat(treino.getPaceMedia()).isEqualTo(Duration.ofSeconds(360));
        }

        @Test
        @DisplayName("pace cai para average_speed só quando moving_time/distance estão ausentes")
        void paceFallbackParaAverageSpeed() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00", null,
                    null, null, null, 2.5, // 2.5 m/s = 400s/km
                    null, null, null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getPaceMedia()).isEqualTo(Duration.ofSeconds(400));
        }

        @Test
        @DisplayName("pace nulo quando não há moving_time/distance nem average_speed")
        void paceNuloSemDadosSuficientes() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00", null,
                    null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getPaceMedia()).isNull();
        }

        @Test
        @DisplayName("moving_time ausente mapeia para Duration.ZERO (coluna duracao_min é NOT NULL) — nunca null")
        void movingTimeAusenteViraDurationZero() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Treadmill", "Esteira", "2026-07-16T06:00:00", null,
                    null, null, 5000.0, null,
                    null, null, null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getDuracaoMin()).isEqualTo(Duration.ZERO).isNotNull();
        }

        @Test
        @DisplayName("distance ausente mapeia para null literal (coluna distancia_km é nullable)")
        void distanceAusenteViraNullLiteral() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00", null,
                    1800, 1850, null, null,
                    null, null, null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getDistanciaKm()).isNull();
        }

        @Test
        @DisplayName("campos opcionais ausentes viram null, sem lançar exceção")
        void camposOpcionaisAusentesViramNull() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00", null,
                    1800, null, 5000.0, null,
                    null, null, null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getElapsedTimeSeg()).isNull();
            assertThat(treino.getFcMedia()).isNull();
            assertThat(treino.getFcMax()).isNull();
            assertThat(treino.getPercepcaoEsforco()).isNull();
            assertThat(treino.getElevacaoGanhoMetros()).isNull();
            assertThat(treino.getDeviceName()).isNull();
        }

        @Test
        @DisplayName("RPE presente é arredondado e mapeado para percepcaoEsforco")
        void rpePresenteEhMapeado() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00", null,
                    1800, 1850, 5000.0, null,
                    null, null, null, null, 7.6, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getPercepcaoEsforco()).isEqualTo(8);
        }

        @Test
        @DisplayName("RPE ausente mantém percepcaoEsforco null")
        void rpeAusenteMantemNull() {
            TreinoRealizado treino = mapper.map(activityMinima(), atleta());

            assertThat(treino.getPercepcaoEsforco()).isNull();
        }

        @Test
        @DisplayName("cadência dobra o valor de perna única do intervals.icu (confirmado contra payload real do gate 3.0)")
        void cadenciaDobraValorRealDoGate() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i166338796", "i641775", "Run", "Taboão da Serra - 8.00Km CONTINUO", "2026-07-16T08:12:19", null,
                    3108, 3110, 8009.18, 2.576, 152.0, 164.0, 69.87482, 80.822655, 2.0, 58,
                    "Garmin Forerunner 970", 666, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getCadenciaMedia()).isEqualTo(162);
        }

        @Test
        @DisplayName("cadência fora da faixa 60-200 após dobrar vira null (dado degenerado)")
        void cadenciaForaDaFaixaViraNull() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00", null,
                    1800, 1850, 5000.0, null, null, null, null, 250.0, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getCadenciaMedia()).isNull();
        }

        @Test
        @DisplayName("cadência ausente mantém cadenciaMedia null")
        void cadenciaAusenteMantemNull() {
            TreinoRealizado treino = mapper.map(activityMinima(), atleta());

            assertThat(treino.getCadenciaMedia()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"Run", "TrailRun", "VirtualRun", "Treadmill"})
        @DisplayName("modalidades de corrida aceitas")
        void modalidadesAceitas(String type) {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", type, "Atividade", "2026-07-16T06:00:00", null,
                    1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino).isNotNull();
            assertThat(treino.getExternalId()).isEqualTo("i1");
        }

        @Test
        @DisplayName("modalidade não suportada (ex.: Ride) lança DomainRuleViolationException")
        void modalidadeNaoSuportadaLancaExcecao() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Ride", "Pedal", "2026-07-16T06:00:00", null,
                    1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null, null, null);

            assertThatThrownBy(() -> mapper.map(dto, atleta()))
                    .isInstanceOf(DomainRuleViolationException.class);
        }

        @Test
        @DisplayName("dto nulo lança IllegalArgumentException")
        void dtoNuloLancaExcecao() {
            assertThatThrownBy(() -> mapper.map(null, atleta()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("atleta nulo lança IllegalArgumentException")
        void atletaNuloLancaExcecao() {
            assertThatThrownBy(() -> mapper.map(activityMinima(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("virada de dia: activity às 23:45 local não vaza para o dia seguinte por fuso do servidor")
        void viradaDeDiaProximaAMeiaNoite() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida noturna", "2026-07-16T23:45:00", null,
                    1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getDataTreino()).isEqualTo(LocalDate.of(2026, 7, 16));
        }

        @Test
        @DisplayName("virada de dia: activity às 00:15 local não vaza para o dia anterior por fuso do servidor")
        void viradaDeDiaLogoApósAMeiaNoite() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida de madrugada", "2026-07-16T00:15:00", null,
                    1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getDataTreino()).isEqualTo(LocalDate.of(2026, 7, 16));
        }
    }

    @Nested
    @DisplayName("isModalidadeSuportada")
    class IsModalidadeSuportada {

        @ParameterizedTest
        @ValueSource(strings = {"Run", "TrailRun", "VirtualRun", "Treadmill"})
        @DisplayName("true para as quatro modalidades aceitas")
        void trueParaAceitas(String type) {
            assertThat(mapper.isModalidadeSuportada(type)).isTrue();
        }

        @Test
        @DisplayName("false para modalidade não suportada")
        void falseParaNaoSuportada() {
            assertThat(mapper.isModalidadeSuportada("Ride")).isFalse();
        }

        @Test
        @DisplayName("false para null")
        void falseParaNull() {
            assertThat(mapper.isModalidadeSuportada(null)).isFalse();
        }
    }

    private IcuActivityDto activityCompleta() {
        return new IcuActivityDto(
                "i86400275", "i641775", "Run", "Corrida matinal", "2026-07-16T06:30:00", null,
                1800, 1850, 5000.0, 2.78, 145.0, 168.0, 42.0, 80.5, 6.0, 55,
                "Garmin Forerunner 965", 420, null, null);
    }

    private IcuActivityDto activityMinima() {
        return new IcuActivityDto(
                "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00", null,
                1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null, null, null);
    }

    private Atleta atleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());

        Atleta atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);
        return atleta;
    }
    @Nested
    @DisplayName("mapEtapas")
    class MapEtapas {

        @Test
        @DisplayName("descarta o intervalo degenerado: 17 no payload real, 16 etapas")
        void descartaIntervaloDegenerado() {
            List<EtapaRealizada> etapas = etapasDoPayloadReal();

            // O payload tem 17 intervalos; o de 1 s / 2,4 m e lixo. icu_lap_count confirma: 16.
            assertThat(etapas).hasSize(16);
            assertThat(etapas).noneMatch(e -> e.getDuracao().getSeconds() < 5);
        }

        @Test
        @DisplayName("ordem e splitIndex vem da posicao, nunca do id opaco do payload")
        void ordemVemDaPosicao() {
            List<EtapaRealizada> etapas = etapasDoPayloadReal();

            assertThat(etapas).extracting(EtapaRealizada::getOrdem)
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 16).boxed().toList());
            assertThat(etapas).extracting(EtapaRealizada::getSplitIndex)
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 16).boxed().toList());
        }

        @Test
        @DisplayName("mapeia as metricas basicas da primeira volta do payload real")
        void mapeiaMetricasBasicas() {
            EtapaRealizada etapa = etapasDoPayloadReal().get(0);

            assertThat(etapa.getDistanciaKm()).isEqualByComparingTo(new BigDecimal("1.002"));
            assertThat(etapa.getDuracao()).isEqualTo(Duration.ofSeconds(388));
            assertThat(etapa.getFcMedia()).isEqualTo(127);
            assertThat(etapa.getFcMax()).isEqualTo(145);
            assertThat(etapa.getTipoEtapa()).isEqualTo("PRINCIPAL");
            assertThat(etapa.getDescricao()).isEqualTo("Lap 1");
            assertThat(etapa.getPotenciaMedia()).isNull();
        }

        @Test
        @DisplayName("velocidade converte m/s para km/h")
        void velocidadeEmKmh() {
            EtapaRealizada etapa = etapasDoPayloadReal().get(0);

            // 2.582268 m/s * 3.6 = 9.2961... -> 9.30
            assertThat(etapa.getVelocidadeMedia()).isEqualByComparingTo(new BigDecimal("9.30"));
        }

        @Test
        @DisplayName("cadencia dobra a de perna unica e sanitiza fora de 60-200")
        void cadenciaDobrada() {
            EtapaRealizada etapa = etapasDoPayloadReal().get(0);

            // 81.3866 de uma perna -> 162.77 -> 163 total
            assertThat(etapa.getCadenciaMedia()).isEqualTo(163);

            assertThat(umaEtapa(intervalo().comCadencia(20.0)).getCadenciaMedia()).isNull();
            assertThat(umaEtapa(intervalo().comCadencia(120.0)).getCadenciaMedia()).isNull();
        }

        @Test
        @DisplayName("pace vem de moving_time/distancia, com average_speed so como fallback")
        void pacePorMovingTime() {
            EtapaRealizada etapa = etapasDoPayloadReal().get(0);

            // 388 s / 1.00192 km = 387.3 -> 6:27, batendo com a coluna Ritmo do CSV
            assertThat(etapa.getPaceMedia()).isEqualTo(Duration.ofSeconds(387));

            // Volta com parada: 397 s de movimento / 0.99873 km -> 6:38, tambem batendo com o CSV.
            // Se o pace usasse elapsed (614 s), daria 10:15 — um treino inteiro reprovado a toa.
            assertThat(etapasDoPayloadReal().get(7).getPaceMedia()).isEqualTo(Duration.ofSeconds(398));
        }

        @Test
        @DisplayName("duracao vem de moving_time, NUNCA de elapsed_time")
        void duracaoUsaMovingTime() {
            // Volta real com o atleta parado: moving 397, elapsed 614. Gravar 614 injetaria 217 s
            // de tempo parado no TSS, no tempo em zona e no decoupling.
            EtapaRealizada comParada = etapasDoPayloadReal().get(7);

            assertThat(comParada.getDuracao()).isEqualTo(Duration.ofSeconds(397));
            assertThat(comParada.getTempoMovimento()).isEqualTo(Duration.ofSeconds(397));
        }

        @Test
        @DisplayName("elevacao: ganho mapeado, perda null — a fonte nao expoe perda por intervalo")
        void elevacaoSoGanho() {
            EtapaRealizada etapa = etapasDoPayloadReal().get(0);

            assertThat(etapa.getElevacaoGanhoMetros()).isEqualTo(2);
            assertThat(etapa.getElevacaoPerdaMetros()).isNull();
        }

        @Test
        @DisplayName("running dynamics: oscilacao vertical converte mm para cm")
        void runningDynamics() {
            EtapaRealizada etapa = etapasDoPayloadReal().get(0);

            assertThat(etapa.getPassadaMediaM()).isEqualByComparingTo(new BigDecimal("0.95"));
            assertThat(etapa.getGctMedioMs()).isEqualTo(250);
            assertThat(etapa.getGctEquilibrioPct()).isEqualByComparingTo(new BigDecimal("51.1"));
            // 113.24149 mm -> 11.3 cm. Sem a divisao o valor estoura NUMERIC(4,1).
            assertThat(etapa.getOscilacaoVerticalCm()).isEqualByComparingTo(new BigDecimal("11.3"));
            assertThat(etapa.getProporcaoVerticalPct()).isEqualByComparingTo(new BigDecimal("12.0"));
            assertThat(etapa.getTemperaturaMediaC()).isEqualByComparingTo(new BigDecimal("24.4"));
        }

        @Test
        @DisplayName("zona e intensidade diretas; inclinacao converte fracao para percentual")
        void zonaIntensidadeInclinacao() {
            EtapaRealizada etapa = etapasDoPayloadReal().get(0);

            assertThat(etapa.getZona()).isEqualTo(1);
            assertThat(etapa.getIntensidadePct()).isEqualByComparingTo(new BigDecimal("75.00"));
            // 0.0011977126 e 0,1% — sem o x100 toda inclinacao viraria 0,0
            assertThat(etapa.getInclinacaoMediaPct()).isEqualByComparingTo(new BigDecimal("0.1"));

            EtapaRealizada descida = umaEtapa(intervalo().comInclinacao(-0.008186669));
            assertThat(descida.getInclinacaoMediaPct()).isEqualByComparingTo(new BigDecimal("-0.8"));
        }

        @ParameterizedTest
        @CsvSource({"WORK,PRINCIPAL", "RECOVERY,RECUPERACAO", "WARMUP,AQUECIMENTO", "COOLDOWN,DESAQUECIMENTO"})
        @DisplayName("tipoEtapa mapeia o vocabulario da fonte")
        void tipoEtapaMapeado(String type, String esperado) {
            assertThat(umaEtapa(intervalo().comTipo(type)).getTipoEtapa()).isEqualTo(esperado);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"ALGO_NOVO", ""})
        @DisplayName("tipo desconhecido ou ausente vira null, nunca chute")
        void tipoDesconhecidoViraNull(String type) {
            assertThat(umaEtapa(intervalo().comTipo(type)).getTipoEtapa()).isNull();
        }

        @ParameterizedTest
        @CsvSource({"5, 20", "5, 1000", "300, 20"})
        @DisplayName("BVA: 5 s e 20 m exatos passam pelo filtro de descarte")
        void limiaresExatosPassam(int movingTime, double distancia) {
            assertThat(etapasDe(intervalo().comTempo(movingTime).comDistancia(distancia))).hasSize(1);
        }

        @ParameterizedTest
        @CsvSource({"4, 1000", "300, 19.9", "1, 2.4"})
        @DisplayName("BVA: abaixo de 5 s ou de 20 m e descartado")
        void abaixoDoLimiarDescartado(int movingTime, double distancia) {
            assertThat(etapasDe(intervalo().comTempo(movingTime).comDistancia(distancia))).isEmpty();
        }

        @Test
        @DisplayName("lista de intervalos nula ou vazia gera treino sem etapas, sem NPE")
        void listaAusenteNaoQuebra() {
            assertThat(mapper.map(activityComIntervalos(null), atleta()).getEtapasRealizadas()).isEmpty();
            assertThat(mapper.map(activityComIntervalos(List.of()), atleta()).getEtapasRealizadas()).isEmpty();
        }

        @Test
        @DisplayName("label longa da fonte e truncada — senao derruba o treino inteiro no flush")
        void labelLongaEhTruncada() {
            String label = "x".repeat(700);

            EtapaRealizada etapa = umaEtapa(intervalo().comLabel(label));

            assertThat(etapa.getDescricao()).hasSize(500);
        }

        @Test
        @DisplayName("label no limite de 500 passa intacta; ausente ou em branco vira \"Lap N\"")
        void labelNoLimiteEAusente() {
            assertThat(umaEtapa(intervalo().comLabel("y".repeat(500))).getDescricao()).hasSize(500);
            assertThat(umaEtapa(intervalo().comLabel(null)).getDescricao()).isEqualTo("Lap 1");
            assertThat(umaEtapa(intervalo().comLabel("   ")).getDescricao()).isEqualTo("Lap 1");
        }

        @Test
        @DisplayName("cada etapa aponta de volta para o treino que a contem")
        void backReferenceSetado() {
            TreinoRealizado treino = mapper.map(payloadReal(), atleta());

            assertThat(treino.getEtapasRealizadas())
                    .isNotEmpty()
                    .allSatisfy(e -> assertThat(e.getTreinoRealizado()).isSameAs(treino));
        }
    }

    // ===== helpers de etapa =====

    private List<EtapaRealizada> etapasDoPayloadReal() {
        return mapper.map(payloadReal(), atleta()).getEtapasRealizadas();
    }

    private List<EtapaRealizada> etapasDe(IntervaloBuilder builder) {
        return mapper.map(activityComIntervalos(List.of(builder.build())), atleta()).getEtapasRealizadas();
    }

    private EtapaRealizada umaEtapa(IntervaloBuilder builder) {
        return etapasDe(builder).get(0);
    }

    private IntervaloBuilder intervalo() {
        return new IntervaloBuilder();
    }

    private IcuActivityDto payloadReal() {
        try (java.io.InputStream in = getClass()
                .getResourceAsStream("/fixtures/intervalsicu/activity-com-intervalos.json")) {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(in, IcuActivityDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("fixture do payload real indisponivel", e);
        }
    }

    private IcuActivityDto activityComIntervalos(List<IcuActivityIntervalDto> intervalos) {
        return new IcuActivityDto(
                "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00", null,
                1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null,
                null, intervalos);
    }

    /** Intervalo valido por padrao; cada `com...` isola a variavel sob teste. */
    private static final class IntervaloBuilder {
        private String type = "WORK";
        private Double distance = 1000.0;
        private Integer movingTime = 300;
        private Double speed = 3.0;
        private Double cadence = 82.0;
        private Double gradient = 0.0;
        private String label = null;

        IntervaloBuilder comTipo(String v) { this.type = v; return this; }
        IntervaloBuilder comDistancia(Double v) { this.distance = v; return this; }
        IntervaloBuilder comTempo(Integer v) { this.movingTime = v; return this; }
        IntervaloBuilder comCadencia(Double v) { this.cadence = v; return this; }
        IntervaloBuilder comInclinacao(Double v) { this.gradient = v; return this; }
        IntervaloBuilder comLabel(String v) { this.label = v; return this; }

        IcuActivityIntervalDto build() {
            return new IcuActivityIntervalDto(
                    1L, type, label, 0, distance, movingTime, movingTime, speed,
                    140.0, 150.0, cadence, null, 1.0, 0.95,
                    250.0, 50.0, 100.0, 10.0, 20.0,
                    2, 80.0, gradient);
        }
    }
}
