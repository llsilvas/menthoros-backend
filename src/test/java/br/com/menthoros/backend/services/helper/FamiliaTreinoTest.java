package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.TipoTreino;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden da ORDEM (pipeline-normalizacao-treino, task 2.3): a receita de cada família, nome a nome,
 * transcrita do {@code design.md} — que por sua vez foi conferida linha a linha contra
 * {@code PlanoLlmValidator.normalizarTreino} em 72304b9 (DoR, 4 rodadas). Mudar a ordem aqui é
 * mudar regra de negócio: os dois bugs de ordem da F2 (gate de contagem mascarado pelo padding;
 * duração dos tiros não rechecada após o IA-05) são legíveis nesta lista.
 */
@DisplayName("FamiliaTreino — receita por família (golden da ordem)")
class FamiliaTreinoTest {

    private static final List<String> CAUDA_COMUM = List.of(
            "validar-repeticoes",
            "corrigir-fc-zona",
            "corrigir-pace-teto-piso",
            "recalcular-duracao",
            "garantir-distancia-continuo",
            "validar-triangulo");

    private NormalizacaoDeTreino normalizacao;

    @BeforeEach
    void setUp() {
        normalizacao = new NormalizacaoDeTreino(
                new TreinoNormalizador(new PaceValidator()),
                new EtapaFcValidator(),
                new PlanoEstruturaReparador(new SimpleMeterRegistry()),
                new PaceValidator(),
                new SimpleMeterRegistry());
    }

    @Nested
    @DisplayName("receita")
    class ReceitaPorFamilia {

        @Test
        @DisplayName("INTERVALADO_TIRO: gate estrutural ANTES de normalizar; gate-duracao-tiros 2× (a 2ª é o IA-05)")
        void intervaladoTiro() {
            assertThat(normalizacao.receita(FamiliaTreino.INTERVALADO_TIRO).nomes()).containsExactly(
                    "corrigir-temporais",
                    "expandir",
                    "gate-existencia",
                    "gate-contagem",
                    "gate-presenca-aquec-desaq",
                    "gate-ordem-aquec-desaq",
                    "alerta-poucos-tiros",
                    "gate-balanceamento",
                    "gate-sequencia",
                    "alerta-distancias",
                    "gate-duracao-tiros",
                    "log-validacao-ok",
                    "normalizar-intervalado",
                    "reconciliar-distancia",
                    "gate-duracao-tiros",
                    "validar-repeticoes",
                    "corrigir-fc-zona",
                    "corrigir-pace-teto-piso",
                    "recalcular-duracao",
                    "garantir-distancia-continuo",
                    "validar-triangulo");
        }

        @Test
        @DisplayName("FARTLEK: gates estruturais DEPOIS de expandir (série comprimida vira pares antes de ser julgada)")
        void fartlek() {
            assertThat(normalizacao.receita(FamiliaTreino.FARTLEK).nomes())
                    .containsExactlyElementsOf(concat(List.of("expandir", "corrigir-temporais",
                            "gate-existencia", "gate-presenca-aquec-desaq", "gate-ordem-aquec-desaq",
                            "gate-aceleracoes-fartlek", "gate-sequencia", "reconciliar-distancia")));
        }

        @Test
        @DisplayName("TRES_ETAPAS: reparar ANTES de validar por tipo (só passa REGENERATIVO só com PRINCIPAL por isso)")
        void tresEtapas() {
            assertThat(normalizacao.receita(FamiliaTreino.TRES_ETAPAS).nomes())
                    .containsExactlyElementsOf(concat(List.of("reparar-3-etapas", "validar-por-tipo")));
        }

        @Test
        @DisplayName("PADRAO: só a cauda comum — explícito, não omissão")
        void padrao() {
            assertThat(normalizacao.receita(FamiliaTreino.PADRAO).nomes()).containsExactlyElementsOf(CAUDA_COMUM);
        }

        @ParameterizedTest
        @EnumSource(FamiliaTreino.class)
        @DisplayName("toda família tem receita e toda receita termina na cauda comum")
        void todaFamiliaTemReceita(FamiliaTreino familia) {
            List<String> nomes = normalizacao.receita(familia).nomes();
            assertThat(nomes).isNotEmpty();
            assertThat(nomes.subList(nomes.size() - CAUDA_COMUM.size(), nomes.size())).isEqualTo(CAUDA_COMUM);
        }
    }

    @Nested
    @DisplayName("de(tipoTreino)")
    class De {

        @ParameterizedTest
        @CsvSource({
                "INTERVALADO, INTERVALADO_TIRO",
                "TIRO, INTERVALADO_TIRO",
                "FARTLEK, FARTLEK",
                "REGENERATIVO, TRES_ETAPAS",
                "CONTINUO, TRES_ETAPAS",
                "TEMPO_RUN, TRES_ETAPAS",
                "LONGO, TRES_ETAPAS",
                "FACIL, PADRAO",
                "SUBIDA, PADRAO",
                "PROVA, PADRAO",
                "DESCANSO, PADRAO"})
        @DisplayName("os 11 tipos de treino caem numa família")
        void mapeiaOs11Tipos(String tipo, FamiliaTreino esperada) {
            assertThat(FamiliaTreino.de(tipo)).isEqualTo(esperada);
        }

        @ParameterizedTest
        @EnumSource(TipoTreino.class)
        @DisplayName("todo TipoTreino do enum tem família (adicionar um tipo força decisão aqui)")
        void todoTipoTreinoTemFamilia(TipoTreino tipo) {
            assertThat(FamiliaTreino.de(tipo.name())).isNotNull();
        }

        @Test
        @DisplayName("null e desconhecido caem em PADRAO; comparação é exata, sem ignorar caixa")
        void nullDesconhecidoECaixa() {
            assertThat(FamiliaTreino.de(null)).isEqualTo(FamiliaTreino.PADRAO);
            assertThat(FamiliaTreino.de("QUALQUER_COISA")).isEqualTo(FamiliaTreino.PADRAO);
            assertThat(FamiliaTreino.de("intervalado")).isEqualTo(FamiliaTreino.PADRAO);
        }
    }

    private static List<String> concat(List<String> cabeca) {
        return java.util.stream.Stream.concat(cabeca.stream(), CAUDA_COMUM.stream()).toList();
    }
}
