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
    @DisplayName("achado do Codex (adversarial review, 2026-09-14): validarTreinoIntervalado roda "
            + "ANTES da normalização que o IA-05 passou a alterar duracaoMin")
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
            // 4 tiros de 0.8km (duracaoMin=4, arbitrário/compatível) + 3 rec de 0.3km + aquec/desaq
            // — as distâncias de aquec/desaq (1.67km) já são as que corrigirDistanciasEtapasTemporais
            // (Passo 0) produziria pra duracaoMin=10 com paceLimiar=5.0 (paceZ2=6.0 min/km →
            // 10/6.0≈1.67), pra não haver surpresa de soma quando o Passo 0 roda antes da
            // normalização. Soma = 1.67+3.2+0.9+1.67 = 7.44km contra um alvo de 7.84km → gap
            // positivo de 0.4km, distribuído proporcionalmente entre os 4 tiros (mesmo caminho da
            // seção 5/IA-05): cada tiro cresce de 0.8 para 0.9km. Com ritmoAlvo="12:00-12:00/km"
            // (pace 12 min/km), o IA-05 recalcula duracaoMin = round(0.9 × 12) = 11min — acima do
            // teto de 10min que validarTreinoIntervalado só checava ANTES dessa normalização rodar.
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
                    .isInstanceOf(LLMException.class);
        }
    }
}
