package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.RestDayLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FatigueSignalType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Descanso que a regra não aceita é resolvido sem turno de reparo
 * (add-descanso-explicito-por-fadiga).
 *
 * <p>Regressão da geração real de 22/09 21:11: a LLM pôs treino <b>e</b> descanso na quinta, o reparo
 * repetiu o erro e a geração falhou nas duas tentativas — o treinador ficou sem plano nenhum.</p>
 */
@DisplayName("DescansoNaoAutorizadoConverter")
class DescansoNaoAutorizadoConverterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DescansoNaoAutorizadoConverter converter = new DescansoNaoAutorizadoConverter(registry);

    @Nested
    @DisplayName("dia com treino e descanso")
    class DiaComTreinoEDescanso {

        @Test
        @DisplayName("regressão 22/09 21:11: o descanso cai e o treino do dia fica")
        void treinoVenceDescanso() {
            var plano = plano(List.of(treino("QUINTA")), List.of(new RestDayLlmDto("QUINTA", "recuperação")));

            var resultado = converter.converter(plano, ctx(List.of(DiaSemana.QUINTA, DiaSemana.SABADO), true));

            assertThat(resultado.restDays()).isEmpty();
            assertThat(resultado.treinosPlanejados()).hasSize(1)
                    .extracting(TreinoPlanejadoLlmDto::diaSemana).containsExactly("QUINTA");
            assertThat(registry.counter("plano_descanso_nao_autorizado", "motivo", "dia_com_treino").count())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("comparação de dia ignora caixa e espaços")
        void normalizaDia() {
            var plano = plano(List.of(treino("QUINTA")), List.of(new RestDayLlmDto(" quinta ", "recuperação")));

            assertThat(converter.converter(plano, ctx(List.of(DiaSemana.QUINTA), true)).restDays()).isEmpty();
        }
    }

    @Nested
    @DisplayName("descanso sem sinal que o libere")
    class SemSinal {

        @Test
        @DisplayName("vira treino leve REGENERATIVO de 30 min, com as três etapas")
        void viraTreinoLeve() {
            var plano = plano(List.of(treino("SABADO")), List.of(new RestDayLlmDto("QUINTA", "cansaço")));

            // semanaAtual=false => sinal de dia não libera descanso em lugar nenhum
            var resultado = converter.converter(plano, ctx(List.of(DiaSemana.QUINTA, DiaSemana.SABADO), false));

            assertThat(resultado.restDays()).isEmpty();
            assertThat(resultado.treinosPlanejados()).hasSize(2);
            var leve = resultado.treinosPlanejados().stream()
                    .filter(t -> "QUINTA".equals(t.diaSemana())).findFirst().orElseThrow();
            assertThat(leve.tipoTreino()).isEqualTo("REGENERATIVO");
            assertThat(leve.duracaoMin()).isEqualTo("30:00");
            assertThat(leve.etapas()).extracting(EtapaTreinoLlmDto::tipoEtapa)
                    .containsExactly("AQUECIMENTO", "PRINCIPAL", "DESAQUECIMENTO");
            assertThat(leve.etapas()).extracting(EtapaTreinoLlmDto::duracaoMin).containsExactly(5, 20, 5);
            // distância e ritmo ficam para o normalizador derivar do pace do atleta
            assertThat(leve.distanciaKm()).isNull();
            assertThat(leve.ritmoAlvo()).isNull();
            assertThat(registry.counter("plano_descanso_nao_autorizado", "motivo", "sem_sinal").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("descanso autorizado pelo check-in do dia é preservado")
        void descansoAutorizadoFica() {
            var descanso = new RestDayLlmDto("QUINTA", "check-in de hoje: DESCANSAR");
            var plano = plano(List.of(treino("SABADO")), List.of(descanso));

            var resultado = converter.converter(plano, ctx(List.of(DiaSemana.QUINTA, DiaSemana.SABADO), true));

            assertThat(resultado.restDays()).containsExactly(descanso);
            assertThat(resultado.treinosPlanejados()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("casos que este conversor não decide")
    class NaoDecide {

        @Test
        @DisplayName("dia fora dos disponíveis fica para o descarte do validador")
        void diaForaDosDisponiveis() {
            var descanso = new RestDayLlmDto("DOMINGO", "folga");
            var plano = plano(List.of(treino("SABADO")), List.of(descanso));

            var resultado = converter.converter(plano, ctx(List.of(DiaSemana.QUINTA, DiaSemana.SABADO), true));

            assertThat(resultado.restDays()).containsExactly(descanso);
        }

        @Test
        @DisplayName("dia inválido fica para a violação do validador")
        void diaInvalido() {
            var descanso = new RestDayLlmDto("FUNDAY", "folga");
            var plano = plano(List.of(treino("SABADO")), List.of(descanso));

            assertThat(converter.converter(plano, ctx(List.of(DiaSemana.SABADO), true)).restDays())
                    .containsExactly(descanso);
        }

        @Test
        @DisplayName("sem contexto de cobertura (kill-switch ou planner) devolve o plano intacto")
        void semCobertura() {
            var plano = plano(List.of(treino("QUINTA")), List.of(new RestDayLlmDto("QUINTA", "x")));

            assertThat(converter.converter(plano, null)).isSameAs(plano);
        }

        @Test
        @DisplayName("plano sem descanso devolve a mesma instância")
        void semDescanso() {
            var plano = plano(List.of(treino("QUINTA")), List.of());

            assertThat(converter.converter(plano, ctx(List.of(DiaSemana.QUINTA), true))).isSameAs(plano);
        }

        @Test
        @DisplayName("nada a mudar devolve a mesma instância — reaplicar é no-op")
        void idempotente() {
            var plano = plano(List.of(treino("SABADO")), List.of(new RestDayLlmDto("QUINTA", "check-in")));
            var ctx = ctx(List.of(DiaSemana.QUINTA, DiaSemana.SABADO), true);

            var uma = converter.converter(plano, ctx);
            assertThat(converter.converter(uma, ctx)).isSameAs(uma);
        }
    }

    private static WeeklyCoverageContext ctx(List<DiaSemana> dias, boolean semanaAtual) {
        return new WeeklyCoverageContext(dias,
                List.of(FatigueSignal.de(FatigueSignalType.READINESS_DESCANSAR)),
                semanaAtual, null, 3);
    }

    private static PlanoSemanalLlmDto plano(List<TreinoPlanejadoLlmDto> treinos, List<RestDayLlmDto> descansos) {
        return PlanoSemanalLlmDto.builder()
                .treinosPlanejados(treinos)
                .restDays(descansos)
                .build();
    }

    private static TreinoPlanejadoLlmDto treino(String dia) {
        return new TreinoPlanejadoLlmDto(dia, "CONTINUO", null, null, null, null, null, null, null, null, List.of(),
                null, null, null);
    }
}
