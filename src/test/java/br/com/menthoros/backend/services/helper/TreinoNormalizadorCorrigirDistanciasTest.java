package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link TreinoNormalizador#corrigirDistanciasEtapasTemporais} — deriva distância de etapas
 * time-based (refactor-iaservice-decomposition, seção 3).
 */
@DisplayName("TreinoNormalizador — corrigirDistanciasEtapasTemporais")
class TreinoNormalizadorCorrigirDistanciasTest {

    private TreinoNormalizador normalizador;

    @BeforeEach
    void setUp() {
        normalizador = new TreinoNormalizador(new PaceValidator());
    }

    // paceZ2 = 4.5 × 1.20 = 5.4 min/km → 10min → arredondar2(10/5.4) = 1.85
    @Test
    @DisplayName("AQUECIMENTO 10min com paceLimiar=4.5 → distanciaKm ≈ 1.85 (não 2.5)")
    void corrigeAquecimento() {
        var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 2.5, "120-136 bpm", 1, null);
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(etapa), new BigDecimal("4.5"));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).distanciaKm()).isCloseTo(1.85, within(0.01));
    }

    // paceZ2 = 5.0 × 1.20 = 6.0 min/km → 10min → arredondar2(10/6.0) = 1.67
    @Test
    @DisplayName("DESAQUECIMENTO 10min com paceLimiar=5.0 → distanciaKm ≈ 1.67")
    void corrigeDesaquecimento() {
        var etapa = new EtapaTreinoLlmDto(1, "DESAQUECIMENTO", "Caminhada", 10, 2.5, "120-136 bpm", 1, null);
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(etapa), new BigDecimal("5.0"));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).distanciaKm()).isCloseTo(1.67, within(0.01));
    }

    // paceZ1 = 4.5 × 1.35 = 6.075 min/km → 2min → arredondar2(2/6.075) = 0.33
    @Test
    @DisplayName("RECUPERACAO 2min com paceLimiar=4.5 → distanciaKm ≈ 0.33")
    void corrigeRecuperacao() {
        var etapa = new EtapaTreinoLlmDto(1, "RECUPERACAO", "Trote leve", 2, 1.0, "120-136 bpm", 1, null);
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(etapa), new BigDecimal("4.5"));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).distanciaKm()).isCloseTo(0.33, within(0.01));
    }

    // paceZ2 default = 7.0 min/km → 10min → arredondar2(10/7.0) = 1.43
    @Test
    @DisplayName("AQUECIMENTO 10min com paceLimiar=null → usa default 7.0 min/km → distanciaKm ≈ 1.43")
    void usaDefaultsQuandoPaceLimiarNulo() {
        var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 2.5, "120-136 bpm", 1, null);
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(etapa), null);
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).distanciaKm()).isCloseTo(1.43, within(0.01));
    }

    // add-etapa-pace-validator: o ritmoAlvo declarado é a verdade — 10 ÷ 6.25 = 1.60, não 10 ÷ (3.5 × 1.20)
    @Test
    @DisplayName("AQUECIMENTO com ritmoAlvo legível → distância pelo pace médio do ritmo, não pelo paceLimiar")
    void usaRitmoAlvoDaEtapaQuandoLegivel() {
        var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 2.38, "120-136 bpm", 1, "6:00-6:30/km");
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(etapa), new BigDecimal("3.5"));
        assertThat(resultado.get(0).distanciaKm()).isEqualTo(1.6);
    }

    // fix-etapas-sintetizadas-sessao-curta: Z2 = 3.83 × 1.20 = 4.6 min/km, mais rápido que a PRINCIPAL (6.75)
    @Test
    @DisplayName("AQUECIMENTO sem ritmo nunca fica mais rápido que a PRINCIPAL do mesmo treino")
    void aquecimentoSemRitmoNaoPassaAPrincipal() {
        var aquec = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "gerado", 10, null, null, 1, null);
        var principal = new EtapaTreinoLlmDto(2, "PRINCIPAL", "regenerativo", 30, 4.44, null, 1, "6:30-7:00/km");
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(aquec, principal), new BigDecimal("3.83"));
        assertThat(resultado.get(0).distanciaKm()).isEqualTo(1.48); // 10 ÷ 6.75
    }

    @Test
    @DisplayName("PRINCIPAL mais rápida que a zona fácil → aquecimento fica na zona")
    void principalRapidaMantemZona() {
        var aquec = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "gerado", 10, null, null, 1, null);
        var principal = new EtapaTreinoLlmDto(2, "PRINCIPAL", "tempo", 20, 4.4, null, 1, "4:30-4:40/km");
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(aquec, principal), new BigDecimal("4.5"));
        assertThat(resultado.get(0).distanciaKm()).isCloseTo(1.85, within(0.01)); // 10 ÷ 5.4
    }

    @Nested
    @DisplayName("encaixarPrincipalNoTotal")
    class EncaixarPrincipalNoTotal {

        @Test
        @DisplayName("PRINCIPAL encaixada mais curta que o aquecimento → bloco único com o total, duração pelo pace")
        void sessaoCurtaViraBlocoUnico() {
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", "REGENERATIVO", null, null, null, null, null,
                    "14:00", 2.0, "7:15-7:45/km", List.of(
                    new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento (gerado pelo sistema)", 10, 1.33, null, 1, null),
                    new EtapaTreinoLlmDto(2, "PRINCIPAL", "regenerativo", 14, 1.87, null, 1, "7:15-7:45/km"),
                    new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento (gerado pelo sistema)", 5, 0.4, null, 1, null)));

            var resultado = normalizador.encaixarPrincipalNoTotal(treino).orElseThrow();

            assertThat(resultado.etapas()).singleElement().satisfies(e -> {
                assertThat(e.tipoEtapa()).isEqualTo("PRINCIPAL");
                assertThat(e.distanciaKm()).isEqualTo(2.0);
                assertThat(e.duracaoMin()).isEqualTo(15); // 2.0 × 7.5
                assertThat(e.ordem()).isEqualTo(1);
            });
            assertThat(resultado.distanciaKm()).isEqualTo(2.0);
        }
    }

    @Test
    @DisplayName("ritmoAlvo ilegível → mantém o fallback por paceLimiar")
    void ritmoIlegivelUsaFallback() {
        var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 2.5, "120-136 bpm", 1, "leve");
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(etapa), new BigDecimal("4.5"));
        assertThat(resultado.get(0).distanciaKm()).isCloseTo(1.85, within(0.01));
    }

    @Test
    @DisplayName("INTERVALADO com distanciaKm=0.4 → não alterado (etapa de distância fixa)")
    void naoAlteraIntervalado() {
        var etapa = new EtapaTreinoLlmDto(1, "INTERVALADO", "400m Z5", 4, 0.4, "160-170 bpm", 1, null);
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(etapa), new BigDecimal("4.5"));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).distanciaKm()).isEqualTo(0.4);
    }

    @Test
    @DisplayName("TIRO com distanciaKm=0.2 → não alterado")
    void naoAlteraTiro() {
        var etapa = new EtapaTreinoLlmDto(1, "TIRO", "200m sprint", 1, 0.2, "165-175 bpm", 1, null);
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(etapa), new BigDecimal("4.5"));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).distanciaKm()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("lista vazia → retorna lista vazia sem exceção")
    void retornaListaVaziaQuandoListaVazia() {
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(), new BigDecimal("4.5"));
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("AQUECIMENTO com duracaoMin=null → distanciaKm original mantida")
    void naoAlteraEtapaSemDuracaoMin() {
        var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", null, 2.5, "120-136 bpm", 1, null);
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(etapa), new BigDecimal("4.5"));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).distanciaKm()).isEqualTo(2.5);
    }

    @Test
    @DisplayName("tipoEtapa=null → etapa retornada intacta, sem NullPointerException")
    void naoLancaNpeComTipoEtapaNulo() {
        var etapa = new EtapaTreinoLlmDto(1, null, "Etapa sem tipo", 10, 2.5, "120-136 bpm", 1, null);
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(etapa), new BigDecimal("4.5"));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).distanciaKm()).isEqualTo(2.5);
    }

    @Test
    @DisplayName("paceLimiar=0 → guarda pace <= 0 evita divisão por zero, distanciaKm original mantida")
    void naoGeraInfinityComPaceLimiarZero() {
        var etapa = new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 2.5, "120-136 bpm", 1, null);
        var resultado = normalizador.corrigirDistanciasEtapasTemporais(List.of(etapa), BigDecimal.ZERO);
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).distanciaKm()).isEqualTo(2.5);
    }

    @Nested
    @DisplayName("integração com reconciliarDistanciaComEtapas — CA1")
    class IntegracaoDistanciaTreinoIntervalado {

        @Test
        @DisplayName("10min aquec + 5×400m + 5×2min rec + 10min desaq → distanciaKm ∈ [5.5, 8.0]")
        void distanciaIntervaladoCorrigidaParaFaixaRealista() {
            // Cenário: LLM gerou distâncias erradas usando pace de tiro para etapas fáceis
            var etapas = List.of(
                    new EtapaTreinoLlmDto(1,  "AQUECIMENTO",    "Trote leve",       10, 2.5, "120-136 bpm", 1, null),
                    new EtapaTreinoLlmDto(2,  "INTERVALADO",    "400m Z5",           4, 0.4, "160-170 bpm", 1, null),
                    new EtapaTreinoLlmDto(3,  "RECUPERACAO",    "Trote leve",        2, 1.0, "120-136 bpm", 1, null),
                    new EtapaTreinoLlmDto(4,  "INTERVALADO",    "400m Z5",           4, 0.4, "160-170 bpm", 1, null),
                    new EtapaTreinoLlmDto(5,  "RECUPERACAO",    "Trote leve",        2, 1.0, "120-136 bpm", 1, null),
                    new EtapaTreinoLlmDto(6,  "INTERVALADO",    "400m Z5",           4, 0.4, "160-170 bpm", 1, null),
                    new EtapaTreinoLlmDto(7,  "RECUPERACAO",    "Trote leve",        2, 1.0, "120-136 bpm", 1, null),
                    new EtapaTreinoLlmDto(8,  "INTERVALADO",    "400m Z5",           4, 0.4, "160-170 bpm", 1, null),
                    new EtapaTreinoLlmDto(9,  "RECUPERACAO",    "Trote leve",        2, 1.0, "120-136 bpm", 1, null),
                    new EtapaTreinoLlmDto(10, "INTERVALADO",    "400m Z5",           4, 0.4, "160-170 bpm", 1, null),
                    new EtapaTreinoLlmDto(11, "RECUPERACAO",    "Trote leve",        2, 1.0, "120-136 bpm", 1, null),
                    new EtapaTreinoLlmDto(12, "DESAQUECIMENTO", "Caminhada",        10, 2.5, "120-136 bpm", 1, null)
            );
            var treino = new TreinoPlanejadoLlmDto(
                    "SEGUNDA", "INTERVALADO", null, null, null, null, null,
                    "50:00", 10.0, null, etapas);

            // Passo 0: corrigir com paceLimiar=4.5 (4:30/km)
            var etapasCorrigidas = normalizador.corrigirDistanciasEtapasTemporais(etapas, new BigDecimal("4.5"));
            var treinoCorrigido = new TreinoPlanejadoLlmDto(
                    treino.diaSemana(), treino.tipoTreino(), treino.fcAlvo(),
                    treino.tssPlanejado(), treino.intensidadePlanejada(),
                    treino.percepcaoEsforcoEsperada(), treino.justificativaIa(),
                    treino.duracaoMin(), treino.distanciaKm(), treino.ritmoAlvo(),
                    etapasCorrigidas);

            // AQUECIMENTO não deve mais ser 2.5km — deve ser ~1.85km
            assertThat(etapasCorrigidas.get(0).distanciaKm())
                    .isLessThan(2.0)
                    .isGreaterThan(1.0);

            // Passo 3: reconciliar total com soma das etapas corrigidas
            var treinoFinal = normalizador.reconciliarDistanciaComEtapas(treinoCorrigido);

            // CA1: total deve estar na faixa realista (não 10km)
            assertThat(treinoFinal.distanciaKm())
                    .isGreaterThanOrEqualTo(5.5)
                    .isLessThanOrEqualTo(8.0);
        }
    }

    /**
     * fix-normalizador-etapas-incompletas: uma etapa com distanciaKm 0/null é "desconhecido", não
     * "zero" — a soma vira um piso e não pode substituir a distância declarada pela LLM.
     */
    @Nested
    @DisplayName("reconciliarDistanciaComEtapas — etapas sem distância")
    class ReconciliarComEtapasIncompletas {

        @Test
        @DisplayName("CA1: FARTLEK 8km com PRINCIPAL em 0km → mantém 8km (soma 2.64 é um piso)")
        void mantemDistanciaDaLlmQuandoAlgumaEtapaNaoTemDistancia() {
            var treino = fartlek(8.0,
                    etapa("AQUECIMENTO", 10, 1.32),
                    etapa("PRINCIPAL", 30, 0.0),
                    etapa("DESAQUECIMENTO", 10, 1.32));

            var resultado = normalizador.reconciliarDistanciaComEtapas(treino);

            assertThat(resultado.distanciaKm()).isEqualTo(8.0);
        }

        @Test
        @DisplayName("etapa com distanciaKm null conta como desconhecida, mesmo efeito de 0")
        void etapaNulaTambemBloqueiaReconciliacao() {
            var treino = fartlek(8.0,
                    etapa("AQUECIMENTO", 10, 1.32),
                    etapa("PRINCIPAL", 30, null),
                    etapa("DESAQUECIMENTO", 10, 1.32));

            var resultado = normalizador.reconciliarDistanciaComEtapas(treino);

            assertThat(resultado.distanciaKm()).isEqualTo(8.0);
        }

        @Test
        @DisplayName("CA2: todas as etapas com distância e desvio 11% → reconcilia para a soma (6.64)")
        void reconciliaQuandoTodasAsEtapasTemDistancia() {
            var treino = fartlek(6.0,
                    etapa("AQUECIMENTO", 10, 1.32),
                    etapa("PRINCIPAL", 30, 4.0),
                    etapa("DESAQUECIMENTO", 10, 1.32));

            var resultado = normalizador.reconciliarDistanciaComEtapas(treino);

            assertThat(resultado.distanciaKm()).isCloseTo(6.64, within(0.001));
        }

        @Test
        @DisplayName("CA3: treino sem distância usa a soma das etapas mesmo com etapa em 0")
        void treinoSemDistanciaUsaSomaMesmoIncompleta() {
            var treino = fartlek(null,
                    etapa("AQUECIMENTO", 10, 1.32),
                    etapa("PRINCIPAL", 30, 0.0),
                    etapa("DESAQUECIMENTO", 10, 1.32));

            var resultado = normalizador.reconciliarDistanciaComEtapas(treino);

            assertThat(resultado.distanciaKm()).isCloseTo(2.64, within(0.001));
        }

        private EtapaTreinoLlmDto etapa(String tipo, int duracaoMin, Double distanciaKm) {
            return new EtapaTreinoLlmDto(1, tipo, tipo.toLowerCase(), duracaoMin, distanciaKm, null, 1, null);
        }

        private TreinoPlanejadoLlmDto fartlek(Double distanciaKm, EtapaTreinoLlmDto... etapas) {
            return new TreinoPlanejadoLlmDto("QUINTA", "FARTLEK", null, null, null, null, null,
                    "50:00", distanciaKm, "6:20-6:45/km", List.of(etapas));
        }
    }
}
