package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
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
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.LocalDate;
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
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00",
                    1800, 1850, 5000.0, null, null, null, null, null, null, null,
                    "Garmin \"Watch\" Pro\\v2", null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(treino.getMetadadosSincronizacao());
            assertThat(node.get("deviceName").asText()).isEqualTo("Garmin \"Watch\" Pro\\v2");
        }

        @Test
        @DisplayName("pace derivado de moving_time/distance tem prioridade sobre average_speed")
        void paceComPrioridadeMovingTimeDistance() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00",
                    1800, 1850, 5000.0, 3.0, // average_speed diferente do que moving_time/distance dariam
                    null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            // moving_time/distance: 1800s / 5km = 360s/km = 6min/km
            assertThat(treino.getPaceMedia()).isEqualTo(Duration.ofSeconds(360));
        }

        @Test
        @DisplayName("pace cai para average_speed só quando moving_time/distance estão ausentes")
        void paceFallbackParaAverageSpeed() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00",
                    null, null, null, 2.5, // 2.5 m/s = 400s/km
                    null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getPaceMedia()).isEqualTo(Duration.ofSeconds(400));
        }

        @Test
        @DisplayName("pace nulo quando não há moving_time/distance nem average_speed")
        void paceNuloSemDadosSuficientes() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00",
                    null, null, null, null,
                    null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getPaceMedia()).isNull();
        }

        @Test
        @DisplayName("moving_time ausente mapeia para Duration.ZERO (coluna duracao_min é NOT NULL) — nunca null")
        void movingTimeAusenteViraDurationZero() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Treadmill", "Esteira", "2026-07-16T06:00:00",
                    null, null, 5000.0, null,
                    null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getDuracaoMin()).isEqualTo(Duration.ZERO).isNotNull();
        }

        @Test
        @DisplayName("distance ausente mapeia para null literal (coluna distancia_km é nullable)")
        void distanceAusenteViraNullLiteral() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00",
                    1800, 1850, null, null,
                    null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getDistanciaKm()).isNull();
        }

        @Test
        @DisplayName("campos opcionais ausentes viram null, sem lançar exceção")
        void camposOpcionaisAusentesViramNull() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00",
                    1800, null, 5000.0, null,
                    null, null, null, null, null, null, null, null);

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
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00",
                    1800, 1850, 5000.0, null,
                    null, null, null, null, 7.6, null, null, null);

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
                    "i166338796", "i641775", "Run", "Taboão da Serra - 8.00Km CONTINUO", "2026-07-16T08:12:19",
                    3108, 3110, 8009.18, 2.576, 152.0, 164.0, 69.87482, 80.822655, 2.0, 58,
                    "Garmin Forerunner 970", 666);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getCadenciaMedia()).isEqualTo(162);
        }

        @Test
        @DisplayName("cadência fora da faixa 60-200 após dobrar vira null (dado degenerado)")
        void cadenciaForaDaFaixaViraNull() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00",
                    1800, 1850, 5000.0, null, null, null, null, 250.0, null, null, null, null);

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
                    "i1", "i641775", type, "Atividade", "2026-07-16T06:00:00",
                    1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino).isNotNull();
            assertThat(treino.getExternalId()).isEqualTo("i1");
        }

        @Test
        @DisplayName("modalidade não suportada (ex.: Ride) lança DomainRuleViolationException")
        void modalidadeNaoSuportadaLancaExcecao() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Ride", "Pedal", "2026-07-16T06:00:00",
                    1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null);

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
                    "i1", "i641775", "Run", "Corrida noturna", "2026-07-16T23:45:00",
                    1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null);

            TreinoRealizado treino = mapper.map(dto, atleta());

            assertThat(treino.getDataTreino()).isEqualTo(LocalDate.of(2026, 7, 16));
        }

        @Test
        @DisplayName("virada de dia: activity às 00:15 local não vaza para o dia anterior por fuso do servidor")
        void viradaDeDiaLogoApósAMeiaNoite() {
            IcuActivityDto dto = new IcuActivityDto(
                    "i1", "i641775", "Run", "Corrida de madrugada", "2026-07-16T00:15:00",
                    1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null);

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
                "i86400275", "i641775", "Run", "Corrida matinal", "2026-07-16T06:30:00",
                1800, 1850, 5000.0, 2.78, 145.0, 168.0, 42.0, 80.5, 6.0, 55,
                "Garmin Forerunner 965", 420);
    }

    private IcuActivityDto activityMinima() {
        return new IcuActivityDto(
                "i1", "i641775", "Run", "Corrida", "2026-07-16T06:00:00",
                1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null);
    }

    private Atleta atleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());

        Atleta atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);
        return atleta;
    }
}
