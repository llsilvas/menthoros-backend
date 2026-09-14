package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.exception.LLMException;
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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testes de {@link PlanoLlmValidator#validarEstrutura3Etapas} — extraído de {@code IaServiceImpl}
 * (refactor-iaservice-decomposition, seção 5). Chamada direta ao colaborador, sem reflexão.
 */
@DisplayName("PlanoLlmValidator — validarEstrutura3Etapas")
class PlanoLlmValidatorTest {

    private PlanoLlmValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PlanoLlmValidator(
                new SimpleMeterRegistry(),
                new PaceValidator(),
                mock(TreinoHistoricoProvider.class),
                mock(br.com.menthoros.backend.services.prompt.PaceHistoricoFormatter.class),
                mock(ZonaTreinoService.class),
                new TreinoNormalizador(new PaceValidator()),
                new EtapaFcValidator(),
                mock(PlanoEstruturaReparador.class)
        );
    }

    @Nested
    @DisplayName("validarEstrutura3Etapas (caracterização do hard-fail atual)")
    class Estrutura3Etapas {

        @Test
        @DisplayName("≠ 3 etapas → LLMException (hoje derruba o plano inteiro)")
        void numeroEtapasErrado() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL")); // 2 etapas
            assertThatThrownBy(() -> validator.validarEstrutura3Etapas(treino, "REGENERATIVO", UUID.randomUUID(), true))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("3 etapas na ordem canônica → ok")
        void ordemCanonica() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL"), etapa("DESAQUECIMENTO"));
            assertThatNoException().isThrownBy(
                    () -> validator.validarEstrutura3Etapas(treino, "REGENERATIVO", UUID.randomUUID(), true));
        }

        @Test
        @DisplayName("fora de ordem com validarOrdem=true → LLMException")
        void ordemTrocada() {
            var treino = treino("REGENERATIVO", etapa("PRINCIPAL"), etapa("AQUECIMENTO"), etapa("DESAQUECIMENTO"));
            assertThatThrownBy(() -> validator.validarEstrutura3Etapas(treino, "REGENERATIVO", UUID.randomUUID(), true))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("LONGO (validarOrdem=false) ignora só a posição de aquec/desaq — meio continua PRINCIPAL")
        void longoIgnoraOrdemDeAquecDesaqMasExigeMeioPrincipal() {
            // validarOrdem=false pula a checagem de posição 0/2 (AQUECIMENTO/DESAQUECIMENTO
            // trocados), mas a etapa central segue tendo que ser PRINCIPAL (fix IA-04).
            var treino = treino("LONGO", etapa("DESAQUECIMENTO"), etapa("PRINCIPAL"), etapa("AQUECIMENTO"));
            assertThatNoException().isThrownBy(
                    () -> validator.validarEstrutura3Etapas(treino, "LONGO", UUID.randomUUID(), false));
        }

        @Test
        @DisplayName("IA-04: etapa central que não é PRINCIPAL → LLMException (validarOrdem=true)")
        void etapaCentralNaoPrincipal_validarOrdemTrue_lancaExcecao() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("RECUPERACAO"), etapa("DESAQUECIMENTO"));
            assertThatThrownBy(() -> validator.validarEstrutura3Etapas(treino, "REGENERATIVO", UUID.randomUUID(), true))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("IA-04 (achado do Codex): etapa central que não é PRINCIPAL → LLMException também em LONGO (validarOrdem=false)")
        void etapaCentralNaoPrincipal_validarOrdemFalse_lancaExcecao() {
            var treino = treino("LONGO", etapa("AQUECIMENTO"), etapa("RECUPERACAO"), etapa("DESAQUECIMENTO"));
            assertThatThrownBy(() -> validator.validarEstrutura3Etapas(treino, "LONGO", UUID.randomUUID(), false))
                    .isInstanceOf(LLMException.class);
        }

        private EtapaTreinoLlmDto etapa(String tipo) {
            return new EtapaTreinoLlmDto(1, tipo, "x", 10, 1.0, null, 1, null);
        }

        private TreinoPlanejadoLlmDto treino(String tipo, EtapaTreinoLlmDto... etapas) {
            return new TreinoPlanejadoLlmDto("SEGUNDA", tipo, null, null, null, null, null, null, null, null, List.of(etapas));
        }
    }

    @Nested
    @DisplayName("ordem gate estrutural → normalização → recheck de duração (achados do /qa: Codex + code-reviewer)")
    class ValidacaoPosNormalizacaoIA05 {

        private PlanoLlmValidator validatorComposto;
        private Atleta atleta;

        @BeforeEach
        void setUp() {
            atleta = Atleta.builder()
                    .id(UUID.randomUUID())
                    .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                    .paceLimiar(BigDecimal.valueOf(5.0))
                    .build();

            TreinoHistoricoProvider treinoHistoricoProvider = mock(TreinoHistoricoProvider.class);
            when(treinoHistoricoProvider.prepararContexto(atleta)).thenReturn(
                    new ContextoTreino(LocalDate.of(2026, 9, 14), List.of(), List.of(), List.of()));

            PaceHistoricoFormatter paceHistoricoFormatter = mock(PaceHistoricoFormatter.class);
            when(paceHistoricoFormatter.calcularTetoPorTipo(any())).thenReturn(java.util.Map.of());
            when(paceHistoricoFormatter.calcularPisoPorTipo(any())).thenReturn(java.util.Map.of());

            PlanoEstruturaReparador estruturaReparador = mock(PlanoEstruturaReparador.class);
            when(estruturaReparador.reparar(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            validatorComposto = new PlanoLlmValidator(
                    new SimpleMeterRegistry(),
                    new PaceValidator(),
                    treinoHistoricoProvider,
                    paceHistoricoFormatter,
                    mock(ZonaTreinoService.class),
                    new TreinoNormalizador(new PaceValidator()),
                    new EtapaFcValidator(),
                    estruturaReparador
            );
        }

        @Test
        @DisplayName("tiro com duracaoMin inicialmente válida, mas que passa de 10min após o "
                + "crescimento de distanciaKm (IA-05) → deve continuar rejeitando (LLMException), "
                + "não silenciar a violação fisiológica")
        void tiroQuePassaDoLimiteDeDuracaoAposCrescimentoDeDistancia_continuaRejeitado() {
            // 4 tiros de 0.8km (duracaoMin=4, arbitrário/compatível) + 3 rec de 0.3km + aquec/desaq.
            // Mecânica: (Passo 0) corrigirDistanciasEtapasTemporais deriva aquec/desaq de
            // duracaoMin=10 × paceZ2 (paceLimiar 5.0 → 6.0 min/km) = 1.67km (já é o valor dado, pra
            // não haver surpresa); normalizarTreinoIntervalado então clampa DESAQUECIMENTO pra
            // 1.5km (teto 0.8-1.5) e calcula soma = 1.67+3.2+0.9+1.5 = 7.27km contra o alvo de 7.84
            // → gap +0.57km (< 0.6, então NÃO sintetiza tiro novo), distribuído entre os 4 tiros:
            // cada um cresce pra ~0.94km. Com ritmoAlvo="12:00-12:00/km" (12 min/km), o IA-05
            // recalcula duracaoMin = round(0.94 × 12) = 11min — acima do teto de 10min. Antes do
            // fix, validarTreinoIntervalado rodava só ANTES dessa normalização e via duracaoMin=4.
            var tiro = new EtapaTreinoLlmDto(1, "INTERVALADO", "Intervalo Z5", 4, 0.8,
                    "90-95% FCmax", 1, "12:00-12:00/km");
            var rec = new EtapaTreinoLlmDto(1, "RECUPERACAO", "Recuperação trote", 2, 0.3,
                    "60-70% FCmax", 1, null);
            var aquecimento = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.67,
                    "120-136 bpm", 1, null);
            var desaquecimento = new EtapaTreinoLlmDto(1, "DESAQUECIMENTO", "Caminhada", 10, 1.67,
                    "120-136 bpm", 1, null);

            var treino = new TreinoPlanejadoLlmDto(
                    "TERCA", "INTERVALADO", "150-160 bpm", 60, 8.0, 6, "Estímulo de VO2max.",
                    "50:00", 7.84, "5:00-5:15/km",
                    List.of(aquecimento, tiro, rec, tiro, rec, tiro, rec, tiro, desaquecimento));

            PlanoSemanalLlmDto plano = new PlanoSemanalLlmDto(
                    30.0, 30.0, null, null, "ATIVO", "base aeróbica", List.of(treino));

            assertThatThrownBy(() -> validatorComposto.validarENormalizarPlano(plano, atleta, atleta.getId()))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("duração incoerente");
        }

        @Test
        @DisplayName("achado do code-reviewer (2ª rodada /qa): treino INTERVALADO com só 4 etapas da LLM "
                + "deve continuar sendo rejeitado (retry), não completado silenciosamente pelo padding "
                + "de adicionarTiroERecuperacao")
        void treinoComEtapasInsuficientes_naoEMascaradoPeloPaddingDaNormalizacao() {
            // AQUECIMENTO + 1 tiro + 1 rec + DESAQUECIMENTO = 4 etapas (mínimo é 6), sem padrão
            // "NxDist" na descrição pra expandirEtapasAgregadas expandir. Soma = 1.67+0.8+0.3+1.67
            // = 4.44km contra alvo de 8.0km → gap de 3.56km > 0.6, então normalizarTreinoIntervalado
            // sintetizaria pares tiro+rec até bater 6+ etapas. Se o gate de contagem mínima rodar só
            // DEPOIS disso, o treino quebrado passa com etapas fabricadas pelo normalizador, não
            // pela LLM — o retry com feedback nunca é acionado.
            var tiro = new EtapaTreinoLlmDto(1, "INTERVALADO", "Intervalo Z5", 4, 0.8,
                    "90-95% FCmax", 1, null);
            var rec = new EtapaTreinoLlmDto(1, "RECUPERACAO", "Recuperação trote", 2, 0.3,
                    "60-70% FCmax", 1, null);
            var aquecimento = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.67,
                    "120-136 bpm", 1, null);
            var desaquecimento = new EtapaTreinoLlmDto(1, "DESAQUECIMENTO", "Caminhada", 10, 1.67,
                    "120-136 bpm", 1, null);

            var treino = new TreinoPlanejadoLlmDto(
                    "TERCA", "INTERVALADO", "150-160 bpm", 60, 8.0, 6, "Estímulo de VO2max.",
                    "50:00", 8.0, "5:00-5:15/km",
                    List.of(aquecimento, tiro, rec, desaquecimento));

            PlanoSemanalLlmDto plano = new PlanoSemanalLlmDto(
                    30.0, 30.0, null, null, "ATIVO", "base aeróbica", List.of(treino));

            assertThatThrownBy(() -> validatorComposto.validarENormalizarPlano(plano, atleta, atleta.getId()))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("mínimo 6");
        }
    }
}
