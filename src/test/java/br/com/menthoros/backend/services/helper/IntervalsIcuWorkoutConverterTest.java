package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.workout.HrTarget;
import br.com.menthoros.backend.domain.workout.PaceTarget;
import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.domain.workout.WorkoutStep;
import br.com.menthoros.backend.entity.EtapaTreino;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntervalsIcuWorkoutConverterTest {

    private final IntervalsIcuWorkoutConverter converter = new IntervalsIcuWorkoutConverter();

    @Nested
    @DisplayName("converter")
    class Converter {

        @Test
        @DisplayName("des-expande bloco reps=4 em UM WorkoutStep.bloco com 2 sub-steps (nunca N²)")
        void desExpandeBlocoConsistente() {
            UUID blocoId = UUID.randomUUID();
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "INTERVALADO", "Tiro", 3, null, "4:30-4:45", null, blocoId, 4),
                    etapa(2, "RECUPERACAO", "Trote", 2, null, null, null, blocoId, 4),
                    etapa(3, "INTERVALADO", "Tiro", 3, null, "4:30-4:45", null, blocoId, 4),
                    etapa(4, "RECUPERACAO", "Trote", 2, null, null, null, blocoId, 4),
                    etapa(5, "INTERVALADO", "Tiro", 3, null, "4:30-4:45", null, blocoId, 4),
                    etapa(6, "RECUPERACAO", "Trote", 2, null, null, null, blocoId, 4),
                    etapa(7, "INTERVALADO", "Tiro", 3, null, "4:30-4:45", null, blocoId, 4),
                    etapa(8, "RECUPERACAO", "Trote", 2, null, null, null, blocoId, 4)
            );
            TreinoPlanejado treino = treino(TipoTreino.INTERVALADO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps()).hasSize(1);
            WorkoutStep bloco = resultado.steps().get(0);
            assertThat(bloco.reps()).isEqualTo(4);
            assertThat(bloco.steps()).hasSize(2);
        }

        @Test
        @DisplayName("bloco com janelas inconsistentes cai no fallback: steps individuais sem reps")
        void fallbackParaBlocoInconsistente() {
            UUID blocoId = UUID.randomUUID();
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "INTERVALADO", "Tiro", 3, null, "4:30-4:45", null, blocoId, 4),
                    etapa(2, "RECUPERACAO", "Trote", 2, null, null, null, blocoId, 4),
                    etapa(3, "INTERVALADO", "Tiro", 3, null, "4:30-4:45", null, blocoId, 4),
                    etapa(4, "RECUPERACAO", "Trote", 2, null, null, null, blocoId, 4),
                    etapa(5, "INTERVALADO", "Tiro", 3, null, "4:30-4:45", null, blocoId, 4),
                    // janela 3 tem duração diferente da referência -> inconsistente
                    etapa(6, "RECUPERACAO", "Trote", 5, null, null, null, blocoId, 4),
                    etapa(7, "INTERVALADO", "Tiro", 3, null, "4:30-4:45", null, blocoId, 4),
                    etapa(8, "RECUPERACAO", "Trote", 2, null, null, null, blocoId, 4)
            );
            TreinoPlanejado treino = treino(TipoTreino.INTERVALADO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps()).hasSize(8);
            assertThat(resultado.steps()).allSatisfy(step -> assertThat(step.reps()).isNull());
        }

        @Test
        @DisplayName("infere bloco reps=4 em série repetida sem blocoId (fartlek expandido pela IA)")
        void inferBlocoSemBlocoId() {
            // Etapas geradas pela expansão do LLM: nascem sem blocoId (o campo só é setado no
            // caminho do treinador, via tipoEtapa=BLOCO). Sem inferência, o Garmin recebe 8 steps
            // soltos em vez de "4x [1min forte, 2min leve]".
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "INTERVALADO", "Aceleração 1/4 - 1min", 1, null, null, "150-160 bpm", null, null),
                    etapa(2, "RECUPERACAO", "Recuperação 1 - 2min trote", 2, null, null, "120-136 bpm", null, null),
                    etapa(3, "INTERVALADO", "Aceleração 2/4 - 1min", 1, null, null, "150-160 bpm", null, null),
                    etapa(4, "RECUPERACAO", "Recuperação 2 - 2min trote", 2, null, null, "120-136 bpm", null, null),
                    etapa(5, "INTERVALADO", "Aceleração 3/4 - 1min", 1, null, null, "150-160 bpm", null, null),
                    etapa(6, "RECUPERACAO", "Recuperação 3 - 2min trote", 2, null, null, "120-136 bpm", null, null),
                    etapa(7, "INTERVALADO", "Aceleração 4/4 - 1min", 1, null, null, "150-160 bpm", null, null),
                    etapa(8, "RECUPERACAO", "Recuperação 4 - 2min trote", 2, null, null, "120-136 bpm", null, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.FARTLEK, LocalDate.of(2026, 8, 20),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps()).hasSize(1);
            WorkoutStep bloco = resultado.steps().getFirst();
            assertThat(bloco.reps()).isEqualTo(4);
            assertThat(bloco.steps()).hasSize(2);
            assertThat(bloco.steps().get(0).durationSeconds()).isEqualTo(60);
            assertThat(bloco.steps().get(1).durationSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("não inventa bloco em etapas heterogêneas sem blocoId")
        void naoInfereBlocoEmSerieHeterogenea() {
            // Progressivo: durações crescentes, nenhuma janela se repete.
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "PRINCIPAL", "Bloco 1", 5, null, null, "130-140 bpm", null, null),
                    etapa(2, "PRINCIPAL", "Bloco 2", 8, null, null, "140-150 bpm", null, null),
                    etapa(3, "PRINCIPAL", "Bloco 3", 12, null, null, "150-160 bpm", null, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, LocalDate.of(2026, 8, 20),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps()).hasSize(3);
            assertThat(resultado.steps()).allSatisfy(step -> assertThat(step.reps()).isNull());
        }

        @Test
        @DisplayName("série repetida sem blocoId convive com aquecimento e desaquecimento avulsos")
        void inferBlocoPreservandoEtapasAvulsas() {
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "AQUECIMENTO", "Aquecimento leve", 8, null, null, "107-121 bpm", null, null),
                    etapa(2, "INTERVALADO", "Aceleração 1/2", 1, null, null, "150-160 bpm", null, null),
                    etapa(3, "RECUPERACAO", "Recuperação 1", 2, null, null, "120-136 bpm", null, null),
                    etapa(4, "INTERVALADO", "Aceleração 2/2", 1, null, null, "150-160 bpm", null, null),
                    etapa(5, "RECUPERACAO", "Recuperação 2", 2, null, null, "120-136 bpm", null, null),
                    etapa(6, "DESAQUECIMENTO", "Desaquecimento", 5, null, null, "107-121 bpm", null, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.FARTLEK, LocalDate.of(2026, 8, 20),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps()).hasSize(3);
            assertThat(resultado.steps().get(0).reps()).isNull();
            assertThat(resultado.steps().get(1).reps()).isEqualTo(2);
            assertThat(resultado.steps().get(1).steps()).hasSize(2);
            assertThat(resultado.steps().get(2).reps()).isNull();
        }

        @Test
        @DisplayName("distância vence duração quando ambas presentes; só duração usa duração; nenhuma gera step aberto")
        void precedenciaDuracaoDistancia() {
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "PRINCIPAL", "Tiro longo", 30, new BigDecimal("5.0"), null, null, null, null),
                    etapa(2, "PRINCIPAL", "Rodagem", 30, null, null, null, null, null),
                    etapa(3, "AQUECIMENTO", "Corra até aquecer", null, null, null, null, null, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps()).hasSize(3);
            assertThat(resultado.steps().get(0).distanceMeters()).isEqualTo(5000);
            assertThat(resultado.steps().get(0).durationSeconds()).isNull();
            assertThat(resultado.steps().get(1).durationSeconds()).isEqualTo(1800);
            assertThat(resultado.steps().get(1).distanceMeters()).isNull();
            assertThat(resultado.steps().get(2).durationSeconds()).isNull();
            assertThat(resultado.steps().get(2).distanceMeters()).isNull();
        }

        @Test
        @DisplayName("pace vence FC: pace preenchido, hr nulo, FC original anexada ao text")
        void precedenciaPaceVenceFc() {
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "PRINCIPAL", "Tiro", 5, null, "5:00-5:15", "140-150 bpm", null, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.INTERVALADO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            WorkoutStep step = resultado.steps().get(0);
            assertThat(step.pace()).isEqualTo(new PaceTarget(300, 315));
            assertThat(step.hr()).isNull();
            assertThat(step.text()).isEqualTo("Tiro (140-150 bpm)");
        }

        @Test
        @DisplayName("treino sem etapas gera step único com duração do treino e zona alvo")
        void treinoSemEtapas() {
            TreinoPlanejado treino = treino(TipoTreino.FACIL, LocalDate.of(2026, 7, 15),
                    Duration.ofMinutes(45), null, "z2", "Rodagem leve", List.of());

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps()).hasSize(1);
            WorkoutStep step = resultado.steps().get(0);
            assertThat(step.durationSeconds()).isEqualTo(2700);
            assertThat(step.distanceMeters()).isNull();
            assertThat(step.hr()).isEqualTo(new HrTarget(HrTarget.Unidade.ZONE, 2, 2));
            assertThat(resultado.description()).isEqualTo("Rodagem leve");
        }

        @Test
        @DisplayName("treino DESCANSO nunca é exportável")
        void naoExportavelDescanso() {
            TreinoPlanejado treino = treino(TipoTreino.DESCANSO, LocalDate.of(2026, 7, 15),
                    Duration.ofMinutes(30), null, null, null, List.of());

            assertThat(converter.converter(treino)).isEmpty();
        }

        @Test
        @DisplayName("etapas todas degeneradas com duração própria do treino exportam como step único")
        void etapasDegeneradasComDuracaoDoTreino() {
            // etapa sem duração/distância (só ritmoAlvo) não é prescritiva:
            // deve cair no mesmo caminho do treino sem etapas
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "PRINCIPAL", "Rodagem em Z2", null, null, "5:30-5:45", null, null, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, LocalDate.of(2026, 7, 15),
                    Duration.ofMinutes(40), null, "z2", null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps()).hasSize(1);
            WorkoutStep step = resultado.steps().get(0);
            assertThat(step.durationSeconds()).isEqualTo(2400);
            assertThat(step.hr()).isEqualTo(new HrTarget(HrTarget.Unidade.ZONE, 2, 2));
        }

        @Test
        @DisplayName("blocos A e B adjacentes viram dois blocos reps independentes")
        void blocosAdjacentesIndependentes() {
            UUID blocoA = UUID.randomUUID();
            UUID blocoB = UUID.randomUUID();
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "INTERVALADO", "Tiro A", 3, null, null, null, blocoA, 2),
                    etapa(2, "INTERVALADO", "Tiro A", 3, null, null, null, blocoA, 2),
                    etapa(3, "INTERVALADO", "Tiro B", 1, null, null, null, blocoB, 3),
                    etapa(4, "INTERVALADO", "Tiro B", 1, null, null, null, blocoB, 3),
                    etapa(5, "INTERVALADO", "Tiro B", 1, null, null, null, blocoB, 3)
            );
            TreinoPlanejado treino = treino(TipoTreino.INTERVALADO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps()).hasSize(2);
            assertThat(resultado.steps().get(0).reps()).isEqualTo(2);
            assertThat(resultado.steps().get(0).steps()).hasSize(1);
            assertThat(resultado.steps().get(1).reps()).isEqualTo(3);
            assertThat(resultado.steps().get(1).steps()).hasSize(1);
        }

        @Test
        @DisplayName("etapa com blocoId mas blocoRepeticoes=1 ou nulo não vira bloco")
        void blocoRepeticoesUmOuNuloNaoViraBloco() {
            UUID blocoUm = UUID.randomUUID();
            UUID blocoNulo = UUID.randomUUID();
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "PRINCIPAL", "Reps 1", 10, null, null, null, blocoUm, 1),
                    etapa(2, "PRINCIPAL", "Reps nulo", 10, null, null, null, blocoNulo, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps()).hasSize(2);
            assertThat(resultado.steps()).allSatisfy(step -> {
                assertThat(step.reps()).isNull();
                assertThat(step.steps()).isNull();
            });
        }

        @Test
        @DisplayName("grupo não divisível por N cai no fallback: steps individuais sem reps")
        void grupoNaoDivisivelPorNCaiNoFallback() {
            UUID blocoId = UUID.randomUUID();
            // 5 etapas físicas com blocoRepeticoes=4: 5 % 4 != 0 -> impossível des-expandir
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "INTERVALADO", "Tiro", 3, null, null, null, blocoId, 4),
                    etapa(2, "INTERVALADO", "Tiro", 3, null, null, null, blocoId, 4),
                    etapa(3, "INTERVALADO", "Tiro", 3, null, null, null, blocoId, 4),
                    etapa(4, "INTERVALADO", "Tiro", 3, null, null, null, blocoId, 4),
                    etapa(5, "INTERVALADO", "Tiro", 3, null, null, null, blocoId, 4)
            );
            TreinoPlanejado treino = treino(TipoTreino.INTERVALADO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps()).hasSize(5);
            assertThat(resultado.steps()).allSatisfy(step -> assertThat(step.reps()).isNull());
        }

        @Test
        @DisplayName("treino sem etapas e sem duração/distância não é exportável")
        void naoExportavelSemConteudo() {
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, List.of());

            assertThat(converter.converter(treino)).isEmpty();
        }

        @ParameterizedTest(name = "duracaoMin={0} produz step aberto")
        @ValueSource(ints = {0, -5})
        @DisplayName("duracaoMin não positivo produz step aberto, nunca lança")
        void duracaoNaoPositivaGeraStepAberto(int duracaoInvalida) {
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "PRINCIPAL", "Válida", 20, null, null, null, null, null),
                    etapa(2, "PRINCIPAL", "Inválida", duracaoInvalida, null, null, null, null, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            WorkoutStep stepAberto = resultado.steps().get(1);
            assertThat(stepAberto.durationSeconds()).isNull();
            assertThat(stepAberto.distanceMeters()).isNull();
        }

        @Test
        @DisplayName("etapa com todos os campos nulos é ignorada")
        void etapaTotalmenteNulaEIgnorada() {
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "PRINCIPAL", "Válida", 20, null, null, null, null, null),
                    etapa(2, null, null, null, null, null, null, null, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps()).hasSize(1);
        }

        @Test
        @DisplayName("descricaoEtapa vazia produz text nulo")
        void descricaoVaziaProduzTextNulo() {
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "PRINCIPAL", "", 10, null, null, null, null, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.steps().get(0).text()).isNull();
        }

        @Test
        @DisplayName("distanciaKm=0 não gera distance, cai para duração")
        void distanciaZeroNaoGeraDistance() {
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "PRINCIPAL", "Rodagem", 15, BigDecimal.ZERO, null, null, null, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            WorkoutStep step = resultado.steps().get(0);
            assertThat(step.distanceMeters()).isNull();
            assertThat(step.durationSeconds()).isEqualTo(900);
        }

        @Test
        @DisplayName("nome segue '<tipoTreino> <dd/MM>', externalId 'menthoros-<uuid>' e namePrefix nulo")
        void nomeEExternalId() {
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "PRINCIPAL", "Rodagem", 30, null, null, null, null, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, null, null, null, etapas);
            UUID treinoId = treino.getId();

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.name()).isEqualTo("CONTINUO 15/07");
            assertThat(resultado.externalId()).isEqualTo("menthoros-" + treinoId);
            assertThat(resultado.namePrefix()).isNull();
        }

        @Test
        @DisplayName("com distanciaKm preenchida, nome vira '<distancia> Km - <tipoTreino>' "
                + "(decisão do founder: mais útil no relógio que a data)")
        void nomeComDistanciaSubstituiData() {
            List<EtapaTreino> etapas = List.of(
                    etapa(1, "PRINCIPAL", "Rodagem", 30, null, null, null, null, null)
            );
            TreinoPlanejado treino = treino(TipoTreino.CONTINUO, LocalDate.of(2026, 7, 15),
                    Duration.ZERO, new BigDecimal("12.00"), null, null, etapas);

            StructuredWorkout resultado = converter.converter(treino).orElseThrow();

            assertThat(resultado.name()).isEqualTo("12 Km - CONTINUO");
        }

        @Test
        @DisplayName("rejeita treino nulo")
        void rejeitaTreinoNulo() {
            assertThatThrownBy(() -> converter.converter(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ===== Helpers de construção =====

    private TreinoPlanejado treino(TipoTreino tipo, LocalDate dataTreino, Duration duracaoMin,
                                    BigDecimal distanciaKm, String zonaAlvo, String descricao,
                                    List<EtapaTreino> etapas) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setId(UUID.randomUUID());
        treino.setTipoTreino(tipo);
        treino.setDataTreino(dataTreino);
        treino.setDuracaoMin(duracaoMin);
        treino.setDistanciaKm(distanciaKm);
        treino.setZonaAlvo(zonaAlvo);
        treino.setDescricao(descricao);
        treino.setEtapas(etapas);
        return treino;
    }

    private EtapaTreino etapa(Integer ordem, String tipoEtapa, String descricaoEtapa, Integer duracaoMin,
                               BigDecimal distanciaKm, String ritmoAlvo, String fcAlvoEtapa,
                               UUID blocoId, Integer blocoRepeticoes) {
        return EtapaTreino.builder()
                .id(UUID.randomUUID())
                .ordem(ordem)
                .tipoEtapa(tipoEtapa)
                .descricaoEtapa(descricaoEtapa)
                .duracaoMin(duracaoMin)
                .distanciaKm(distanciaKm)
                .ritmoAlvo(ritmoAlvo)
                .fcAlvoEtapa(fcAlvoEtapa)
                .blocoId(blocoId)
                .blocoRepeticoes(blocoRepeticoes)
                .build();
    }
}
