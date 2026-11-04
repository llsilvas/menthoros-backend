package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.AlvoPrimario;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.services.helper.IntervalsIcuFcAlvoResolver;
import br.com.menthoros.backend.services.helper.IntervalsIcuWorkoutConverter;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Simulação ponta a ponta do que chega ao relógio Garmin: treino planejado (como o plano o grava)
 * → {@link IntervalsIcuWorkoutConverter} → {@link GarminWorkoutPayloadMapper} → JSON da Training
 * API V2. Cobre as sessões típicas de corrida; os unitários de cada peça cobrem as bordas.
 *
 * <p>Pace no Garmin é velocidade em m/s com 3 casas: Low = ritmo mais lento, High = mais rápido.</p>
 */
class GarminTreinoSimulacaoTest {

    /** LTHR 170 — Z2 e 85-89% => 145-151 bpm (mesma base do IntervalsIcuWorkoutConverterTest). */
    private static final int FC_LIMIAR = 170;
    private static final LocalDate DATA = LocalDate.of(2026, 11, 10);

    private final IntervalsIcuWorkoutConverter converter = new IntervalsIcuWorkoutConverter(
            new IntervalsIcuFcAlvoResolver(new ZonaTreinoService()));
    private final GarminWorkoutPayloadMapper mapper = new GarminWorkoutPayloadMapper(new ObjectMapper());

    @Nested
    @DisplayName("treinos contínuos")
    class Continuos {

        @Test
        @DisplayName("rodagem 45 min em 145-151 bpm (formato atual do plano): um step por tempo com a faixa intacta")
        void rodagemZ2() {
            JsonNode steps = garmin(treino(TipoTreino.CONTINUO, List.of(
                    etapa(1, "PRINCIPAL", "Rodagem", 45, null, null, "145-151 bpm"))));

            assertThat(steps).hasSize(1);
            assertStep(steps.get(0), "TIME", 2700);
            assertFc(steps.get(0), 145, 151);
            assertThat(steps.get(0).get("intensity").asText()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("tempo run 20 min a 4:30/km entre aquecimento e desaquecimento livres")
        void tempoRunPaceUnico() {
            JsonNode steps = garmin(treino(TipoTreino.TEMPO_RUN, List.of(
                    etapa(1, "AQUECIMENTO", "Aquecimento", 15, null, null, null),
                    etapa(2, "PRINCIPAL", "Tempo", 20, null, "4:30", null),
                    etapa(3, "DESAQUECIMENTO", "Desaquecimento", 10, null, null, null))));

            assertThat(steps).hasSize(3);
            assertStep(steps.get(0), "TIME", 900);
            assertThat(steps.get(0).get("targetType").asText()).isEqualTo("OPEN");
            assertStep(steps.get(1), "TIME", 1200);
            assertPace(steps.get(1), 3.636, 3.774); // 4:25-4:35/km
            assertStep(steps.get(2), "TIME", 600);
        }

        @Test
        @DisplayName("longão progressivo 3x6 km (5:30, 5:10, 4:50): três steps por distância, sem bloco inventado")
        void longaoProgressivo() {
            JsonNode steps = garmin(treino(TipoTreino.LONGO, List.of(
                    etapa(1, "PRINCIPAL", "Bloco 1", null, km("6"), "5:30", null),
                    etapa(2, "PRINCIPAL", "Bloco 2", null, km("6"), "5:10", null),
                    etapa(3, "PRINCIPAL", "Bloco 3", null, km("6"), "4:50", null))));

            assertThat(steps).hasSize(3);
            assertThat(steps).allSatisfy(s -> assertThat(s.get("type").asText()).isEqualTo("WorkoutStep"));
            assertStep(steps.get(0), "DISTANCE", 6000);
            assertPace(steps.get(0), 2.985, 3.077);
            assertPace(steps.get(1), 3.175, 3.279);
            assertPace(steps.get(2), 3.390, 3.509);
        }

        @Test
        @DisplayName("simulado de 10 km sem etapas: step único por distância, sem meta")
        void simulado10kSemEtapas() {
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, List.of());
            treino.setDistanciaKm(km("10"));

            JsonNode steps = garmin(treino);

            assertThat(steps).hasSize(1);
            assertStep(steps.get(0), "DISTANCE", 10000);
            assertThat(steps.get(0).get("targetType").asText()).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("treino sem etapas com zona Z2 e duração: step único por tempo com a faixa da zona")
        void semEtapasComZona() {
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, List.of());
            treino.setDuracaoMin(Duration.ofMinutes(50));
            treino.setZonaAlvo("Z2");

            JsonNode steps = garmin(treino);

            assertThat(steps).hasSize(1);
            assertStep(steps.get(0), "TIME", 3000);
            assertFc(steps.get(0), 145, 151);
        }

        @Test
        @DisplayName("descanso não gera workout")
        void descansoNaoGera() {
            assertThat(converter.converter(treino(TipoTreino.DESCANSO, List.of()))).isEmpty();
        }
    }

    @Nested
    @DisplayName("treinos intervalados")
    class Intervalados {

        @Test
        @DisplayName("6x1 km a 4:00-4:10 com 2 min de trote: um repeat de 6 com tiro por distância e trote REST")
        void seisPorMil() {
            UUID bloco = UUID.randomUUID();
            List<EtapaTreino> etapas = new ArrayList<>();
            etapas.add(etapa(1, "AQUECIMENTO", "Aquecimento", 15, null, null, null));
            for (int i = 0; i < 6; i++) {
                etapas.add(noBloco(etapa(2 + 2 * i, "INTERVALADO", "Tiro", null, km("1"), "4:00-4:10", null), bloco, 6));
                etapas.add(noBloco(etapa(3 + 2 * i, "RECUPERACAO", "Trote", 2, null, null, null), bloco, 6));
            }
            etapas.add(etapa(20, "DESAQUECIMENTO", "Desaquecimento", 10, null, null, null));

            JsonNode steps = garmin(treino(TipoTreino.INTERVALADO, etapas));

            assertThat(steps).hasSize(3);
            JsonNode repeat = steps.get(1);
            assertThat(repeat.get("type").asText()).isEqualTo("WorkoutRepeatStep");
            assertThat(repeat.get("repeatType").asText()).isEqualTo("REPEAT_UNTIL_STEPS_CMPLT");
            assertThat(repeat.get("repeatValue").asInt()).isEqualTo(6);
            JsonNode tiro = repeat.get("steps").get(0);
            JsonNode trote = repeat.get("steps").get(1);
            assertStep(tiro, "DISTANCE", 1000);
            assertPace(tiro, 4.0, 4.167);
            assertThat(tiro.get("intensity").asText()).isEqualTo("ACTIVE");
            assertStep(trote, "TIME", 120);
            assertThat(trote.get("targetType").asText()).isEqualTo("OPEN");
            assertThat(trote.get("intensity").asText()).isEqualTo("REST");
            // stepOrder global e sequencial, contando o repeat e os filhos
            assertThat(List.of(steps.get(0), repeat, tiro, trote, steps.get(2)))
                    .extracting(s -> s.get("stepOrder").asInt())
                    .containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        @DisplayName("10x200 m a 3:30-3:40 com 200 m de trote: repeat de 10 por distância")
        void dezPorDuzentos() {
            UUID bloco = UUID.randomUUID();
            List<EtapaTreino> etapas = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                etapas.add(noBloco(etapa(1 + 2 * i, "INTERVALADO", "Tiro", null, km("0.2"), "3:30-3:40", null), bloco, 10));
                etapas.add(noBloco(etapa(2 + 2 * i, "RECUPERACAO", "Trote", null, km("0.2"), null, null), bloco, 10));
            }

            JsonNode steps = garmin(treino(TipoTreino.INTERVALADO, etapas));

            assertThat(steps).hasSize(1);
            assertThat(steps.get(0).get("repeatValue").asInt()).isEqualTo(10);
            JsonNode tiro = steps.get(0).get("steps").get(0);
            assertStep(tiro, "DISTANCE", 200);
            assertPace(tiro, 4.545, 4.762);
            assertStep(steps.get(0).get("steps").get(1), "DISTANCE", 200);
        }

        @Test
        @DisplayName("fartlek da IA sem blocoId (4x 1 min forte + 2 min leve por FC): bloco inferido, os dois com meta")
        void fartlekInferido() {
            List<EtapaTreino> etapas = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                etapas.add(etapa(1 + 2 * i, "INTERVALADO", "Forte", 1, null, null, "150-160 bpm"));
                etapas.add(etapa(2 + 2 * i, "RECUPERACAO", "Leve", 2, null, null, "120-136 bpm"));
            }

            JsonNode steps = garmin(treino(TipoTreino.FARTLEK, etapas));

            assertThat(steps).hasSize(1);
            assertThat(steps.get(0).get("repeatValue").asInt()).isEqualTo(4);
            JsonNode forte = steps.get(0).get("steps").get(0);
            JsonNode leve = steps.get(0).get("steps").get(1);
            assertStep(forte, "TIME", 60);
            assertFc(forte, 150, 160);
            assertFc(leve, 120, 136);
            // recuperação com meta não é REST: o relógio controla a FC dela também
            assertThat(leve.get("intensity").asText()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("ondulado 10 min a 85-89% / 5 min a 4:50 sem etapa INTERVALADO: steps soltos, sem bloco inventado")
        void onduladoSemBloco() {
            JsonNode steps = garmin(treino(TipoTreino.CONTINUO, List.of(
                    etapa(1, "PRINCIPAL", "Leve", 10, null, null, "85-89%"),
                    etapa(2, "PRINCIPAL", "Moderado", 5, null, "4:50", null),
                    etapa(3, "PRINCIPAL", "Leve", 10, null, null, "85-89%"),
                    etapa(4, "PRINCIPAL", "Moderado", 5, null, "4:50", null))));

            assertThat(steps).hasSize(4);
            assertThat(steps).noneSatisfy(s -> assertThat(s.get("type").asText()).isEqualTo("WorkoutRepeatStep"));
        }
    }

    @Nested
    @DisplayName("escolha do alvo")
    class Alvo {

        @Test
        @DisplayName("etapa com FC e ritmo: a FC é a meta no relógio")
        void fcVenceRitmo() {
            JsonNode steps = garmin(treino(TipoTreino.CONTINUO, List.of(
                    etapa(1, "PRINCIPAL", "Rodagem", 40, null, "5:30-5:45", "85-89%"))));

            assertFc(steps.get(0), 145, 151);
        }

        @Test
        @DisplayName("treinador escolheu PACE como eixo: o ritmo é a meta mesmo com FC prescrita")
        void eixoPaceEscolhido() {
            EtapaTreino etapa = etapa(1, "PRINCIPAL", "Rodagem", 40, null, "5:30-5:45", "85-89%");
            etapa.setAlvoPrimario(AlvoPrimario.PACE);

            JsonNode steps = garmin(treino(TipoTreino.CONTINUO, List.of(etapa)));

            assertPace(steps.get(0), 2.899, 3.030);
        }

        @Test
        @DisplayName("plano legado em % e atleta sem limiar de FC: o percentual não resolve e o ritmo assume a meta")
        void semLimiarRitmoAssume() {
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, List.of(
                    etapa(1, "PRINCIPAL", "Moderado", 30, null, "4:50-5:00", "90-94%")));
            treino.setAtleta(Atleta.builder().id(UUID.randomUUID()).dataNascimento(LocalDate.of(1990, 1, 1)).build());

            IntervalsIcuWorkoutConverter.ResultadoConversao r = converter.converter(treino).orElseThrow();
            JsonNode step = mapper.montar(r.workout()).get("segments").get(0).get("steps").get(0);

            assertPace(step, 3.333, 3.448);
            assertThat(r.metaFcDescartada()).isTrue();
        }

        @Test
        @DisplayName("plano legado em %, atleta sem limiar e etapa só com FC: vai sem meta (OPEN), nunca com FC estimada por idade")
        void semLimiarSemRitmoViraOpen() {
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, List.of(
                    etapa(1, "PRINCIPAL", "Rodagem", 40, null, null, "85-89%")));
            treino.setAtleta(Atleta.builder().id(UUID.randomUUID()).dataNascimento(LocalDate.of(1990, 1, 1)).build());

            JsonNode steps = garmin(treino);

            assertThat(steps.get(0).get("targetType").asText()).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("FC literal escrita ao contrário (160-150 bpm) chega ordenada")
        void fcInvertidaOrdenada() {
            JsonNode steps = garmin(treino(TipoTreino.CONTINUO, List.of(
                    etapa(1, "PRINCIPAL", "Rodagem", 30, null, null, "160-150 bpm"))));

            assertFc(steps.get(0), 150, 160);
        }
    }

    @Nested
    @DisplayName("limites do Garmin")
    class Limites {

        @Test
        @DisplayName("treino com 101 etapas distintas passa de 100 steps e é recusado antes do envio")
        void maisDeCemSteps() {
            List<EtapaTreino> etapas = new ArrayList<>();
            for (int i = 1; i <= 101; i++) {
                etapas.add(etapa(i, "PRINCIPAL", "Etapa " + i, i, null, null, null));
            }
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, etapas);

            assertThatThrownBy(() -> garmin(treino)).isInstanceOf(DomainRuleViolationException.class);
        }

        @Test
        @DisplayName("repeat de 30x conta só os filhos uma vez: 3 steps no total, aceito")
        void repeatGrandeNaoMultiplicaSteps() {
            UUID bloco = UUID.randomUUID();
            List<EtapaTreino> etapas = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                etapas.add(noBloco(etapa(1 + 2 * i, "INTERVALADO", "Tiro", 1, null, "3:50-4:00", null), bloco, 30));
                etapas.add(noBloco(etapa(2 + 2 * i, "RECUPERACAO", "Trote", 1, null, null, null), bloco, 30));
            }

            JsonNode steps = garmin(treino(TipoTreino.INTERVALADO, etapas));

            assertThat(steps).hasSize(1);
            assertThat(steps.get(0).get("repeatValue").asInt()).isEqualTo(30);
            assertThat(steps.get(0).get("steps")).hasSize(2);
        }
    }

    // ===== Helpers =====

    private JsonNode garmin(TreinoPlanejado treino) {
        JsonNode payload = mapper.montar(converter.converter(treino).orElseThrow().workout());
        assertThat(payload.get("sport").asText()).isEqualTo("RUNNING");
        return payload.get("segments").get(0).get("steps");
    }

    private static void assertStep(JsonNode step, String durationType, int durationValue) {
        assertThat(step.get("type").asText()).isEqualTo("WorkoutStep");
        assertThat(step.get("durationType").asText()).isEqualTo(durationType);
        assertThat(step.get("durationValue").asInt()).isEqualTo(durationValue);
    }

    private static void assertPace(JsonNode step, double low, double high) {
        assertThat(step.get("targetType").asText()).isEqualTo("PACE");
        assertThat(step.get("targetValueLow").asDouble()).isEqualTo(low);
        assertThat(step.get("targetValueHigh").asDouble()).isEqualTo(high);
    }

    private static void assertFc(JsonNode step, int low, int high) {
        assertThat(step.get("targetType").asText()).isEqualTo("HEART_RATE");
        assertThat(step.get("targetValueLow").asInt()).isEqualTo(low);
        assertThat(step.get("targetValueHigh").asInt()).isEqualTo(high);
        assertThat(step.has("targetValueType")).isFalse(); // bpm absoluto, nunca PERCENT
    }

    private static BigDecimal km(String valor) {
        return new BigDecimal(valor);
    }

    private static TreinoPlanejado treino(TipoTreino tipo, List<EtapaTreino> etapas) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setId(UUID.randomUUID());
        treino.setTipoTreino(tipo);
        treino.setDataTreino(DATA);
        treino.setDuracaoMin(Duration.ZERO);
        treino.setEtapas(etapas);
        treino.setAtleta(Atleta.builder().id(UUID.randomUUID()).fcLimiar(FC_LIMIAR).build());
        return treino;
    }

    private static EtapaTreino etapa(int ordem, String tipoEtapa, String descricao, Integer duracaoMin,
                                     BigDecimal distanciaKm, String ritmoAlvo, String fcAlvo) {
        return EtapaTreino.builder()
                .id(UUID.randomUUID())
                .ordem(ordem)
                .tipoEtapa(tipoEtapa)
                .descricaoEtapa(descricao)
                .duracaoMin(duracaoMin)
                .distanciaKm(distanciaKm)
                .ritmoAlvo(ritmoAlvo)
                .fcAlvoEtapa(fcAlvo)
                .build();
    }

    private static EtapaTreino noBloco(EtapaTreino etapa, UUID blocoId, int repeticoes) {
        etapa.setBlocoId(blocoId);
        etapa.setBlocoRepeticoes(repeticoes);
        return etapa;
    }
}
