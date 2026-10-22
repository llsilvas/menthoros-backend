package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.domain.planner.SessionCompositionResolver.CompositionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionCompositionResolver")
class SessionCompositionResolverTest {

    private final SessionCompositionResolver resolver = new SessionCompositionResolver();

    private static CompositionRequest req(TrainingPhase phase, double targetTss, int dias) {
        return new CompositionRequest(phase, targetTss, dias, null, null, 3, null);
    }

    private static List<String> tipos(List<SessionSlot> slots) {
        return slots.stream().map(SessionSlot::sessionType).toList();
    }

    @Nested
    @DisplayName("composicao por fase (golden)")
    class Composicao {

        @Test
        @DisplayName("BASE com 5 dias: LONGO chave + aerobicos, zero duras (polarizacao 0-12% escolhe 0)")
        void baseCincoDias() {
            List<SessionSlot> s = resolver.compose(req(TrainingPhase.BASE, 350, 5));
            assertThat(tipos(s)).containsExactly("LONGO", "FACIL", "CONTINUO", "FACIL", "REGENERATIVO");
            assertThat(s.get(0).chave()).isTrue();
            assertThat(s.stream().anyMatch(x -> x.sessionType().equals("TEMPO_RUN") || x.sessionType().equals("INTERVALADO"))).isFalse();
        }

        @Test
        @DisplayName("BUILD com 6 dias: LONGO chave + 1 dura (INTERVALADO) dentro da faixa 15-22%")
        void buildSeisDias() {
            List<SessionSlot> s = resolver.compose(req(TrainingPhase.BUILD, 450, 6));
            assertThat(tipos(s)).containsExactly("LONGO", "INTERVALADO", "FACIL", "CONTINUO", "REGENERATIVO", "FACIL");
            assertThat(s.get(0).chave()).isTrue(); // LONGO
        }

        @Test
        @DisplayName("gate de carga (§calibração): BUILD com targetTss < 220 não recebe sessão dura")
        void buildSemanaLeveSemDura() {
            List<SessionSlot> s = resolver.compose(req(TrainingPhase.BUILD, 156, 4));
            assertThat(s.stream().anyMatch(x -> x.sessionType().equals("INTERVALADO")
                    || x.sessionType().equals("TIRO") || x.sessionType().equals("TEMPO_RUN"))).isFalse();
        }

        @Test
        @DisplayName("gate de carga: BUILD com targetTss >= 220 mantém a sessão dura")
        void buildSemanaCheiaComDura() {
            List<SessionSlot> s = resolver.compose(req(TrainingPhase.BUILD, 300, 4));
            assertThat(s.stream().anyMatch(x -> x.sessionType().equals("INTERVALADO"))).isTrue();
        }

        @Test
        @DisplayName("RECOVERY: só aeróbico leve, sem chave e sem duras")
        void recovery() {
            List<SessionSlot> s = resolver.compose(req(TrainingPhase.RECOVERY, 120, 3));
            assertThat(tipos(s)).containsExactly("REGENERATIVO", "FACIL", "FACIL");
            assertThat(s.stream().noneMatch(SessionSlot::chave)).isTrue();
        }

        @Test
        @DisplayName("CALIBRATION não é emitida pelo planner (lista vazia)")
        void calibration() {
            assertThat(resolver.compose(req(TrainingPhase.CALIBRATION, 300, 5))).isEmpty();
        }

        @Test
        @DisplayName("zero dias disponíveis: lista vazia")
        void zeroDias() {
            assertThat(resolver.compose(req(TrainingPhase.BASE, 300, 0))).isEmpty();
        }
    }

    @Nested
    @DisplayName("RETURN_TO_TRAINING — LONGO condicionado a capacidade recente")
    class Retorno {

        @Test
        @DisplayName("sem longões recentes: LONGO não entra nem como chave")
        void semLongoes() {
            var r = new CompositionRequest(TrainingPhase.RETURN_TO_TRAINING, 200, 3, null, null, 0, null);
            List<SessionSlot> s = resolver.compose(r);
            assertThat(tipos(s)).doesNotContain("LONGO");
            assertThat(s.stream().noneMatch(SessionSlot::chave)).isTrue();
        }

        @Test
        @DisplayName("com longões recentes: LONGO entra como chave (slot #1)")
        void comLongoes() {
            var r = new CompositionRequest(TrainingPhase.RETURN_TO_TRAINING, 250, 3, null, null, 2, null);
            List<SessionSlot> s = resolver.compose(r);
            assertThat(s.get(0).sessionType()).isEqualTo("LONGO");
            assertThat(s.get(0).chave()).isTrue();
        }
    }

    @Nested
    @DisplayName("PROVA (RACE_WEEK)")
    class Prova {

        @Test
        @DisplayName("PROVA é a chave, ocupa slot e tem TSS reservado do alvo")
        void provaReserva() {
            var r = new CompositionRequest(TrainingPhase.RACE_WEEK, 300, 3, null, null, 3, 1.5); // 1.5h de prova
            List<SessionSlot> s = resolver.compose(r);
            SessionSlot prova = s.stream().filter(x -> x.sessionType().equals("PROVA")).findFirst().orElseThrow();
            assertThat(prova.chave()).isTrue();
            // TSS da prova = fatorImpacto(1.3) * TAXA_BASE(50) * 1.5h = 97.5
            assertThat(prova.targetTss()).isEqualTo(97.5);
            assertThat(s.stream().filter(x -> !x.sessionType().equals("PROVA")).allMatch(x -> !x.chave())).isTrue();
        }
    }

    /**
     * fix-planner-ignora-prova-na-semana (task 5.1, CA8/CA9): slot PROVA por prova preparatória,
     * independente da fase — hoje só RACE_WEEK compõe prova. TSS pela distância (fórmula do
     * determinante), sem entrar na repartição do loadTarget da semana.
     */
    @Nested
    @DisplayName("provas preparatórias (independente da fase)")
    class ProvasPreparatorias {

        @Test
        @DisplayName("fase PEAK + 1 preparatória: slot PROVA adicional, loadTarget inalterado")
        void peakComPreparatoria() {
            var semPrep = resolver.compose(req(TrainingPhase.PEAK, 400, 5));
            var comPrep = resolver.compose(new CompositionRequest(TrainingPhase.PEAK, 400, 5, null, null, 3, null,
                    List.of(new CompositionRequest.ProvaPreparatoriaSlot(1.5))));

            assertThat(tipos(comPrep)).containsAll(tipos(semPrep));
            assertThat(comPrep).hasSize(semPrep.size() + 1);
            SessionSlot prova = comPrep.stream().filter(s -> s.sessionType().equals("PROVA")).findFirst().orElseThrow();
            // TSS = fatorImpacto(1.3) * TAXA_BASE(50) * 1.5h = 97.5 — igual à fórmula do determinante (RACE_WEEK)
            assertThat(prova.targetTss()).isEqualTo(97.5);
            // loadTarget da semana (targetTss do request) não mudou: a soma dos slots "normais" é a mesma
            double tssNormal = comPrep.stream().filter(s -> !s.sessionType().equals("PROVA")).mapToDouble(SessionSlot::targetTss).sum();
            double tssNormalSemPrep = semPrep.stream().mapToDouble(SessionSlot::targetTss).sum();
            assertThat(tssNormal).isEqualTo(tssNormalSemPrep);
        }

        @Test
        @DisplayName("preparatória não é chave e não disputa a chave da fase (LONGO continua chave)")
        void preparatoriaNaoERoubaChave() {
            var s = resolver.compose(new CompositionRequest(TrainingPhase.BUILD, 450, 6, null, null, 3, null,
                    List.of(new CompositionRequest.ProvaPreparatoriaSlot(1.0))));

            SessionSlot prova = s.stream().filter(x -> x.sessionType().equals("PROVA")).findFirst().orElseThrow();
            assertThat(prova.chave()).isFalse();
            assertThat(s.stream().filter(x -> x.sessionType().equals("LONGO")).findFirst().orElseThrow().chave()).isTrue();
        }

        @Test
        @DisplayName("duas preparatórias na semana: dois slots PROVA")
        void duasPreparatorias() {
            var s = resolver.compose(new CompositionRequest(TrainingPhase.BASE, 300, 5, null, null, 3, null,
                    List.of(new CompositionRequest.ProvaPreparatoriaSlot(1.0),
                            new CompositionRequest.ProvaPreparatoriaSlot(0.5))));

            assertThat(s.stream().filter(x -> x.sessionType().equals("PROVA")).count()).isEqualTo(2);
        }

        @Test
        @DisplayName("preparatória com horas nulo: TSS zero, não quebra")
        void semHoras() {
            var s = resolver.compose(new CompositionRequest(TrainingPhase.BASE, 300, 5, null, null, 3, null,
                    List.of(new CompositionRequest.ProvaPreparatoriaSlot(null))));

            SessionSlot prova = s.stream().filter(x -> x.sessionType().equals("PROVA")).findFirst().orElseThrow();
            assertThat(prova.targetTss()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("sem preparatórias (lista vazia), comportamento idêntico ao de antes")
        void semPreparatorias() {
            var comListaVazia = resolver.compose(new CompositionRequest(TrainingPhase.PEAK, 400, 5, null, null, 3, null, List.of()));
            var comConstrutorAntigo = resolver.compose(req(TrainingPhase.PEAK, 400, 5));

            assertThat(tipos(comListaVazia)).isEqualTo(tipos(comConstrutorAntigo));
        }
    }

    @Nested
    @DisplayName("carga e duração")
    class CargaDuracao {

        @Test
        @DisplayName("mínimos vencem: alvo minúsculo reduz sessionCount")
        void minimosVencem() {
            // alvo 20 TSS, 3 dias RECOVERY: 3x REGEN no mínimo (~14 TSS cada) somariam ~42 > 20 -> reduz.
            List<SessionSlot> s = resolver.compose(req(TrainingPhase.RECOVERY, 20, 3));
            assertThat(s.size()).isLessThan(3);
        }

        @Test
        @DisplayName("durações ficam dentro dos clamps por tipo")
        void duracoesClampadas() {
            List<SessionSlot> s = resolver.compose(req(TrainingPhase.BUILD, 500, 5));
            assertThat(s).allSatisfy(slot -> {
                assertThat(slot.durationMinutes()).isNotNull();
                assertThat(slot.durationMinutes()).isBetween(20, 150);
            });
        }

        @Test
        @DisplayName("determinístico: mesma entrada, mesma saída")
        void deterministico() {
            var r = req(TrainingPhase.PEAK, 480, 6);
            assertThat(tipos(resolver.compose(r))).isEqualTo(tipos(resolver.compose(r)));
        }
    }
}
