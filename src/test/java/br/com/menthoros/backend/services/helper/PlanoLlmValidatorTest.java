package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.ai.ledger.Violacao;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.exception.PlanoNaoConformeException;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.ContextoTreino;
import br.com.menthoros.backend.services.prompt.PaceHistoricoFormatter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code plan-generation-repair-turn}, seção 3 — {@code validarENormalizarPlano} passa a percorrer
 * TODOS os treinos do plano antes de decidir, em vez de abortar no primeiro treino inválido
 * (achado do pré-mortem: {@code .stream().map(normalizar).toList()} escondia o 2º treino quebrado
 * de um plano com 2+ treinos malformados). {@code NormalizacaoDeTreino} continua abortando na 1ª
 * violação <b>dentro</b> de um treino (F2.5, não reaberto) — o gatilho de falha aqui é
 * {@code validar-repeticoes} (família-agnóstico: qualquer etapa com {@code repeticoes != 1}).
 */
@DisplayName("PlanoLlmValidator — coleta de violações de todos os treinos")
class PlanoLlmValidatorTest {

    private static final UUID ATLETA_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private Atleta atleta;

    @BeforeEach
    void setUp() {
        atleta = Atleta.builder()
                .id(ATLETA_ID)
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(BigDecimal.valueOf(5.0))
                .build();
    }

    @Nested
    @DisplayName("validarENormalizarPlano")
    class ValidarENormalizarPlano {

        @Test
        @DisplayName("plano sem treinos inválidos passa normalmente")
        void planoValidoPassa() {
            var plano = plano(treinoValido("SEGUNDA"), treinoValido("QUARTA"));

            var resultado = validador().validarENormalizarPlano(plano, atleta, ATLETA_ID);

            assertThat(resultado.treinosPlanejados()).hasSize(2);
        }

        @Test
        @DisplayName("1 treino inválido entre 2 → PlanoNaoConformeException com 1 Violacao")
        void umTreinoInvalido() {
            var plano = plano(treinoValido("SEGUNDA"), treinoInvalido("QUARTA"));

            assertThatThrownBy(() -> validador().validarENormalizarPlano(plano, atleta, ATLETA_ID))
                    .isInstanceOf(PlanoNaoConformeException.class)
                    .satisfies(e -> {
                        var violacoes = ((PlanoNaoConformeException) e).violacoes();
                        assertThat(violacoes).hasSize(1);
                        assertThat(violacoes.get(0).key()).contains("QUARTA");
                        assertThat(violacoes.get(0).mensagem()).contains("repeticoes");
                    });
        }

        @Test
        @DisplayName("2 treinos inválidos → PlanoNaoConformeException carrega as 2 Violacao, uma por dia — não só a primeira")
        void doisTreinosInvalidos() {
            var plano = plano(treinoValido("SEGUNDA"), treinoInvalido("QUARTA"), treinoInvalido("SEXTA"));

            assertThatThrownBy(() -> validador().validarENormalizarPlano(plano, atleta, ATLETA_ID))
                    .isInstanceOf(PlanoNaoConformeException.class)
                    .satisfies(e -> {
                        var violacoes = ((PlanoNaoConformeException) e).violacoes();
                        assertThat(violacoes).hasSize(2);
                        assertThat(violacoes).extracting(Violacao::key)
                                .anyMatch(k -> k.contains("QUARTA"))
                                .anyMatch(k -> k.contains("SEXTA"));
                    });
        }

        @Test
        @DisplayName("todos os treinos inválidos → todas as violações chegam, nenhuma perdida por abortar cedo")
        void todosInvalidos() {
            var plano = plano(treinoInvalido("SEGUNDA"), treinoInvalido("QUARTA"), treinoInvalido("SEXTA"));

            assertThatThrownBy(() -> validador().validarENormalizarPlano(plano, atleta, ATLETA_ID))
                    .isInstanceOf(PlanoNaoConformeException.class)
                    .satisfies(e -> assertThat(((PlanoNaoConformeException) e).violacoes()).hasSize(3));
        }
    }

    @Nested
    @DisplayName("validarPlanoV2 (semantic-session-schema)")
    class ValidarPlanoV2 {

        @Test
        @DisplayName("plano v2 resolvido, estruturalmente válido, sem skeleton — passa")
        void planoValidoSemSkeletonPassa() {
            var plano = plano(treinoTresEtapasValido("SEGUNDA"));

            var resultado = validador().validarPlanoV2(plano, atleta, ATLETA_ID, null);

            assertThat(resultado.treinosPlanejados()).hasSize(1);
        }

        @Test
        @DisplayName("2 treinos com estrutura inválida → PlanoNaoConformeException com 2 Violacao")
        void doisTreinosEstruturalmenteInvalidos() {
            var plano = plano(treinoTresEtapasInvalido("SEGUNDA"), treinoTresEtapasInvalido("QUARTA"));

            assertThatThrownBy(() -> validador().validarPlanoV2(plano, atleta, ATLETA_ID, null))
                    .isInstanceOf(PlanoNaoConformeException.class)
                    .satisfies(e -> {
                        var violacoes = ((PlanoNaoConformeException) e).violacoes();
                        assertThat(violacoes).hasSize(2);
                        assertThat(violacoes).extracting(Violacao::key)
                                .anyMatch(k -> k.contains("SEGUNDA"))
                                .anyMatch(k -> k.contains("QUARTA"));
                    });
        }

        @Test
        @DisplayName("TSS resolvido fora de ±20% do slot — rejeita")
        void tssForaDaFaixaDoSlotRejeitado() {
            var plano = plano(treinoTresEtapasComTss("SEGUNDA", 80));
            var skeleton = skeletonComSlot(java.time.DayOfWeek.MONDAY, 50.0);

            assertThatThrownBy(() -> validador().validarPlanoV2(plano, atleta, ATLETA_ID, skeleton))
                    .isInstanceOf(PlanoNaoConformeException.class)
                    .satisfies(e -> assertThat(((PlanoNaoConformeException) e).violacoes())
                            .anySatisfy(v -> assertThat(v.mensagem()).contains("TSS resolvido")));
        }

        @Test
        @DisplayName("TSS resolvido dentro de ±20% do slot — passa")
        void tssDentroDaFaixaDoSlotPassa() {
            var plano = plano(treinoTresEtapasComTss("SEGUNDA", 55));
            var skeleton = skeletonComSlot(java.time.DayOfWeek.MONDAY, 50.0);

            var resultado = validador().validarPlanoV2(plano, atleta, ATLETA_ID, skeleton);

            assertThat(resultado.treinosPlanejados()).hasSize(1);
        }

        @Test
        @DisplayName("achado do /qa (code-reviewer + codex): diaSemana malformado não lança IllegalArgumentException — pula a checagem de TSS")
        void diaSemanaMalformadoNaoLancaIllegalArgumentException() {
            var treinoComDiaInvalido = new TreinoPlanejadoLlmDto("SEGUNDA-FEIRA", "REGENERATIVO",
                    "120-136 bpm", 55, 0.6, 3, "Recuperação ativa", "30:00", 4.0, "6:30-7:00/km",
                    treinoTresEtapasValido("x").etapas());
            var plano = plano(treinoComDiaInvalido);
            var skeleton = skeletonComSlot(java.time.DayOfWeek.MONDAY, 50.0);

            // Não lança IllegalArgumentException (DiaSemana.valueOf) — encontrarSlot devolve null,
            // a checagem de TSS é pulada, e o resto do plano é validado normalmente.
            var resultado = validador().validarPlanoV2(plano, atleta, ATLETA_ID, skeleton);

            assertThat(resultado.treinosPlanejados()).hasSize(1);
        }
    }

    // ---------- arranjo ----------

    private static WeekPlanSkeleton skeletonComSlot(java.time.DayOfWeek dia, double targetTss) {
        var slot = new br.com.menthoros.backend.domain.planner.SessionSlot(dia, "REGENERATIVO", targetTss, "Z2", false, 30);
        return new WeekPlanSkeleton(null, null, List.of(slot), null, null, false, null,
                LocalDate.of(2026, 9, 14), null, java.util.Optional.empty());
    }

    private static TreinoPlanejadoLlmDto treinoTresEtapasValido(String dia) {
        return new TreinoPlanejadoLlmDto(dia, "REGENERATIVO", "120-136 bpm", 40, 0.6, 3,
                "Recuperação ativa", "30:00", 4.0, "6:30-7:00/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", null, 5, 0.7, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", null, 20, 2.6, "120-136 bpm", 1, "6:30-7:00/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", null, 5, 0.7, "120-136 bpm", 1, null)));
    }

    private static TreinoPlanejadoLlmDto treinoTresEtapasComTss(String dia, int tssPlanejado) {
        var base = treinoTresEtapasValido(dia);
        return new TreinoPlanejadoLlmDto(base.diaSemana(), base.tipoTreino(), base.fcAlvo(), tssPlanejado,
                base.intensidadePlanejada(), base.percepcaoEsforcoEsperada(), base.justificativaIa(),
                base.duracaoMin(), base.distanciaKm(), base.ritmoAlvo(), base.etapas());
    }

    /** Só 2 etapas (esperado 3) — validarEstrutura3Etapas rejeita. */
    private static TreinoPlanejadoLlmDto treinoTresEtapasInvalido(String dia) {
        return new TreinoPlanejadoLlmDto(dia, "REGENERATIVO", "120-136 bpm", 40, 0.6, 3,
                "Recuperação ativa", "30:00", 4.0, "6:30-7:00/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", null, 5, 0.7, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", null, 20, 2.6, "120-136 bpm", 1, "6:30-7:00/km")));
    }

    private PlanoLlmValidator validador() {
        TreinoHistoricoProvider treinoHistoricoProvider = mock(TreinoHistoricoProvider.class);
        when(treinoHistoricoProvider.prepararContexto(atleta)).thenReturn(
                new ContextoTreino(LocalDate.of(2026, 9, 14), List.of(), List.of(), List.of()));
        PaceHistoricoFormatter paceHistoricoFormatter = mock(PaceHistoricoFormatter.class);
        when(paceHistoricoFormatter.calcularTetoPorTipo(any())).thenReturn(java.util.Map.of());
        when(paceHistoricoFormatter.calcularPisoPorTipo(any())).thenReturn(java.util.Map.of());
        ZonaTreinoService zonaTreinoService = mock(ZonaTreinoService.class);

        return new PlanoLlmValidator(
                treinoHistoricoProvider,
                paceHistoricoFormatter,
                zonaTreinoService,
                new NormalizacaoDeTreino(
                        new TreinoNormalizador(new PaceValidator()),
                        new EtapaFcValidator(),
                        new PlanoEstruturaReparador(new SimpleMeterRegistry()),
                        new PaceValidator(),
                        new SimpleMeterRegistry()),
                new DescansoNaoAutorizadoConverter(new SimpleMeterRegistry()),
                new WeeklyCoverageValidator(), new LongRunAnchor());
    }

    /**
     * add-descanso-explicito-por-fadiga (task 2.2): a regra de cobertura entra no mesmo caminho das
     * violações estruturais — volta como PlanoNaoConformeException e vira turno de reparo.
     */
    @Nested
    @DisplayName("cobertura da semana")
    class Cobertura {

        private final List<DiaSemana> dias =
                List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUINTA);

        @Test
        @DisplayName("dia disponível sem treino → PlanoNaoConformeException com COBERTURA_DIAS")
        void diaOmitidoReprova() {
            var plano = plano(treinoValido("SEGUNDA"), treinoValido("TERCA"));

            assertThatThrownBy(() -> validador().validarENormalizarPlano(
                    plano, atleta, ATLETA_ID, contexto(List.of())))
                    .isInstanceOf(PlanoNaoConformeException.class)
                    .satisfies(e -> assertThat(((PlanoNaoConformeException) e).violacoes())
                            .extracting(v -> v.key()).contains("COBERTURA_DIAS"));
        }

        @Test
        @DisplayName("todos os dias cobertos → passa")
        void coberturaCompletaPassa() {
            var plano = plano(treinoValido("SEGUNDA"), treinoValido("TERCA"), treinoValido("QUINTA"));

            var resultado = validador().validarENormalizarPlano(plano, atleta, ATLETA_ID, contexto(List.of()));

            assertThat(resultado.treinosPlanejados()).hasSize(3);
        }

        @Test
        @DisplayName("kill-switch: sem contexto de cobertura, dia omitido passa como antes da change")
        void semContextoPassa() {
            var plano = plano(treinoValido("SEGUNDA"), treinoValido("TERCA"));

            var resultado = validador().validarENormalizarPlano(plano, atleta, ATLETA_ID, null);

            assertThat(resultado.treinosPlanejados()).hasSize(2);
        }

        @Test
        @DisplayName("a âncora do longo roda antes da validação: o plano validado já sai com o longo no dia preferido")
        void ancoraRodaAntesDaValidacao() {
            var plano = plano(longo("SEGUNDA"), treinoValido("TERCA"), treinoValido("QUINTA"));
            var ctx = new WeeklyCoverageContext(dias, List.of(), true, DiaSemana.QUINTA, 7);

            var resultado = validador().validarENormalizarPlano(plano, atleta, ATLETA_ID, ctx);

            assertThat(resultado.treinosPlanejados())
                    .filteredOn(t -> "LONGO".equals(t.tipoTreino()))
                    .singleElement()
                    .satisfies(t -> assertThat(t.diaSemana()).isEqualTo("QUINTA"));
        }

        private WeeklyCoverageContext contexto(List<FatigueSignal> sinais) {
            return new WeeklyCoverageContext(dias, sinais, true, null, 7);
        }
    }

    private static TreinoPlanejadoLlmDto longo(String dia) {
        return new TreinoPlanejadoLlmDto(dia, "LONGO", "130-145 bpm", 60, 0.7, 5,
                "Longo", "60:00", 10.0, "6:00-6:30/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.6, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Longo contínuo", 45, 7.0, "130-145 bpm", 1, "6:00-6:30/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Caminhada", 5, 0.8, "120-136 bpm", 1, null)));
    }

    private static PlanoSemanalLlmDto plano(TreinoPlanejadoLlmDto... treinos) {
        return new PlanoSemanalLlmDto(30.0, 30.0, null, null, "ATIVO", "base aeróbica", List.of(treinos), List.of());
    }

    private static TreinoPlanejadoLlmDto treinoValido(String dia) {
        return new TreinoPlanejadoLlmDto(dia, "FACIL", "130-145 bpm", 40, 0.7, 4,
                "Rodagem fácil", "40:00", 6.5, "6:00-6:30/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 5, 0.8, "120-136 bpm", 1, null),
                        new EtapaTreinoLlmDto(2, "PRINCIPAL", "Rodagem confortável", 30, 5.0, "130-145 bpm", 1, "6:00-6:30/km"),
                        new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Caminhada", 5, 0.7, "120-136 bpm", 1, null)));
    }

    /** repeticoes=2 é rejeitado por validar-repeticoes (cauda comum, família-agnóstico). */
    private static TreinoPlanejadoLlmDto treinoInvalido(String dia) {
        return new TreinoPlanejadoLlmDto(dia, "FACIL", "130-145 bpm", 40, 0.7, 4,
                "Rodagem fácil", "40:00", 6.5, "6:00-6:30/km",
                List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 5, 0.8, "120-136 bpm", 2, null)));
    }
}
