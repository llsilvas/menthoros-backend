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
    private ContextoNormalizacao ctx;

    @BeforeEach
    void setUp() {
        normalizacao = new NormalizacaoDeTreino(
                new TreinoNormalizador(new PaceValidator()),
                new EtapaFcValidator(),
                new PlanoEstruturaReparador(new SimpleMeterRegistry()),
                new PaceValidator(),
                new SimpleMeterRegistry());
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
            assertThat(debug).hasSize(6); // um por passo da cauda comum
            assertThat(debug).allSatisfy(m -> assertThat(m).contains("familia=PADRAO").contains("alterou=false"));
            assertThat(debug.get(3)).contains("passo=recalcular-duracao");
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
        @DisplayName("CA4: REGENERATIVO 45:00 / 7km / 7:28-7:55 com etapas 10+5min → mantém 45:00")
        void mantemDuracaoDaLlmQuandoEtapasNaoCobremOTreino() {
            var treino = continuo("REGENERATIVO", "45:00", 7.0, "7:28-7:55/km",
                    etapaMin("PRINCIPAL", 10), etapaMin("DESAQUECIMENTO", 5));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.duracaoMin()).isEqualTo("45:00");
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
        @DisplayName("CA4b: etapas já fechavam 45 e o reparo acrescenta 10 de aquec → 55 (19,5%) perde para 45 (2%)")
        void reparoQueEsticaEtapasNaoVenceDuracaoDaLlm() {
            // caso real de 21/09 22:41: 6km a 7:28-7:55 esperam ~46min; a soma 55 ficava dentro dos
            // 20% e vencia com o desempate por tolerância — por isso o desempate é "mais perto vence"
            var treino = continuo("REGENERATIVO", "45:00", 6.0, "7:28-7:55/km",
                    etapaMin("PRINCIPAL", 40), etapaMin("DESAQUECIMENTO", 5));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.duracaoMin()).isEqualTo("45:00");
        }

        @Test
        @DisplayName("CA6: sem ritmoAlvo não há desempate → soma das etapas (regra vigente)")
        void semRitmoAlvoMantemRegraAtual() {
            var treino = continuo("REGENERATIVO", "45:00", 7.0, null,
                    etapaMin("PRINCIPAL", 10), etapaMin("DESAQUECIMENTO", 5));

            var resultado = normalizacao.normalizar(treino, ctx);

            // reparo acrescenta AQUECIMENTO de 10min → 10 + 10 + 5
            assertThat(resultado.duracaoMin()).isEqualTo("25:00");
        }

        @Test
        @DisplayName("LLM mais longe do triângulo que a soma → soma das etapas")
        void ambosInconsistentesMantemSoma() {
            // esperado ≈ 53.8min; 120 desvia 123%, soma 25 desvia 54% → a soma está mais perto e vence
            var treino = continuo("REGENERATIVO", "120:00", 7.0, "7:28-7:55/km",
                    etapaMin("PRINCIPAL", 10), etapaMin("DESAQUECIMENTO", 5));

            var resultado = normalizacao.normalizar(treino, ctx);

            assertThat(resultado.duracaoMin()).isEqualTo("25:00");
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

    // mínimo pra família TRES_ETAPAS: só o tipo das etapas importa ao reparo e ao gate
    private static EtapaTreinoLlmDto etapa3(String tipoEtapa) {
        return new EtapaTreinoLlmDto(1, tipoEtapa, "x", 10, 1.0, null, 1, null);
    }

    private static TreinoPlanejadoLlmDto tresEtapas(String tipoTreino, EtapaTreinoLlmDto... etapas) {
        return new TreinoPlanejadoLlmDto("SEGUNDA", tipoTreino, null, null, null, null, null, null, null, null, List.of(etapas));
    }
}
