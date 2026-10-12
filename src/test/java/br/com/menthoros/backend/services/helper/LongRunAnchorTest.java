package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.RestDayLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FatigueSignalType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LongRunAnchor} (add-descanso-explicito-por-fadiga, Decisão 6): com a cobertura validada a
 * redistribuição não roda mais — ela descartava treino por conflito de dias. A âncora do longo, que
 * era dela, vira uma troca de dias que nunca descarta.
 */
@DisplayName("LongRunAnchor — longo no dia preferido")
class LongRunAnchorTest {

    private static final List<DiaSemana> DIAS =
            List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO);

    private final LongRunAnchor anchor = new LongRunAnchor();

    @Nested
    @DisplayName("troca")
    class Troca {

        @Test
        @DisplayName("CA12c: LONGO fora do dia preferido troca de dia com o treino que está lá")
        void trocaComTreino() {
            var plano = plano(List.of(
                    treino("SEGUNDA", "LONGO"), treino("TERCA", "CONTINUO"),
                    treino("QUINTA", "FARTLEK"), treino("SABADO", "REGENERATIVO")), List.of());

            var resultado = anchor.ancorar(plano, ctx(DiaSemana.SABADO));

            assertThat(diaDoTipo(resultado, "LONGO")).isEqualTo("SABADO");
            assertThat(diaDoTipo(resultado, "REGENERATIVO")).isEqualTo("SEGUNDA");
        }

        @Test
        @DisplayName("os demais treinos e seus dias ficam como estavam")
        void naoMexeNoResto() {
            var plano = plano(List.of(
                    treino("SEGUNDA", "LONGO"), treino("TERCA", "CONTINUO"),
                    treino("QUINTA", "FARTLEK"), treino("SABADO", "REGENERATIVO")), List.of());

            var resultado = anchor.ancorar(plano, ctx(DiaSemana.SABADO));

            assertThat(diaDoTipo(resultado, "CONTINUO")).isEqualTo("TERCA");
            assertThat(diaDoTipo(resultado, "FARTLEK")).isEqualTo("QUINTA");
            assertThat(resultado.treinosPlanejados()).hasSize(4);
        }

        @Test
        @DisplayName("LONGO já no dia preferido: plano devolvido sem mudança")
        void jaNoDiaPreferido() {
            var plano = plano(List.of(treino("SABADO", "LONGO"), treino("TERCA", "CONTINUO")), List.of());

            assertThat(anchor.ancorar(plano, ctx(DiaSemana.SABADO))).isSameAs(plano);
        }
    }

    @Nested
    @DisplayName("quando não trocar")
    class NaoTroca {

        @Test
        @DisplayName("CA12c: dia preferido fora dos dias disponíveis → nada muda")
        void diaPreferidoIndisponivel() {
            var plano = plano(List.of(treino("SEGUNDA", "LONGO"), treino("TERCA", "CONTINUO")), List.of());

            assertThat(anchor.ancorar(plano, ctx(DiaSemana.DOMINGO))).isSameAs(plano);
        }

        @Test
        @DisplayName("CA12c: dia preferido ocupado por descanso → nada muda (o descanso está preso ao sinal do dia)")
        void diaPreferidoComDescanso() {
            var plano = plano(List.of(treino("SEGUNDA", "LONGO"), treino("TERCA", "CONTINUO"), treino("QUINTA", "FACIL")),
                    List.of(new RestDayLlmDto("SABADO", "check-in de hoje: DESCANSAR")));

            assertThat(anchor.ancorar(plano, ctx(DiaSemana.SABADO))).isSameAs(plano);
        }

        @Test
        @DisplayName("sem dia preferido configurado → nada muda")
        void semDiaPreferido() {
            var plano = plano(List.of(treino("SEGUNDA", "LONGO")), List.of());

            assertThat(anchor.ancorar(plano, ctx(null))).isSameAs(plano);
        }

        @Test
        @DisplayName("plano sem LONGO → nada muda")
        void semLongo() {
            var plano = plano(List.of(treino("SEGUNDA", "CONTINUO"), treino("TERCA", "FACIL")), List.of());

            assertThat(anchor.ancorar(plano, ctx(DiaSemana.SABADO))).isSameAs(plano);
        }

        @Test
        @DisplayName("dia preferido vazio (sem treino nem descanso) → nada muda; a cobertura acusa o buraco")
        void diaPreferidoVazio() {
            var plano = plano(List.of(treino("SEGUNDA", "LONGO"), treino("TERCA", "CONTINUO")), List.of());

            assertThat(anchor.ancorar(plano, ctx(DiaSemana.QUINTA))).isSameAs(plano);
        }

        @Test
        @DisplayName("dois LONGO no plano (ambíguo) → nada muda")
        void doisLongos() {
            var plano = plano(List.of(treino("SEGUNDA", "LONGO"), treino("TERCA", "LONGO"), treino("SABADO", "FACIL")), List.of());

            assertThat(anchor.ancorar(plano, ctx(DiaSemana.SABADO))).isSameAs(plano);
        }

        @Test
        @DisplayName("plano nulo ou sem treinos → devolve como veio")
        void planoDegenerado() {
            var semTreinos = plano(List.of(), List.of());

            assertThat(anchor.ancorar(semTreinos, ctx(DiaSemana.SABADO))).isSameAs(semTreinos);
            assertThat(anchor.ancorar(null, ctx(DiaSemana.SABADO))).isNull();
        }
    }

    /**
     * fix-planner-ignora-prova-na-semana (task 3.1): com a PROVA já na lista, ancorar o longo na
     * véspera, no dia ou no dia seguinte dela desfaz o reparo — o LLM tira o longo de lá e a âncora o
     * devolve, ou ela troca o longo com a própria PROVA. Na geração real do Leandro o preferido era o
     * sábado e a prova, o domingo.
     */
    @Nested
    @DisplayName("semana de prova")
    class SemanaDeProva {

        private final List<DiaSemana> dias = List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA,
                DiaSemana.QUINTA, DiaSemana.SABADO, DiaSemana.DOMINGO);

        @Test
        @DisplayName("CA3: preferido na véspera da prova (sábado, prova no domingo) → nada muda")
        void preferidoNaVespera() {
            var plano = plano(List.of(treino("SEGUNDA", "LONGO"), treino("TERCA", "CONTINUO"),
                    treino("QUINTA", "FACIL"), treino("SABADO", "REGENERATIVO"), treino("DOMINGO", "PROVA")), List.of());

            assertThat(anchor.ancorar(plano, ctxComProva(DiaSemana.SABADO, "DOMINGO"))).isSameAs(plano);
        }

        @Test
        @DisplayName("CA3: preferido no dia da prova → nada muda (a PROVA não troca de dia com o longo)")
        void preferidoNoDiaDaProva() {
            var plano = plano(List.of(treino("SEGUNDA", "LONGO"), treino("TERCA", "CONTINUO"),
                    treino("QUINTA", "FACIL"), treino("DOMINGO", "PROVA")), List.of());

            assertThat(anchor.ancorar(plano, ctxComProva(DiaSemana.DOMINGO, "DOMINGO"))).isSameAs(plano);
        }

        @Test
        @DisplayName("CA3: preferido no dia seguinte à prova (prova na quarta, preferido quinta) → nada muda")
        void preferidoNoDiaSeguinte() {
            var plano = plano(List.of(treino("SEGUNDA", "FACIL"), treino("TERCA", "FACIL"), treino("QUARTA", "PROVA"),
                    treino("QUINTA", "FACIL"), treino("DOMINGO", "LONGO")), List.of());

            assertThat(anchor.ancorar(plano, ctxComProva(DiaSemana.QUINTA, "QUARTA"))).isSameAs(plano);
        }

        @Test
        @DisplayName("CA3: preferido longe da prova (terça, prova no domingo) → ancora como antes")
        void preferidoLongeDaProva() {
            var plano = plano(List.of(treino("SEGUNDA", "LONGO"), treino("TERCA", "CONTINUO"),
                    treino("QUINTA", "FACIL"), treino("DOMINGO", "PROVA")), List.of());

            var resultado = anchor.ancorar(plano, ctxComProva(DiaSemana.TERCA, "DOMINGO"));

            assertThat(diaDoTipo(resultado, "LONGO")).isEqualTo("TERCA");
            assertThat(diaDoTipo(resultado, "PROVA")).isEqualTo("DOMINGO");
        }

        @Test
        @DisplayName("sem PROVA na lista do contexto, a âncora funciona como antes")
        void semProvaNaListaDoContexto() {
            var plano = plano(List.of(treino("SEGUNDA", "LONGO"), treino("TERCA", "CONTINUO"),
                    treino("QUINTA", "FACIL"), treino("SABADO", "REGENERATIVO")), List.of());

            var resultado = anchor.ancorar(plano, ctx(DiaSemana.SABADO));

            assertThat(diaDoTipo(resultado, "LONGO")).isEqualTo("SABADO");
        }

        private WeeklyCoverageContext ctxComProva(DiaSemana diaPreferidoLongo, String diaDaProva) {
            var prova = new TreinoPlanejadoLlmDto(diaDaProva, "PROVA", null, null, null, null, null,
                    null, null, null, null, "Prova", "Zona 3-4", java.util.UUID.randomUUID());
            return new WeeklyCoverageContext(dias, List.of(), false, diaPreferidoLongo, 7, List.of(prova));
        }
    }

    // ---------- fixtures ----------

    private static WeeklyCoverageContext ctx(DiaSemana diaPreferidoLongo) {
        return new WeeklyCoverageContext(DIAS,
                List.of(FatigueSignal.de(FatigueSignalType.READINESS_DESCANSAR)), true, diaPreferidoLongo, 7);
    }

    private static String diaDoTipo(PlanoSemanalLlmDto plano, String tipo) {
        return plano.treinosPlanejados().stream()
                .filter(t -> tipo.equals(t.tipoTreino()))
                .map(TreinoPlanejadoLlmDto::diaSemana)
                .findFirst().orElseThrow();
    }

    private static TreinoPlanejadoLlmDto treino(String dia, String tipo) {
        return new TreinoPlanejadoLlmDto(dia, tipo, null, null, null, null, null, null, null, null, List.of());
    }

    private static PlanoSemanalLlmDto plano(List<TreinoPlanejadoLlmDto> treinos, List<RestDayLlmDto> descansos) {
        return new PlanoSemanalLlmDto(0.0, 0.0, null, null, "PLANEJADO", "objetivo",
                Arrays.asList(treinos.toArray(TreinoPlanejadoLlmDto[]::new)), descansos);
    }
}
