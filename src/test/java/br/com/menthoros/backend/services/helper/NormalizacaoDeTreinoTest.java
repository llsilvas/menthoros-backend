package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.exception.LLMException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NormalizacaoDeTreino#normalizar} chamado direto, sem {@code PlanoLlmValidator} nem mocks
 * de fontes de dados (pipeline-normalizacao-treino, task 2.7). Os cenários de rejeição vieram de
 * {@code PlanoLlmValidatorTest#ValidacaoPosNormalizacaoIA05} (F2) — a ordem certa produz o
 * resultado certo; o golden da ordem em si é {@code FamiliaTreinoTest}.
 */
@DisplayName("NormalizacaoDeTreino — normalizar")
class NormalizacaoDeTreinoTest {

    private NormalizacaoDeTreino normalizacao;
    private SimpleMeterRegistry registry;
    private ContextoNormalizacao ctx;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        normalizacao = new NormalizacaoDeTreino(
                new TreinoNormalizador(new PaceValidator()),
                new EtapaFcValidator(),
                new PlanoEstruturaReparador(new SimpleMeterRegistry()),
                new PaceValidator(),
                registry);
        Atleta atleta = Atleta.builder()
                .id(UUID.randomUUID())
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(BigDecimal.valueOf(5.0))
                .build();
        ctx = new ContextoNormalizacao(atleta, atleta.getId(), null, Map.of(), Map.of());
    }

    @Nested
    @DisplayName("rejeições — a ordem da receita é o que as produz")
    class Rejeicoes {

        @Test
        @DisplayName("4 etapas não é mascarado pelo padding: gate-contagem vem ANTES de normalizar-intervalado")
        void paddingDe4Etapas() {
            var treino = intervalado(8.0, aquec(), tiro(4, null), rec(), desaq());

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("mínimo 6");
        }

        @Test
        @DisplayName("tiro que passa de 10min após crescer de distância: a 2ª gate-duracao-tiros (IA-05) pega")
        void tiroPassaDe10minAposCrescimento() {
            // soma 7.27km (desaq clampado pra 1.5) contra alvo 7.84 → gap +0.57 distribuído em 4 tiros
            // (0.8→~0.94km); pace 12 min/km → duracaoMin recalculado 11 > 10
            var tiro = tiro(4, "12:00-12:00/km");
            var treino = intervalado(7.84, aquec(), tiro, rec(), tiro, rec(), tiro, rec(), tiro, desaq());

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("duração incoerente");
        }

        @Test
        @DisplayName("tiro com duracaoMin == null é rejeitado pela 1ª gate-duracao-tiros, antes de qualquer normalização")
        void tiroComDuracaoNull() {
            var tiro = tiro(null, null);
            var treino = intervalado(7.44, aquec(), tiro, rec(), tiro, rec(), tiro, rec(), tiro, desaq());

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("duração incoerente");
        }

        @Test
        @DisplayName("INTERVALADO com série por tempo '4x (3min Z4 + 2min Z1)': recuperações expandidas recebem pace de trote, não 0 km")
        void intervaladoComSeriePorTempoNaoDeixaRecuperacaoSemDistancia() {
            // o expansor é compartilhado com FARTLEK e cria a recuperação com 0.0; nesta receita
            // corrigir-temporais rodava só antes de expandir e a etapa ficava sem distância
            var treino = intervalado(8.0, aquec(),
                    new EtapaTreinoLlmDto(2, "INTERVALADO", "4x (3min Z4 + 2min Z1)", 20, 0.0, null, 1, "5:00-5:15/km"),
                    desaq());

            var resultado = normalizacao.normalizar(treino, ctx);

            // Z1 = limiar 5,0 × 1,35 = 6,75 min/km → 2min / 6,75 = 0,3 km
            assertThat(resultado.etapas())
                    .filteredOn(e -> "RECUPERACAO".equals(e.tipoEtapa()))
                    .isNotEmpty()
                    .allSatisfy(e -> assertThat(e.distanciaKm()).isGreaterThan(0.0));
        }

        @Test
        @DisplayName("RECUPERACAO antes do 1º tiro: gate-sequencia rejeita mesmo com contagem, extremos e balanceamento ok")
        void recuperacaoAntesDoPrimeiroTiro() {
            var treino = intervalado(6.0, aquec(), rec(), tiro(4, null), tiro(4, null), rec(), desaq());

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("recuperações sem tiro");
        }
    }

    /**
     * Família TRES_ETAPAS pela interface: {@code reparar-3-etapas} roda ANTES de
     * {@code validar-por-tipo}, então o que chega ao gate é só o que o reparo não conserta —
     * exatamente 1 PRINCIPAL é reparável (falta/ordem de aquec/desaq); 0 ou 2+ não. Os 6 casos
     * vieram de {@code PlanoLlmValidatorTest#Estrutura3Etapas} (chamavam o gate direto) — task 3.2.
     */
    @Nested
    @DisplayName("família TRES_ETAPAS — reparar-3-etapas antes de validar-por-tipo")
    class TresEtapas {

        @Test
        @DisplayName("≠ 3 etapas que o reparo não conserta (2 PRINCIPAL) → LLMException")
        void numeroEtapasErradoNaoReparavel() {
            var treino = tresEtapas("REGENERATIVO",
                    etapa3("AQUECIMENTO"), etapa3("PRINCIPAL"), etapa3("PRINCIPAL"), etapa3("DESAQUECIMENTO"));

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("esperado 3");
        }

        @Test
        @DisplayName("3 etapas na ordem canônica → passa intacto")
        void ordemCanonica() {
            var treino = tresEtapas("REGENERATIVO", etapa3("AQUECIMENTO"), etapa3("PRINCIPAL"), etapa3("DESAQUECIMENTO"));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(tipos(resultado)).containsExactly("AQUECIMENTO", "PRINCIPAL", "DESAQUECIMENTO");
        }

        @Test
        @DisplayName("fora de ordem com os 3 tipos presentes: o reparo reordena antes do gate, não rejeita")
        void ordemTrocadaEhReparada() {
            var treino = tresEtapas("REGENERATIVO", etapa3("PRINCIPAL"), etapa3("AQUECIMENTO"), etapa3("DESAQUECIMENTO"));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(tipos(resultado)).containsExactly("AQUECIMENTO", "PRINCIPAL", "DESAQUECIMENTO");
        }

        @Test
        @DisplayName("REGENERATIVO (validarOrdem=true) sem DESAQUECIMENTO no fim e não reparável (2 PRINCIPAL) → LLMException")
        void regenerativoExigePosicaoDeAquecDesaq() {
            var treino = tresEtapas("REGENERATIVO", etapa3("AQUECIMENTO"), etapa3("PRINCIPAL"), etapa3("PRINCIPAL"));

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO");
        }

        @Test
        @DisplayName("LONGO (validarOrdem=false) ignora só a posição de aquec/desaq — a mesma entrada passa")
        void longoIgnoraPosicaoDeAquecDesaq() {
            var treino = tresEtapas("LONGO", etapa3("AQUECIMENTO"), etapa3("PRINCIPAL"), etapa3("PRINCIPAL"));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(tipos(resultado)).containsExactly("AQUECIMENTO", "PRINCIPAL", "PRINCIPAL");
        }

        @Test
        @DisplayName("IA-04: etapa central que não é PRINCIPAL → LLMException (REGENERATIVO, validarOrdem=true)")
        void etapaCentralNaoPrincipal_regenerativo() {
            var treino = tresEtapas("REGENERATIVO", etapa3("AQUECIMENTO"), etapa3("RECUPERACAO"), etapa3("DESAQUECIMENTO"));

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("etapa central deve ser PRINCIPAL");
        }

        @Test
        @DisplayName("IA-04 (achado do Codex): etapa central que não é PRINCIPAL → LLMException também em LONGO (validarOrdem=false)")
        void etapaCentralNaoPrincipal_longo() {
            var treino = tresEtapas("LONGO", etapa3("AQUECIMENTO"), etapa3("RECUPERACAO"), etapa3("DESAQUECIMENTO"));

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("etapa central deve ser PRINCIPAL");
        }

        private List<String> tipos(TreinoPlanejadoLlmDto treino) {
            return treino.etapas().stream().map(EtapaTreinoLlmDto::tipoEtapa).toList();
        }
    }

    /**
     * fix-etapas-continuos-pace: a receita TRES_ETAPAS não tocava a distância das etapas. Caso real
     * 22/09 07:24 (limiar 6:20 → Z2 = 7,60 min/km): REGENERATIVO 6,0 km / 45:00 com PRINCIPAL de
     * 5,5 km em 30min (5:27/km para um ritmo de 7:28-7:55) e aquecimento de 10min com 0 km.
     */
    @Nested
    @DisplayName("família TRES_ETAPAS — distância das etapas pelo pace")
    class TresEtapasDistanciaPorPace {

        private ContextoNormalizacao ctxLeandro;

        @BeforeEach
        void limiarDoLeandro() {
            Atleta atleta = Atleta.builder()
                    .id(UUID.randomUUID())
                    .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                    .paceLimiar(BigDecimal.valueOf(6.33))
                    .build();
            ctxLeandro = new ContextoNormalizacao(atleta, atleta.getId(), null, Map.of(), Map.of());
        }

        @Test
        @DisplayName("CA1: caso real — PRINCIPAL 3,90 km, aquec 1,32, desaq 0,66; total = soma 5,88 km, 45:00")
        void casoRealRegenerativo() {
            var treino = continuo("6.0", "45:00", "7:28-7:55/km",
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::distanciaKm)
                    .containsExactly(1.3, 3.9, 0.65);  // fix-etapas-sintetizadas-sessao-curta: aquec/desaq sem ritmo no pace da PRINCIPAL (7,69), mais lenta que Z2
            assertThat(resultado.distanciaKm()).isEqualTo(5.85);
            assertThat(resultado.duracaoMin()).isEqualTo("45:00");
        }

        @Test
        @DisplayName("CA4: aquecimento sintetizado pelo reparo (distância null) recebe duração ÷ pace Z2")
        void aquecimentoSintetizadoRecebeDistancia() {
            var treino = continuo("6.0", "45:00", "7:28-7:55/km",
                    etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            // reparo acrescenta AQUECIMENTO de 10min → 10 / 7,60 = 1,32
            assertThat(resultado.etapas().getFirst().tipoEtapa()).isEqualTo("AQUECIMENTO");
            assertThat(resultado.etapas().getFirst().distanciaKm()).isEqualTo(1.3);  // fix-etapas-sintetizadas-sessao-curta: aquec/desaq sem ritmo no pace da PRINCIPAL (7,69), mais lenta que Z2
        }

        @Test
        @DisplayName("CA5: total vira a soma, e a duração é a soma das etapas (mais perto do triângulo que os 60 da LLM)")
        void somaForaDaToleranciaSubstituiOTotal() {
            // LLM diz 10 km / 60:00; etapas pelo pace somam 1,32 + 3,90 + 0,66 = 5,88 km → desvio 41%.
            // Triângulo: 5,88 × 7,69 ≈ 45,2min — a soma 45 fica mais perto que os 60 da LLM
            var treino = continuo("10.0", "60:00", "7:28-7:55/km",
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, 8.0, "7:28-7:55/km"),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            assertThat(resultado.distanciaKm()).isCloseTo(5.85, org.assertj.core.data.Offset.offset(0.001));  // fix-etapas-sintetizadas-sessao-curta: aquec/desaq sem ritmo no pace da PRINCIPAL (7,69), mais lenta que Z2
            assertThat(resultado.duracaoMin()).isEqualTo("45:00");
        }

        @Test
        @DisplayName("CA1: aquecimento rápido demais vai para a Z1 do atleta e a distância sai do ritmo corrigido")
        void aquecimentoForaDaZonaCorrigidoAntesDaDistancia() {
            // limiar 6:20 → Z1 de Friel ≈ 8:10–8:52; o aquecimento veio a 6:00–6:30
            var comZonas = new ContextoNormalizacao(ctxLeandro.atleta(), ctxLeandro.atletaId(), null, Map.of(), Map.of(),
                    new ZonaTreinoService().zonasPace(ctxLeandro.atleta()));
            var treino = continuo("6.0", "45:00", "8:00-8:20/km",
                    etapa("AQUECIMENTO", 10, 0.0, "6:00-6:30/km"),
                    etapa("PRINCIPAL", 30, 3.7, "8:00-8:20/km"),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            var resultado = normalizacao.normalizar(treino, comZonas);

            var aquecimento = resultado.etapas().get(0);
            assertThat(aquecimento.ritmoAlvo()).isNotEqualTo("6:00-6:30/km");
            assertThat(new PaceValidator().calcularPaceMedia(aquecimento.ritmoAlvo()).orElseThrow())
                    .isBetween(8.10, 8.90);
            assertThat(aquecimento.distanciaKm()).isCloseTo(1.17, org.assertj.core.data.Offset.offset(0.1)); // 10 ÷ ~8:31
        }

        @Test
        @DisplayName("zona não deixa o aquecimento mais rápido que a PRINCIPAL lenta por cautela")
        void aquecimentoNaoPassaAPrincipal() {
            // Z1 ≈ 8:10–8:52 e Z2–Z3 ≈ 6:39–8:10; principal a 8:50–9:10 (mais lenta que a zona) fica intacta
            var comZonas = new ContextoNormalizacao(ctxLeandro.atleta(), ctxLeandro.atletaId(), null, Map.of(), Map.of(),
                    new ZonaTreinoService().zonasPace(ctxLeandro.atleta()));
            var treino = continuo("5.0", "45:00", "8:50-9:10/km",
                    etapa("AQUECIMENTO", 10, 0.0, "9:00-9:20/km"),
                    etapa("PRINCIPAL", 30, 3.3, "8:50-9:10/km"),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            var resultado = normalizacao.normalizar(treino, comZonas);

            assertThat(resultado.etapas().get(1).ritmoAlvo()).isEqualTo("8:50-9:10/km");
            assertThat(resultado.etapas().get(0).ritmoAlvo()).isEqualTo("9:00-9:20/km");
        }

        @Test
        @DisplayName("aquecimento recortado no teto do histórico não é desfeito pela regra da principal")
        void aquecimentoDesaceleradoPeloTetoFica() {
            // Z1 ≈ 8:10–8:52; teto REGENERATIVO 8:20 → aquecimento 8:12–8:40 vira 8:20–8:40, mesmo com a principal mais lenta
            var comZonas = new ContextoNormalizacao(ctxLeandro.atleta(), ctxLeandro.atletaId(), null,
                    Map.of(br.com.menthoros.backend.enums.TipoTreino.REGENERATIVO, new BigDecimal("8.3333")), Map.of(),
                    new ZonaTreinoService().zonasPace(ctxLeandro.atleta()));
            var treino = new TreinoPlanejadoLlmDto("TERCA", "REGENERATIVO", "120-135 bpm", 40, 0.5, 3,
                    "Regenerativo", "45:00", 5.0, "8:30-8:50/km",
                    List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote", 10, 1.2, null, 1, "8:12-8:40/km"),
                            new EtapaTreinoLlmDto(2, "PRINCIPAL", "Leve", 30, 3.4, null, 1, "8:30-8:50/km"),
                            new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Trote", 5, 0.5, null, 1, null)));

            var resultado = normalizacao.normalizar(treino, comZonas);

            assertThat(resultado.etapas().get(0).ritmoAlvo()).isEqualTo("8:20-8:40/km");
        }

        @Test
        @DisplayName("ritmo absurdo da PRINCIPAL é descartado e a distância sai do ritmo do treino (validar-ritmo, S4)")
        void ritmoAbsurdoDescartado() {
            var treino = continuo("6.0", "45:00", "6:00-6:30/km",
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, 5.5, "70:00-60:00/km"),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            assertThat(resultado.etapas().get(1).ritmoAlvo()).isNull();
            assertThat(resultado.etapas().get(1).distanciaKm()).isEqualTo(4.8); // 30 ÷ 6,25
        }

        @Test
        @DisplayName("PRINCIPAL sem ritmo usa o ritmo do treino: distância pelo pace e total = soma das etapas")
        void principalSemRitmoUsaRitmoDoTreino() {
            // fix-total-treino-soma-etapas (CA1): antes a PRINCIPAL ficava com os 5,5 km da LLM e o total de 6,0
            // não batia com as etapas (7,48); agora 30 ÷ 7,69 = 3,9 km e o total é a soma
            var treino = continuo("6.0", "45:00", "7:28-7:55/km",
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, 5.5, null),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            assertThat(resultado.etapas().get(1).distanciaKm()).isEqualTo(3.9);
            assertThat(resultado.distanciaKm()).isEqualTo(
                    resultado.etapas().stream().mapToDouble(EtapaTreinoLlmDto::distanciaKm).sum(),
                    org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("etapa sintetizada: o total prescrito fica e a PRINCIPAL encaixa — total == soma, duração pelo pace")
        void sintetizadaEncaixaPrincipalNoTotal() {
            // REGENERATIVO 7 km só com PRINCIPAL (fix-total-treino-soma-etapas, CA2)
            var treino = continuo("7.0", "50:00", "6:30-7:00/km", etapa("PRINCIPAL", 50, 7.0, "6:30-7:00/km"));

            var resultado = normalizacao.normalizar(treino, ctx);

            double soma = resultado.etapas().stream().mapToDouble(EtapaTreinoLlmDto::distanciaKm).sum();
            assertThat(resultado.distanciaKm()).isEqualTo(7.0);
            assertThat(soma).isCloseTo(7.0, org.assertj.core.data.Offset.offset(0.001));
            var principal = resultado.etapas().get(1);
            assertThat(principal.duracaoMin()).isEqualTo((int) Math.round(principal.distanciaKm() * 6.75));
            assertThat(contadorDivergencia("REGENERATIVO", "etapa-sintetizada")).isZero();
        }

        @Test
        @DisplayName("CA8: treino sem distância e PRINCIPAL sem ritmo → fallback Z2 de garantir-distancia-continuo continua")
        void semDistanciaNemRitmoUsaFallbackZ2() {
            // sem a guarda, reconciliar adotaria a soma parcial (só aquec + desaq) e a PRINCIPAL
            // ficaria sem distância para sempre (garantir só age com o treino sem distância)
            var treino = continuo(null, "45:00", null,
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, null, null),
                    etapa("DESAQUECIMENTO", 5, 0.0, null));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            // PRINCIPAL: 30 / 7,60 = 3,95
            assertThat(resultado.etapas().get(1).distanciaKm()).isEqualTo(3.95);
            assertThat(resultado.distanciaKm()).isCloseTo(5.93, org.assertj.core.data.Offset.offset(0.001));
        }

        @ParameterizedTest(name = "{0}: {1} km / {2} (ritmo {3}) → PRINCIPAL {4} km, total {5} km")
        @CsvSource({
                // os três contínuos persistidos em 22/09 07:24 (limiar 6:20), LLM → esperado
                "REGENERATIVO, 6.0, 45:00, 7:28-7:55/km, 30, 5.5, 5, 0.5, 3.9,  5.85",
                "REGENERATIVO, 7.0, 48:00, 6:45-7:06/km, 35, 6.5, 3, 0.5, 5.05, 6.76",
                "LONGO,        9.0, 60:00, 6:45-7:06/km, 45, 8.5, 5, 0.5, 6.5,  8.48",
                // os três de 22/09 08:00 — já com a correção, os totais da LLM ficavam 7 e 8% acima da soma
                "REGENERATIVO, 6.0, 45:00, 7:28-7:55/km, 30, 4.0, 5, 1.0, 3.9,  5.85",
                "REGENERATIVO, 7.0, 48:00, 6:55-7:28/km, 30, 5.0, 8, 1.0, 4.17, 6.54",
                "LONGO,        9.0, 60:00, 6:55-7:28/km, 45, 6.5, 5, 1.0, 6.26, 8.24"
        })
        @DisplayName("casos reais de 22/09: PRINCIPAL volta ao ritmo e o total é a soma das etapas (tolerância zero)")
        void casosReaisDe2209(String tipo, double km, String duracao, String ritmo, int durPrincipal,
                              double kmPrincipal, int durDesaq, double kmDesaq,
                              double principalEsperada, double totalEsperado) {
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", tipo, null, null, null, null, null, duracao, km, ritmo,
                    List.of(etapa("AQUECIMENTO", 10, 0.0, null),
                            etapa("PRINCIPAL", durPrincipal, kmPrincipal, ritmo),
                            etapa("DESAQUECIMENTO", durDesaq, kmDesaq, null)));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            assertThat(resultado.etapas().get(1).distanciaKm()).isEqualTo(principalEsperada);
            assertThat(resultado.etapas().get(0).distanciaKm()).isEqualTo(kmPrincipalRitmoLento(ritmo) ? 1.3 : 1.32); // 10 ÷ max(Z2 7,60, PRINCIPAL)
            assertThat(resultado.distanciaKm()).isEqualTo(totalEsperado);
            assertThat(resultado.duracaoMin()).isEqualTo(duracao);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"REGENERATIVO", "CONTINUO", "LONGO", "TEMPO_RUN"})
        @DisplayName("CA2 ponta a ponta, todo tipo da família: pace da PRINCIPAL dentro do ritmoAlvo e total = soma quando desvia > 10%")
        void todoTipoDaFamilia(String tipo) {
            // limiar 5:00 → Z2 6:00; PRINCIPAL 40min a 5:00-5:15 (5,125) = 7,80; aquec 1,67; desaq 0,83
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", tipo, null, null, null, null, null,
                    "55:00", 9.0, "5:00-5:15/km", List.of(
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 40, 9.0, "5:00-5:15/km"),
                    etapa("DESAQUECIMENTO", 5, 0.0, null)));

            var resultado = normalizacao.normalizar(treino, ctx);

            var principal = resultado.etapas().get(1);
            assertThat(principal.duracaoMin() / principal.distanciaKm()).isBetween(5.0 - 0.01, 5.25 + 0.01);
            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::distanciaKm).containsExactly(1.67, 7.8, 0.83);
            assertThat(resultado.distanciaKm()).isCloseTo(10.3, org.assertj.core.data.Offset.offset(0.001));
            assertThat(resultado.duracaoMin()).isEqualTo("55:00");
        }

        @ParameterizedTest(name = "LLM {0} km contra soma 10,0 → {1} km")
        @CsvSource({
                "10.0,  10.0",  // igual
                "10.01, 10.0",  // 0,1% — antes ficava (tolerância de 10%), agora a soma vale
                "9.99,  10.0",  // 0,1% por baixo
                "11.0,  10.0",  // 9,1% — antes ficava
                "9.1,   10.0",  // 9,9% por baixo — antes ficava
                "11.2,  10.0",  // 10,7%
                "20.0,  10.0",  // muito acima
                "5.0,   10.0"   // muito abaixo
        })
        @DisplayName("CA5 BVA (tolerância zero): com toda etapa pelo pace, o total é sempre a soma — qualquer desvio")
        void limiteDaReconciliacao(double totalLlm, double totalEsperado) {
            // limiar 5:00 → Z2 6:00; ritmo fixo 6:00 → aquec 12min = 2,0 · PRINCIPAL 42min = 7,0 · desaq 6min = 1,0
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", "CONTINUO", null, null, null, null, null,
                    "60:00", totalLlm, "6:00-6:00/km", List.of(
                    etapa("AQUECIMENTO", 12, 0.0, null),
                    etapa("PRINCIPAL", 42, totalLlm, "6:00-6:00/km"),
                    etapa("DESAQUECIMENTO", 6, 0.0, null)));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::distanciaKm).containsExactly(2.0, 7.0, 1.0);
            assertThat(resultado.distanciaKm()).isEqualTo(totalEsperado);
            assertThat(resultado.duracaoMin()).isEqualTo("60:00");
        }

        @Test
        @DisplayName("reparo que só reordena (nada sintetizado) não bloqueia a reconciliação")
        void reordenadoReconcilia() {
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", "CONTINUO", null, null, null, null, null,
                    "60:00", 15.0, "6:00-6:00/km", List.of(
                    etapa("PRINCIPAL", 42, 15.0, "6:00-6:00/km"),
                    etapa("AQUECIMENTO", 12, 0.0, null),
                    etapa("DESAQUECIMENTO", 6, 0.0, null)));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::tipoEtapa)
                    .containsExactly("AQUECIMENTO", "PRINCIPAL", "DESAQUECIMENTO");
            assertThat(resultado.distanciaKm()).isEqualTo(10.0);
        }

        @ParameterizedTest(name = "sintetizado: {0}")
        @ValueSource(strings = {"AQUECIMENTO", "DESAQUECIMENTO"})
        @DisplayName("qualquer etapa sintetizada pelo reparo bloqueia a reconciliação — o total prescrito fica")
        void sintetizadoBloqueiaReconciliacao(String faltante) {
            var etapas = new java.util.ArrayList<EtapaTreinoLlmDto>();
            if (!"AQUECIMENTO".equals(faltante)) etapas.add(etapa("AQUECIMENTO", 12, 0.0, null));
            etapas.add(etapa("PRINCIPAL", 42, 15.0, "6:00-6:00/km"));
            if (!"DESAQUECIMENTO".equals(faltante)) etapas.add(etapa("DESAQUECIMENTO", 6, 0.0, null));
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", "CONTINUO", null, null, null, null, null,
                    "60:00", 15.0, "6:00-6:00/km", etapas);

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.etapas()).hasSize(3);
            assertThat(resultado.etapas()).anyMatch(PlanoEstruturaReparador::foiSintetizada);
            assertThat(resultado.distanciaKm()).isEqualTo(15.0);
            // a sintetizada também ganha distância pelo pace Z2 — só não entra no total
            assertThat(resultado.etapas()).allSatisfy(e -> assertThat(e.distanciaKm()).isPositive());
        }

        @Test
        @DisplayName("regenerativo só com PRINCIPAL: aquec/desaq sintetizados, o total de 4 km fica e a PRINCIPAL encaixa nele")
        void soPrincipalPreservaPrescricao() {
            // a decisão de produto de 22/09: o que o sistema inventa não infla o volume que o treinador aprova
            var treino = continuo("4.0", "30:00", "6:30-7:00/km", etapa("PRINCIPAL", 30, 4.0, "6:30-7:00/km"));

            var resultado = normalizacao.normalizar(treino, ctx);

            // fix-total-treino-soma-etapas + fix-etapas-sintetizadas-sessao-curta: aquec/desaq no pace da PRINCIPAL
            // (6,75) → 1,48 e 0,74; PRINCIPAL = 4,0 − 1,48 − 0,74 = 1,78 km, 1,78 × 6,75 = 12 min
            assertThat(resultado.distanciaKm()).isEqualTo(4.0);
            assertThat(resultado.duracaoMin()).isEqualTo("27:00");
            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::distanciaKm).containsExactly(1.48, 1.78, 0.74);
            assertThat(resultado.etapas().get(1).duracaoMin()).isEqualTo(12);
        }

        @Test
        @DisplayName("total 0.0 conta como ausente: garantir-distancia-continuo assume com a soma das etapas pelo pace")
        void totalZeroViraSoma() {
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", "CONTINUO", null, null, null, null, null,
                    "60:00", 0.0, "6:00-6:00/km", List.of(
                    etapa("AQUECIMENTO", 12, 0.0, null),
                    etapa("PRINCIPAL", 42, 0.0, "6:00-6:00/km"),
                    etapa("DESAQUECIMENTO", 6, 0.0, null)));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.distanciaKm()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("total null com PRINCIPAL pelo pace: garantir-distancia-continuo assume com a soma")
        void totalNullViraSoma() {
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", "CONTINUO", null, null, null, null, null,
                    "60:00", null, "6:00-6:00/km", List.of(
                    etapa("AQUECIMENTO", 12, null, null),
                    etapa("PRINCIPAL", 42, null, "6:00-6:00/km"),
                    etapa("DESAQUECIMENTO", 6, null, null)));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::distanciaKm).containsExactly(2.0, 7.0, 1.0);
            assertThat(resultado.distanciaKm()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("durações das etapas nunca mudam — só a distância é derivada")
        void duracoesDasEtapasIntocadas() {
            var treino = continuo("6.0", "45:00", "7:28-7:55/km",
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::duracaoMin).containsExactly(10, 30, 5);
        }

        @Test
        @DisplayName("CA6 ponta a ponta: LONGO com duas PRINCIPAL e aquecimento — cada uma pelo próprio ritmo")
        void longoComDuasPrincipais() {
            var treino = new TreinoPlanejadoLlmDto("SABADO", "LONGO", null, null, null, null, null,
                    "60:00", 9.0, "6:45-7:06/km", List.of(
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 40, 8.0, "6:45-7:06/km"),
                    etapa("PRINCIPAL", 10, 1.0, "6:00-6:10/km")));

            var resultado = normalizacao.normalizar(treino, ctx);

            // aquec 10 ÷ 6,0 = 1,67 · 40 ÷ 6,925 = 5,78 · 10 ÷ 6,083 = 1,64 → total = soma 9,09
            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::distanciaKm).containsExactly(1.44, 5.78, 1.64);  // aquec no pace da 1ª PRINCIPAL (6,93)
            assertThat(resultado.distanciaKm()).isEqualTo(8.86);
        }

        @Test
        @DisplayName("PRINCIPAL com ritmo diferente do treino: vale o da etapa")
        void ritmoDaEtapaPrevalece() {
            var treino = continuo("6.0", "45:00", "7:28-7:55/km",
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, 5.5, "7:00-7:00/km"),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            assertThat(resultado.etapas().get(1).distanciaKm()).isEqualTo(4.29); // 30 ÷ 7,0
        }

        @Test
        @DisplayName("família PADRAO (FACIL) não ganha os passos novos: PRINCIPAL fica com a distância da LLM")
        void padraoNaoAfetado() {
            var treino = new TreinoPlanejadoLlmDto("TERCA", "FACIL", null, null, null, null, null,
                    "40:00", 6.5, "6:00-6:30/km", List.of(
                    etapa("AQUECIMENTO", 5, 0.0, null),
                    etapa("PRINCIPAL", 30, 6.5, "6:00-6:30/km"),
                    etapa("DESAQUECIMENTO", 5, 0.0, null)));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::distanciaKm).containsExactly(0.0, 6.5, 0.0);
        }

        @Test
        @DisplayName("normalizar é idempotente na família: aplicar duas vezes dá o mesmo que uma")
        void idempotente() {
            var treino = continuo("6.0", "45:00", "7:28-7:55/km",
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            var uma = normalizacao.normalizar(treino, ctxLeandro);
            var duas = normalizacao.normalizar(uma, ctxLeandro);

            assertThat(duas).isEqualTo(uma);
        }

        @Test
        @DisplayName("tolerância zero: o total adotado é a soma arredondada a 2 casas, sem resíduo de ponto flutuante")
        void totalArredondado() {
            // 1,32 + 3,90 + 0,66 em double dá 5,880000000000001
            var treino = continuo("6.0", "45:00", "7:28-7:55/km",
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            assertThat(resultado.distanciaKm()).isEqualTo(5.85);  // fix-etapas-sintetizadas-sessao-curta: aquec/desaq sem ritmo no pace da PRINCIPAL (7,69), mais lenta que Z2
        }

        @ParameterizedTest(name = "{0} min a {1} com LLM {2} km")
        @CsvSource({
                "20, 5:00-5:15/km, 3.0",
                "30, 7:28-7:55/km, 6.0",
                "45, 6:45-7:06/km, 12.0",
                "60, 5:30-6:00/km, 15.0",
                "90, 6:00-6:30/km, 14.0",
                "120, 6:55-7:28/km, 25.0"
        })
        @DisplayName("propriedade: com toda etapa pelo pace e nada sintetizado, total == soma das etapas")
        void totalIgualASomaDasEtapas(int durPrincipal, String ritmo, double kmLlm) {
            var treino = new TreinoPlanejadoLlmDto("SABADO", "LONGO", null, null, null, null, null,
                    String.format("%02d:00", durPrincipal + 15), kmLlm, ritmo, List.of(
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", durPrincipal, kmLlm, ritmo),
                    etapa("DESAQUECIMENTO", 5, 0.0, null)));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            double soma = resultado.etapas().stream().mapToDouble(EtapaTreinoLlmDto::distanciaKm).sum();
            assertThat(resultado.distanciaKm()).isCloseTo(soma, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("telemetria: TEMPO_RUN com PRINCIPAL sem ritmo e soma > 10% do total mantido → WARN + contador motivo=principal-sem-ritmo (CA3)")
        void telemetriaPrincipalSemRitmo() {
            // sem ritmo em lugar nenhum: nem na PRINCIPAL nem no treino (o do treino viraria fallback). Só TEMPO_RUN:
            // os tipos leves derivam a PRINCIPAL pelo pace Z2 (fix-distancia-treino-soma-etapas)
            var treino = tempoRun("6.0", "45:00",
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, 5.5, null),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            assertThat(resultado.distanciaKm()).isEqualTo(6.0);
            assertThat(contadorDivergencia("TEMPO_RUN", "principal-sem-ritmo")).isEqualTo(1.0);
            assertThat(contadorDivergencia("TEMPO_RUN", "etapa-sintetizada")).isZero();
        }

        @Test
        @DisplayName("telemetria: etapa sintetizada com soma > 10% do total mantido → contador motivo=etapa-sintetizada")
        void telemetriaEtapaSintetizada() {
            // total de 2 km menor que aquec + desaq sintetizados (1,67 + 0,83) e PRINCIPAL sem ritmo: sem pace
            // não há bloco único, a divergência fica. Com ritmo o caso vira bloco único
            // (fix-etapas-sintetizadas-encaixe-total) e não conta — sem pace com etapa sintetizada conta
            // como etapa-sintetizada, não como principal-sem-ritmo (a ordem do teste em reconciliarContinuo).
            // TEMPO_RUN: nos tipos leves o pace Z2 de fallback faz o bloco único (fix-distancia-treino-soma-etapas)
            var treino = tempoRun("2.0", "30:00", etapa("PRINCIPAL", 30, 2.0, null));

            normalizacao.normalizar(treino, ctx);

            assertThat(contadorDivergencia("TEMPO_RUN", "etapa-sintetizada")).isEqualTo(1.0);
            assertThat(contadorDivergencia("TEMPO_RUN", "principal-sem-ritmo")).isZero();
        }

        @Test
        @DisplayName("telemetria: divergência mantida de até 10% não conta (ruído de arredondamento/aquec curto)")
        void telemetriaAbaixoDoLimiarNaoConta() {
            // PRINCIPAL sem ritmo com os 3,9 km "certos": soma 5,88 contra 6,0 → 2%
            var treino = continuo("6.0", "45:00", "7:28-7:55/km",
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, 3.9, null),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            normalizacao.normalizar(treino, ctxLeandro);

            assertThat(contadorDivergencia("REGENERATIVO", "principal-sem-ritmo")).isZero();
        }

        @Test
        @DisplayName("telemetria: quando o total é reconciliado não há divergência a contar")
        void telemetriaNaoContaQuandoReconcilia() {
            var treino = continuo("9.0", "45:00", "7:28-7:55/km",
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, 9.0, "7:28-7:55/km"),
                    etapa("DESAQUECIMENTO", 5, 0.5, null));

            normalizacao.normalizar(treino, ctxLeandro);

            assertThat(registry.find("plano_etapas_total_divergente").counters()).isEmpty();
        }

        @Test
        @DisplayName("sem limiar cadastrado: aquec/desaq caem no Z2 genérico 7:00/km")
        void semLimiarUsaZ2Generico() {
            Atleta semLimiar = Atleta.builder().id(UUID.randomUUID()).nivelExperiencia(NivelExperiencia.INICIANTE).build();
            var ctxSemLimiar = new ContextoNormalizacao(semLimiar, semLimiar.getId(), null, Map.of(), Map.of());
            var treino = continuo("6.0", "45:00", "7:28-7:55/km",
                    etapa("AQUECIMENTO", 14, 0.0, null),
                    etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"),
                    etapa("DESAQUECIMENTO", 7, 0.5, null));

            var resultado = normalizacao.normalizar(treino, ctxSemLimiar);

            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::distanciaKm).containsExactly(1.82, 3.9, 0.91);  // Z2 genérico 7,00 mais rápido que a PRINCIPAL (7,69)
        }

        /** Ritmo da PRINCIPAL mais lento que o Z2 do Leandro (6,33 × 1,20 = 7,60): o aquecimento segue a PRINCIPAL. */
        private boolean kmPrincipalRitmoLento(String ritmo) {
            return "7:28-7:55/km".equals(ritmo);
        }

        private EtapaTreinoLlmDto etapa(String tipo, Integer duracaoMin, Double distanciaKm, String ritmoAlvo) {
            return new EtapaTreinoLlmDto(1, tipo, tipo.toLowerCase(), duracaoMin, distanciaKm, null, 1, ritmoAlvo);
        }

        private TreinoPlanejadoLlmDto continuo(String distanciaKm, String duracaoMin, String ritmoAlvo,
                                               EtapaTreinoLlmDto... etapas) {
            return new TreinoPlanejadoLlmDto("SEGUNDA", "REGENERATIVO", null, null, null, null, null,
                    duracaoMin, distanciaKm != null ? Double.valueOf(distanciaKm) : null, ritmoAlvo, List.of(etapas));
        }

        private TreinoPlanejadoLlmDto tempoRun(String distanciaKm, String duracaoMin, EtapaTreinoLlmDto... etapas) {
            return new TreinoPlanejadoLlmDto("SEGUNDA", "TEMPO_RUN", null, null, null, null, null,
                    duracaoMin, Double.valueOf(distanciaKm), null, List.of(etapas));
        }
    }

    @Nested
    @DisplayName("runner")
    class Runner {

        private ListAppender<ILoggingEvent> appender;
        private Logger logger;
        private Level nivelAnterior;

        @BeforeEach
        void capturarDebug() {
            logger = (Logger) LoggerFactory.getLogger(NormalizacaoDeTreino.class);
            nivelAnterior = logger.getLevel();
            logger.setLevel(Level.DEBUG);
            appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
        }

        @AfterEach
        void restaurar() {
            logger.detachAppender(appender);
            logger.setLevel(nivelAnterior);
        }

        @Test
        @DisplayName("alterou é por equals, não identidade: recalcular-duracao devolve record novo com valores iguais → alterou=false")
        void alterouPorEqualsNaoIdentidade() {
            // FACIL (PADRAO): só a cauda. A duração "40:00" já é a soma das etapas (5+30+5), então
            // recalcular-duracao constrói um record novo idêntico — por identidade diria "alterou".
            var treino = new TreinoPlanejadoLlmDto("TERCA", "FACIL", "130-145 bpm", 40, 0.7, 4,
                    "Rodagem fácil", "40:00", 6.5, "6:00-6:30/km",
                    List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 5, 0.8, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(2, "PRINCIPAL", "Rodagem confortável", 30, 5.0, "130-145 bpm", 1, "6:00-6:30/km"),
                            new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Caminhada", 5, 0.7, "120-136 bpm", 1, null)));

            normalizacao.normalizar(treino, ctx);

            List<String> debug = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.DEBUG)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertThat(debug).hasSize(8); // sanear-ritmos + corrigir-pace-zona + um por passo da cauda comum
            assertThat(debug).allSatisfy(m -> assertThat(m).contains("familia=PADRAO").contains("alterou=false"));
            assertThat(debug.get(5)).contains("passo=recalcular-duracao");
        }

        @Test
        @DisplayName("um passo que muda o treino loga alterou=true, e só ele")
        void alterouTrueSoNoPassoQueMudou() {
            // duração "99:00" ≠ soma das etapas (40) → só recalcular-duracao altera
            var treino = new TreinoPlanejadoLlmDto("TERCA", "FACIL", "130-145 bpm", 40, 0.7, 4,
                    "Rodagem fácil", "99:00", 6.5, "6:00-6:30/km",
                    List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 5, 0.8, "120-136 bpm", 1, null),
                            new EtapaTreinoLlmDto(2, "PRINCIPAL", "Rodagem confortável", 30, 5.0, "130-145 bpm", 1, "6:00-6:30/km"),
                            new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Caminhada", 5, 0.7, "120-136 bpm", 1, null)));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.duracaoMin()).isEqualTo("40:00");
            List<String> alterados = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.contains("alterou=true"))
                    .toList();
            assertThat(alterados).singleElement().asString().contains("passo=recalcular-duracao");
        }
    }

    /**
     * fix-normalizador-etapas-incompletas: recalcular-duracao só sobrescreve a duração da LLM quando a
     * soma das etapas é consistente com ritmoAlvo × distanciaKm — ou quando não há triângulo para
     * desempatar. Caso real: REGENERATIVO 7km/45:00 a 7:28-7:55 cujas etapas somavam 25min.
     */
    @Nested
    @DisplayName("recalcular-duracao — desempate pelo triângulo pace×dist×dur")
    class RecalcularDuracaoComEtapasIncompletas {

        @Test
        @DisplayName("CA4: REGENERATIVO 45:00 / 7km / 7:28-7:55 com etapas 10+5min → PRINCIPAL encaixa nos 7 km, 50:00")
        void mantemDuracaoDaLlmQuandoEtapasNaoCobremOTreino() {
            var treino = continuo("REGENERATIVO", "45:00", 7.0, "7:28-7:55/km",
                    etapaMin("PRINCIPAL", 10), etapaMin("DESAQUECIMENTO", 5));

            var resultado = normalizacao.normalizar(treino, ctx);

            // fix-total-treino-soma-etapas: a PRINCIPAL sem ritmo usa o do treino e encaixa no total de 7 km,
            // e as etapas passam a cobrir o treino (antes: 45:00 da LLM com etapas de 25 min)
            assertThat(resultado.duracaoMin()).isEqualTo("50:00");
            assertThat(resultado.distanciaKm()).isEqualTo(7.0);
        }

        @Test
        @DisplayName("CA5: LONGO 60:00 / 10km / 6:45-7:00 com etapas 10+45+5 → 60:00 (soma consistente)")
        void somaConsistenteContinuaValendo() {
            var treino = continuo("LONGO", "60:00", 10.0, "6:45-7:00/km",
                    etapaMin("AQUECIMENTO", 10), etapaMin("PRINCIPAL", 45), etapaMin("DESAQUECIMENTO", 5));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.duracaoMin()).isEqualTo("60:00");
        }

        @Test
        @DisplayName("CA5: LLM diz 90:00 fora da tolerância e etapas somam 60 dentro dela → 60:00")
        void somaConsistentePrevaleceSobreDuracaoDaLlmForaDaTolerancia() {
            var treino = continuo("LONGO", "90:00", 10.0, "6:45-7:00/km",
                    etapaMin("AQUECIMENTO", 10), etapaMin("PRINCIPAL", 45), etapaMin("DESAQUECIMENTO", 5));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.duracaoMin()).isEqualTo("60:00");
        }

        @Test
        @DisplayName("CA4b: etapas já fechavam 45 e o reparo acrescenta 10 de aquec → nem os 55 esticados nem os 45 da LLM: a soma encaixada (42)")
        void reparoQueEsticaEtapasNaoVenceDuracaoDaLlm() {
            // caso real de 21/09 22:41: 6km a 7:28-7:55 esperam ~46min; a soma 55 ficava dentro dos
            // 20% e vencia com o desempate por tolerância — por isso o desempate é "mais perto vence".
            // fix-etapas-sintetizadas-encaixe-total: a PRINCIPAL encaixada nos 6 km deixa as etapas em 42 e o
            // treino passa a dizer 42 (antes 45, com o editor somando 42 — card e editor divergentes)
            var treino = continuo("REGENERATIVO", "45:00", 6.0, "7:28-7:55/km",
                    etapaMin("PRINCIPAL", 40), etapaMin("DESAQUECIMENTO", 5));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.duracaoMin()).isEqualTo("42:00");
        }

        @Test
        @DisplayName("CA6: TEMPO_RUN sem ritmoAlvo não há desempate → soma das etapas (regra vigente)")
        void semRitmoAlvoMantemRegraAtual() {
            // TEMPO_RUN: nos tipos leves o pace Z2 de fallback encaixa a PRINCIPAL (fix-distancia-treino-soma-etapas)
            var treino = continuo("TEMPO_RUN", "45:00", 7.0, null,
                    etapaMin("PRINCIPAL", 10), etapaMin("DESAQUECIMENTO", 5));

            var resultado = normalizacao.normalizar(treino, ctx);

            // reparo acrescenta AQUECIMENTO de 10min → 10 + 10 + 5
            assertThat(resultado.duracaoMin()).isEqualTo("25:00");
        }

        @Test
        @DisplayName("LLM mais longe do triângulo que a soma → soma das etapas")
        void ambosInconsistentesMantemSoma() {
            // esperado ≈ 53.8min; 120 desvia 123% e a soma das etapas, com a PRINCIPAL encaixada nos 7 km
            // (fix-total-treino-soma-etapas), dá 50 → a soma está mais perto e vence
            var treino = continuo("REGENERATIVO", "120:00", 7.0, "7:28-7:55/km",
                    etapaMin("PRINCIPAL", 10), etapaMin("DESAQUECIMENTO", 5));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.duracaoMin()).isEqualTo("50:00");
        }

        private EtapaTreinoLlmDto etapaMin(String tipoEtapa, int duracaoMin) {
            return new EtapaTreinoLlmDto(1, tipoEtapa, tipoEtapa.toLowerCase(), duracaoMin, 0.0, null, 1, null);
        }

        private TreinoPlanejadoLlmDto continuo(String tipo, String duracaoMin, Double distanciaKm,
                                               String ritmoAlvo, EtapaTreinoLlmDto... etapas) {
            return new TreinoPlanejadoLlmDto("TERCA", tipo, null, null, null, null, null,
                    duracaoMin, distanciaKm, ritmoAlvo, List.of(etapas));
        }
    }

    /**
     * fix-fartlek-etapas-estruturadas: a receita FARTLEK ganhou gates estruturais depois de
     * {@code expandir}. Caso real: FARTLEK do Leandro de 24/09 persistido com PRINCIPAL "Fartlek
     * livre" única — sem gate, passava sem turno de reparo.
     */
    @Nested
    @DisplayName("família FARTLEK — gates estruturais depois de expandir")
    class Fartlek {

        @Test
        @DisplayName("CA2: 'Fartlek livre' numa PRINCIPAL única → LLMException pedindo acelerações individuais")
        void fartlekLivreEmEtapaUnicaReprova() {
            var treino = fartlek(aquec(),
                    new EtapaTreinoLlmDto(2, "PRINCIPAL", "Fartlek livre 20-30 min com acelerações curtas Z2-Z3.",
                            30, 0.0, null, 1, "6:20-7:28/km"),
                    desaq());

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("acelerações individuais");
        }

        @Test
        @DisplayName("CA3: série comprimida '4x (1min forte + 2min leve)' é expandida e passa")
        void serieComprimidaReconhecivelEhExpandidaEPassa() {
            var treino = fartlek(aquec(),
                    new EtapaTreinoLlmDto(2, "PRINCIPAL", "Fartlek leve Z2-Z3, 4x (1min forte + 2min leve)",
                            12, 0.0, null, 1, "6:20-7:28/km"),
                    desaq());

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(tipos(resultado)).containsExactly("AQUECIMENTO",
                    "INTERVALADO", "RECUPERACAO", "INTERVALADO", "RECUPERACAO",
                    "INTERVALADO", "RECUPERACAO", "INTERVALADO", "RECUPERACAO",
                    "DESAQUECIMENTO");
        }

        @Test
        @DisplayName("CA4: já expandido pela LLM (aquec, 3 pares alternados, desaq) → passa")
        void jaExpandidoPassa() {
            var treino = fartlek(aquec(), acel(), rec(), acel(), rec(), acel(), rec(), desaq());

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(tipos(resultado)).hasSize(8).startsWith("AQUECIMENTO").endsWith("DESAQUECIMENTO");
        }

        @Test
        @DisplayName("CA5: 2 acelerações é o piso → passa")
        void duasAceleracoesPassa() {
            var treino = fartlek(aquec(), acel(), rec(), acel(), rec(), desaq());

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(tipos(resultado)).filteredOn("INTERVALADO"::equals).hasSize(2);
        }

        @Test
        @DisplayName("CA5: 1 aceleração só → LLMException")
        void umaAceleracaoReprova() {
            var treino = fartlek(aquec(), acel(), rec(), desaq());

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("acelerações individuais");
        }

        @Test
        @DisplayName("CA6: sem aquecimento → LLMException")
        void semAquecimentoReprova() {
            var treino = fartlek(acel(), rec(), acel(), rec(), desaq());

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("aquecimento");
        }

        @Test
        @DisplayName("CA6: desaquecimento fora da última posição → LLMException")
        void desaquecimentoForaDoFimReprova() {
            var treino = fartlek(aquec(), acel(), rec(), desaq(), acel(), rec());

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("desaquecimento");
        }

        @Test
        @DisplayName("CA7: acelerações sem nenhuma recuperação → LLMException")
        void semRecuperacaoReprova() {
            var treino = fartlek(aquec(), acel(), acel(), acel(), desaq());

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("recuperação");
        }

        @Test
        @DisplayName("CA7: recuperação antes da 1ª aceleração → LLMException")
        void recuperacaoSemAceleracaoAnteriorReprova() {
            var treino = fartlek(aquec(), rec(), acel(), rec(), acel(), desaq());

            assertThatThrownBy(() -> normalizacao.normalizar(treino, ctx))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("recuperações sem tiro");
        }

        @Test
        @DisplayName("CA7b: fartlek 'Misto' (2 acelerações seguidas + 1 recuperação por bloco) → passa")
        void fartlekMistoComAceleracoesConsecutivasPassa() {
            // o system prompt prescreve "Misto: 6× (3min Z4 + 1min Z5 + 2min Z2)" na Categoria D —
            // alternância estrita ou balanceamento 1:1 o reprovariam
            var treino = fartlek(aquec(), acel(), acel(), rec(), acel(), acel(), rec(), desaq());

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(tipos(resultado)).filteredOn("INTERVALADO"::equals).hasSize(4);
        }

        @Test
        @DisplayName("CA9: caso real 22/09 — série 5× (1min + 2min) com a distância do treino na PRINCIPAL não gera pace impossível")
        void serieExpandidaNaoHerdaDistanciaDoTreinoInteiro() {
            // Arrange — resposta bruta da LLM: treino 40:00 / 5,0 km, PRINCIPAL 25min / 5,0km. Antes da
            // correção o expansor repartia os 5 km pela série (1 km a cada 3min) e o treino saía com
            // 6,98 km em 30min — 4:18/km para um ritmo de 6:20-6:45
            var ctxLeandro = contextoComLimiar(6.33);
            var treino = new TreinoPlanejadoLlmDto("QUARTA", "FARTLEK", null, 45, 1.0, 6, "Fartlek",
                    "40:00", 5.0, "6:20-6:45/km", List.of(
                    new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento progressivo Z1-Z2", 10, 0.0, null, 1, null),
                    new EtapaTreinoLlmDto(2, "PRINCIPAL", "Fartlek 5× (1min Z3 + 2min Z2)", 25, 5.0, null, 1, "6:20-6:45/km"),
                    new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento leve Z1-Z2", 5, 0.0, null, 1, null)));

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            // Assert — nenhuma etapa (nem o treino) mais rápida que o limite rápido do ritmo, 6:20/km
            double limiteRapido = 6.0 + 20.0 / 60;
            assertThat(resultado.etapas())
                    .filteredOn(e -> e.distanciaKm() != null && e.distanciaKm() > 0)
                    .allSatisfy(e -> assertThat(e.duracaoMin() / e.distanciaKm())
                            .as("pace de %s", e.descricaoEtapa())
                            .isGreaterThanOrEqualTo(limiteRapido));
            assertThat(resultado.duracaoMin()).isEqualTo("30:00");
            assertThat(resultado.distanciaKm()).isCloseTo(3.91, org.assertj.core.data.Offset.offset(0.05));
            assertThat(30.0 / resultado.distanciaKm()).isGreaterThanOrEqualTo(limiteRapido);
        }

        @Test
        @DisplayName("CA12: recuperação criada pela expansão recebe duração ÷ pace Z1 (corrigir-temporais depois de expandir)")
        void recuperacaoExpandidaRecebePaceDeTrote() {
            var ctxLeandro = contextoComLimiar(6.33);
            var treino = fartlek(aquec(),
                    new EtapaTreinoLlmDto(2, "PRINCIPAL", "Fartlek 5× (1min Z3 + 2min Z2)", 15, 5.0, null, 1, "6:20-6:45/km"),
                    desaq());

            var resultado = normalizacao.normalizar(treino, ctxLeandro);

            // Z1 = limiar 6,33 × 1,35 = 8,55 min/km → 2min / 8,55 = 0,23 km
            assertThat(resultado.etapas())
                    .filteredOn(e -> "RECUPERACAO".equals(e.tipoEtapa()))
                    .hasSize(5)
                    .allSatisfy(e -> assertThat(e.distanciaKm()).isEqualTo(0.23));
        }

        private ContextoNormalizacao contextoComLimiar(double paceLimiar) {
            Atleta atleta = Atleta.builder()
                    .id(UUID.randomUUID())
                    .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                    .paceLimiar(BigDecimal.valueOf(paceLimiar))
                    .build();
            return new ContextoNormalizacao(atleta, atleta.getId(), null, Map.of(), Map.of());
        }

        private EtapaTreinoLlmDto acel() {
            return new EtapaTreinoLlmDto(1, "INTERVALADO", "Aceleração Z3", 2, 0.35, null, 1, "5:40-6:00/km");
        }

        private TreinoPlanejadoLlmDto fartlek(EtapaTreinoLlmDto... etapas) {
            return new TreinoPlanejadoLlmDto("QUINTA", "FARTLEK", null, 50, 1.0, 6, "Fartlek",
                    "50:00", 8.0, "6:20-7:28/km", List.of(etapas));
        }

        private List<String> tipos(TreinoPlanejadoLlmDto treino) {
            return treino.etapas().stream().map(EtapaTreinoLlmDto::tipoEtapa).toList();
        }
    }

    private double contadorDivergencia(String tipo, String motivo) {
        var c = registry.find("plano_etapas_total_divergente").tag("tipo", tipo).tag("motivo", motivo).counter();
        return c == null ? 0.0 : c.count();
    }

    // ---------- fixtures (aquec/desaq com 1.67km = 10min × paceZ2 6.0, estável sob corrigir-temporais) ----------

    private static EtapaTreinoLlmDto aquec() {
        return new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Trote leve", 10, 1.67, "120-136 bpm", 1, null);
    }

    private static EtapaTreinoLlmDto desaq() {
        return new EtapaTreinoLlmDto(1, "DESAQUECIMENTO", "Caminhada", 10, 1.67, "120-136 bpm", 1, null);
    }

    private static EtapaTreinoLlmDto tiro(Integer duracaoMin, String ritmoAlvo) {
        return new EtapaTreinoLlmDto(1, "INTERVALADO", "Intervalo Z5", duracaoMin, 0.8, "90-95% FCmax", 1, ritmoAlvo);
    }

    private static EtapaTreinoLlmDto rec() {
        return new EtapaTreinoLlmDto(1, "RECUPERACAO", "Recuperação trote", 2, 0.3, "60-70% FCmax", 1, null);
    }

    private static TreinoPlanejadoLlmDto intervalado(double distanciaKm, EtapaTreinoLlmDto... etapas) {
        return new TreinoPlanejadoLlmDto("TERCA", "INTERVALADO", "150-160 bpm", 60, 8.0, 6, "VO2max",
                "50:00", distanciaKm, "5:00-5:15/km", List.of(etapas));
    }

    @Nested
    @DisplayName("teto de pace (fix-pace-teto-invariante)")
    class TetoDePace {

        private static final BigDecimal TETO = new BigDecimal("4.9");   // 4:54/km
        private static final BigDecimal PISO = new BigDecimal("6.25");  // 6:15/km

        private TreinoPlanejadoLlmDto continuo(String tipo, String ritmoCabecalho, String ritmoPrincipal) {
            return new TreinoPlanejadoLlmDto("QUARTA", tipo, null, 50, 0.7, 5, "Contínuo", "55:00", 8.0, ritmoCabecalho,
                    List.of(new EtapaTreinoLlmDto(1, "AQUECIMENTO", "Aquecimento", 10, 0.0, null, 1, null),
                            new EtapaTreinoLlmDto(2, "PRINCIPAL", "Bloco", 40, 8.0, null, 1, ritmoPrincipal),
                            new EtapaTreinoLlmDto(3, "DESAQUECIMENTO", "Desaquecimento", 5, 0.0, null, 1, null)));
        }

        /** Atleta sem limiar nem zonas manuais: zonasPace null, como em produção. */
        private ContextoNormalizacao semZonas() {
            Atleta atleta = Atleta.builder().id(UUID.randomUUID()).nivelExperiencia(NivelExperiencia.INTERMEDIARIO).build();
            return new ContextoNormalizacao(atleta, atleta.getId(), null,
                    Map.of(br.com.menthoros.backend.enums.TipoTreino.CONTINUO, TETO, br.com.menthoros.backend.enums.TipoTreino.FACIL, TETO),
                    Map.of(br.com.menthoros.backend.enums.TipoTreino.CONTINUO, PISO));
        }

        private static String principal(TreinoPlanejadoLlmDto t) {
            return t.etapas().stream().filter(e -> "PRINCIPAL".equals(e.tipoEtapa())).findFirst().orElseThrow().ritmoAlvo();
        }

        @Test
        @DisplayName("CA2: sem zonas de pace, a PRINCIPAL mais rápida que o teto vai para o teto com a mesma amplitude")
        void semZonasPrincipalNoTeto() {
            var saida = normalizacao.normalizar(continuo("CONTINUO", "5:00-5:20/km", "4:20-4:40/km"), semZonas());

            assertThat(principal(saida)).isEqualTo("4:54-5:14/km");
        }

        @ParameterizedTest(name = "tipo \"{0}\"")
        @ValueSource(strings = {"facil", " FACIL "})
        @DisplayName("CA12: tipo do treino em minúscula ou com espaço ainda recebe o teto no cabeçalho")
        void tipoNaoCanonico(String tipo) {
            var saida = normalizacao.normalizar(continuo(tipo, "4:20-4:40/km", "5:00-5:20/km"), semZonas());

            assertThat(saida.ritmoAlvo()).isEqualTo("4:54-5:14/km");
        }

        @Test
        @DisplayName("CA10: ritmo único mais lento que o teto fica intacto no cabeçalho — o piso não o puxa")
        void unicoLentoIntacto() {
            var saida = normalizacao.normalizar(continuo("CONTINUO", "8:00/km", "5:00-5:20/km"), semZonas());

            assertThat(saida.ritmoAlvo()).isEqualTo("8:00/km");
        }

        @Test
        @DisplayName("CA6: ritmo único mais rápido que o teto na PRINCIPAL vira o teto")
        void unicoRapidoNoTeto() {
            var saida = normalizacao.normalizar(continuo("CONTINUO", "5:00-5:20/km", "4:00/km"), semZonas());

            assertThat(principal(saida)).isEqualTo("4:54/km");
        }

        @Test
        @DisplayName("CA7: ritmo único implausível no cabeçalho é descartado")
        void unicoImplausivel() {
            var saida = normalizacao.normalizar(continuo("CONTINUO", "2:00/km", "5:00-5:20/km"), semZonas());

            assertThat(saida.ritmoAlvo()).isNull();
        }

        @Test
        @DisplayName("CA8: texto livre com m:ss mais rápido que o teto não é reescrito")
        void textoLivreIntacto() {
            var saida = normalizacao.normalizar(continuo("CONTINUO", "5:00-5:20/km", "forte perto de 4:00"), semZonas());

            assertThat(principal(saida)).isEqualTo("forte perto de 4:00");
        }
    }

    // mínimo pra família TRES_ETAPAS: só o tipo das etapas importa ao reparo e ao gate
    private static EtapaTreinoLlmDto etapa3(String tipoEtapa) {
        return new EtapaTreinoLlmDto(1, tipoEtapa, "x", 10, 1.0, null, 1, null);
    }

    private static TreinoPlanejadoLlmDto tresEtapas(String tipoTreino, EtapaTreinoLlmDto... etapas) {
        return new TreinoPlanejadoLlmDto("SEGUNDA", tipoTreino, null, null, null, null, null, null, null, null, List.of(etapas));
    }
}
