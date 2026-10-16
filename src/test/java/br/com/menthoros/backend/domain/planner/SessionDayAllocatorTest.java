package br.com.menthoros.backend.domain.planner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;

import static java.time.DayOfWeek.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionDayAllocator")
class SessionDayAllocatorTest {

    private final SessionDayAllocator allocator = new SessionDayAllocator();
    private static final List<DayOfWeek> SEMANA_TODA = List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY);

    private static SessionSlot slot(String tipo, boolean chave) {
        return new SessionSlot(null, tipo, 50.0, "zona", chave, 45);
    }

    private static DayOfWeek diaDe(List<SessionSlot> s, String tipo) {
        return s.stream().filter(x -> x.sessionType().equals(tipo)).map(SessionSlot::day).findFirst().orElseThrow();
    }

    private static int distanciaCircular(DayOfWeek a, DayOfWeek b) {
        int diff = Math.abs(a.getValue() - b.getValue());
        return Math.min(diff, 7 - diff);
    }

    @Nested
    @DisplayName("sessão-chave LONGO")
    class Longo {

        @Test
        @DisplayName("ancora no dia preferido quando disponível")
        void diaPreferido() {
            var s = allocator.allocate(List.of(slot("LONGO", true), slot("FACIL", false)), SEMANA_TODA, SATURDAY, List.of());
            assertThat(diaDe(s, "LONGO")).isEqualTo(SATURDAY);
        }

        @Test
        @DisplayName("sem dia preferido, vai para o último dia disponível")
        void ultimoDia() {
            var s = allocator.allocate(List.of(slot("LONGO", true), slot("FACIL", false)),
                    List.of(MONDAY, WEDNESDAY, FRIDAY), null, List.of());
            assertThat(diaDe(s, "LONGO")).isEqualTo(FRIDAY);
        }

        @Test
        @DisplayName("CA12: LONGO sem chave (TAPER) também ganha dia normalmente, sem prova na semana")
        void longoSemChaveGanhaDia() {
            var s = allocator.allocate(List.of(slot("LONGO", false), slot("FACIL", false), slot("REGENERATIVO", false)),
                    List.of(MONDAY, WEDNESDAY, FRIDAY), null, List.of());
            assertThat(diaDe(s, "LONGO")).isEqualTo(FRIDAY);
        }

        @Test
        @DisplayName("CA12: LONGO sem chave não cai na véspera de uma prova da semana")
        void longoSemChaveNaoCaiNaVesperaDaProva() {
            // SEG/QUA/SEX disponíveis, prova no sábado -> proibido = sexta (D-1). LONGO deve ir pra quarta.
            var s = allocator.allocate(List.of(slot("LONGO", false), slot("FACIL", false), slot("REGENERATIVO", false)),
                    List.of(MONDAY, WEDNESDAY, FRIDAY), null, List.of(SATURDAY));
            assertThat(diaDe(s, "LONGO")).isEqualTo(WEDNESDAY);
        }

        @Test
        @DisplayName("preferido cai no D-1 de uma prova: o LONGO não vai para lá, usa o último dia livre restante")
        void preferidoNoDMenosUmDaProva() {
            var s = allocator.allocate(List.of(slot("LONGO", true), slot("FACIL", false)),
                    List.of(MONDAY, WEDNESDAY, FRIDAY), FRIDAY, List.of(SATURDAY));
            assertThat(diaDe(s, "LONGO")).isEqualTo(WEDNESDAY);
        }
    }

    @Nested
    @DisplayName("sessões duras não-adjacentes")
    class Duras {

        @Test
        @DisplayName("duas duras ficam com ≥2 dias de distância (semana cheia)")
        void naoAdjacentes() {
            var s = allocator.allocate(
                    List.of(slot("LONGO", true), slot("INTERVALADO", false), slot("TEMPO_RUN", false)),
                    SEMANA_TODA, SATURDAY, List.of());
            assertThat(distanciaCircular(diaDe(s, "INTERVALADO"), diaDe(s, "TEMPO_RUN"))).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("tasks.md 7.2: o LONGO é âncora de intensidade — a dura não cai vizinha dele")
        void duraNaoViraVizinhaDoLongo() {
            // Semana cheia, sem preferido: LONGO vai ao domingo. Sem o LONGO em diasComDura a dura cairia
            // na segunda (vizinha circular); com ele, o dia de maior distância é a quarta.
            var s = allocator.allocate(
                    List.of(slot("LONGO", true), slot("INTERVALADO", false)),
                    SEMANA_TODA, null, List.of());
            assertThat(diaDe(s, "LONGO")).isEqualTo(SUNDAY);
            assertThat(diaDe(s, "INTERVALADO")).isEqualTo(WEDNESDAY);
        }

        @Test
        @DisplayName("dura nunca cai no D-1 de uma prova da semana")
        void duraNuncaNoDMenosUmDaProva() {
            var s = allocator.allocate(
                    List.of(slot("LONGO", true), slot("INTERVALADO", false)),
                    List.of(MONDAY, WEDNESDAY, FRIDAY, SATURDAY), MONDAY, List.of(SUNDAY));
            assertThat(diaDe(s, "INTERVALADO")).isNotEqualTo(SATURDAY);
        }
    }

    @Nested
    @DisplayName("PROVA")
    class Prova {

        @Test
        @DisplayName("fica no dia real da prova, ignorando disponibilidade")
        void diaRealDaProva() {
            var s = allocator.allocate(
                    List.of(slot("PROVA", true), slot("REGENERATIVO", false), slot("FACIL", false)),
                    List.of(MONDAY, TUESDAY, WEDNESDAY), null, List.of(SUNDAY));
            assertThat(diaDe(s, "PROVA")).isEqualTo(SUNDAY);
            assertThat(s).allSatisfy(x -> assertThat(x.day()).isNotNull());
        }

        @Test
        @DisplayName("duas provas na semana: cada slot PROVA recebe um dia, na ordem")
        void duasProvasNaSemana() {
            var s = allocator.allocate(
                    List.of(slot("PROVA", true), slot("PROVA", false), slot("FACIL", false)),
                    List.of(MONDAY, TUESDAY, WEDNESDAY), null, List.of(SATURDAY, SUNDAY));
            var diasDeProva = s.stream().filter(x -> x.sessionType().equals("PROVA")).map(SessionSlot::day).toList();
            assertThat(diasDeProva).containsExactlyInAnyOrder(SATURDAY, SUNDAY);
        }
    }

    @Nested
    @DisplayName("fixture do caso real (CA9) — QUI/SÁB/DOM, prova no domingo, PROVA + LONGO + dura + leve")
    class FixtureCA9 {

        @Test
        @DisplayName("LONGO ocupa a quinta (passo 2 roda antes); a dura fica sem dia; nada pesado no sábado")
        void longoNaQuintaDuraSemDia() {
            var s = allocator.allocate(
                    List.of(slot("PROVA", true), slot("LONGO", false), slot("INTERVALADO", false), slot("REGENERATIVO", false)),
                    List.of(THURSDAY, SATURDAY, SUNDAY), null, List.of(SUNDAY));

            assertThat(diaDe(s, "PROVA")).isEqualTo(SUNDAY);
            assertThat(diaDe(s, "LONGO")).isEqualTo(THURSDAY);
            SessionSlot dura = s.stream().filter(x -> x.sessionType().equals("INTERVALADO")).findFirst().orElseThrow();
            assertThat(dura.day()).isNull();
            assertThat(s).noneMatch(x -> x.day() == SATURDAY && !x.sessionType().equals("REGENERATIVO"));
        }

        @Test
        @DisplayName("a leve pode cair no sábado (D-1), já que o passo 4 usa a lista inteira de dias livres")
        void leveCaiNoSabado() {
            var s = allocator.allocate(
                    List.of(slot("PROVA", true), slot("LONGO", false), slot("INTERVALADO", false), slot("REGENERATIVO", false)),
                    List.of(THURSDAY, SATURDAY, SUNDAY), null, List.of(SUNDAY));

            assertThat(diaDe(s, "REGENERATIVO")).isEqualTo(SATURDAY);
        }
    }

    @Test
    @DisplayName("todos os slots recebem um dia (slots ≤ dias)")
    void todosComDia() {
        var s = allocator.allocate(List.of(slot("LONGO", true), slot("FACIL", false), slot("REGENERATIVO", false)),
                SEMANA_TODA, SUNDAY, List.of());
        assertThat(s).allSatisfy(x -> assertThat(x.day()).isNotNull());
        assertThat(s.stream().map(SessionSlot::day).distinct().count()).isEqualTo(3); // dias distintos
    }
}
