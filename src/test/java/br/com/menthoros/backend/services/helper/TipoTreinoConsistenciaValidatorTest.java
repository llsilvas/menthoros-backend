package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD: TipoTreinoConsistenciaValidator
 *
 * <p>Valida que o tipo declarado pelo coach é consistente com a estrutura real
 * das etapas (baseada no IF = fcMedia / fcLimiar do atleta).
 *
 * <p>Regras:
 * <ul>
 *   <li>REGENERATIVO: IF médio ≤ 0.70 e nenhuma etapa com IF > 0.80</li>
 *   <li>CONTINUO: IF médio em [0.70, 0.90] e variação intra-treino &lt; 20%</li>
 *   <li>TEMPO_RUN: IF médio em [0.85, 0.95] e segmento central dominante &gt; 0.85</li>
 * </ul>
 */
class TipoTreinoConsistenciaValidatorTest {

    private TipoTreinoConsistenciaValidator validator;

    // FC limiar do atleta padrão = 170 bpm
    private static final int FC_LIMIAR = 170;

    @BeforeEach
    void setUp() {
        validator = new TipoTreinoConsistenciaValidator();
    }

    // =========================================================================
    // REGENERATIVO
    // =========================================================================

    @Test
    @DisplayName("REGENERATIVO com IF médio 0.65 e sem picos — coerente, sem sugestão")
    void regenerativoCoerente_semSugestao() {
        // IFs: 0.62, 0.65, 0.68 → média ≈ 0.65, nenhum > 0.80
        TreinoRealizado treino = criarTreinoComEtapas(TipoTreino.REGENERATIVO,
                List.of(0.62, 0.65, 0.68));

        Optional<SugestaoReclassificacao> sugestao = validator.validarEstrutura(treino);

        assertTrue(sugestao.isEmpty(),
                "REGENERATIVO coerente não deve gerar sugestão");
    }

    @Test
    @DisplayName("REGENERATIVO com etapa IF=0.88 — sugere CONTINUO")
    void regenerativoComPico_sugereContiguo() {
        // IFs: 0.62, 0.65, 0.88 → pico acima de 0.80
        TreinoRealizado treino = criarTreinoComEtapas(TipoTreino.REGENERATIVO,
                List.of(0.62, 0.65, 0.88));

        Optional<SugestaoReclassificacao> sugestao = validator.validarEstrutura(treino);

        assertTrue(sugestao.isPresent(), "Deve sugerir reclassificação quando há pico de IF");
        assertEquals(TipoTreino.CONTINUO, sugestao.get().tipoSugerido(),
                "Pico moderado deve sugerir CONTINUO");
        assertNotNull(sugestao.get().motivo(), "Sugestão deve ter motivo");
        assertTrue(sugestao.get().confianca() > 0.0, "Confiança deve ser positiva");
    }

    @Test
    @DisplayName("REGENERATIVO com IF médio 0.75 — sugere CONTINUO por IF médio elevado")
    void regenerativoComIfMedioAlto_sugereContiguo() {
        // IFs: 0.73, 0.75, 0.77 → média 0.75, acima de 0.70
        TreinoRealizado treino = criarTreinoComEtapas(TipoTreino.REGENERATIVO,
                List.of(0.73, 0.75, 0.77));

        Optional<SugestaoReclassificacao> sugestao = validator.validarEstrutura(treino);

        assertTrue(sugestao.isPresent(), "IF médio > 0.70 em REGENERATIVO deve gerar sugestão");
        assertEquals(TipoTreino.CONTINUO, sugestao.get().tipoSugerido());
    }

    // =========================================================================
    // CONTINUO
    // =========================================================================

    @Test
    @DisplayName("CONTINUO coerente — IF médio 0.78 sem variação extrema")
    void continuoCoerente_semSugestao() {
        // IFs: 0.76, 0.78, 0.80 → média 0.78, variação < 20%
        TreinoRealizado treino = criarTreinoComEtapas(TipoTreino.CONTINUO,
                List.of(0.76, 0.78, 0.80));

        Optional<SugestaoReclassificacao> sugestao = validator.validarEstrutura(treino);

        assertTrue(sugestao.isEmpty(), "CONTINUO coerente não deve gerar sugestão");
    }

    @Test
    @DisplayName("CONTINUO com variação extrema de IF — sugere reclassificação")
    void continuoComVariacaoExtrema_sugereReclassificacao() {
        // IFs: 0.60, 0.78, 0.95 → variação (max - min) = 0.35 > 20%
        TreinoRealizado treino = criarTreinoComEtapas(TipoTreino.CONTINUO,
                List.of(0.60, 0.78, 0.95));

        Optional<SugestaoReclassificacao> sugestao = validator.validarEstrutura(treino);

        assertTrue(sugestao.isPresent(), "Variação extrema em CONTINUO deve gerar sugestão");
    }

    // =========================================================================
    // TEMPO_RUN
    // =========================================================================

    @Test
    @DisplayName("TEMPO_RUN com segmento central IF=0.91 — coerente")
    void tempoRunCoerente_semSugestao() {
        // Estrutura: aquecimento leve, segmento central dominante acima de 0.85, desaquecimento leve
        // IFs: 0.70 (aquec), 0.91 (central), 0.91 (central), 0.70 (desaq)
        TreinoRealizado treino = criarTreinoComEtapas(TipoTreino.TEMPO_RUN,
                List.of(0.70, 0.91, 0.91, 0.70));

        Optional<SugestaoReclassificacao> sugestao = validator.validarEstrutura(treino);

        assertTrue(sugestao.isEmpty(), "TEMPO_RUN coerente com segmento dominante não deve gerar sugestão");
    }

    @Test
    @DisplayName("TEMPO_RUN sem segmento dominante acima de 0.85 — sugere CONTINUO")
    void tempoRunSemDominante_sugereContiguo() {
        // IFs: 0.78, 0.80, 0.82 → nenhuma etapa > 0.85
        TreinoRealizado treino = criarTreinoComEtapas(TipoTreino.TEMPO_RUN,
                List.of(0.78, 0.80, 0.82));

        Optional<SugestaoReclassificacao> sugestao = validator.validarEstrutura(treino);

        assertTrue(sugestao.isPresent(), "TEMPO_RUN sem segmento dominante deve sugerir reclassificação");
        assertEquals(TipoTreino.CONTINUO, sugestao.get().tipoSugerido());
    }

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Test
    @DisplayName("treino sem etapas — retorna Optional.empty() sem exception")
    void treinoSemEtapas_retornaVazio() {
        TreinoRealizado treino = criarTreinoSemEtapas(TipoTreino.REGENERATIVO);

        Optional<SugestaoReclassificacao> sugestao = assertDoesNotThrow(
                () -> validator.validarEstrutura(treino),
                "Treino sem etapas não deve lançar exceção"
        );

        assertTrue(sugestao.isEmpty(), "Treino sem etapas deve retornar Optional.empty()");
    }

    @Test
    @DisplayName("treino sem fcLimiar do atleta — retorna Optional.empty() sem exception")
    void treinoSemFcLimiarAtleta_retornaVazio() {
        Atleta atletaSemLimiar = Atleta.builder()
                .nome("Sem Limiar")
                .objetivo("Teste")
                .nivelExperiencia(NivelExperiencia.INICIANTE)
                // fcLimiar não setado
                .build();

        TreinoRealizado treino = new TreinoRealizado();
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(TipoTreino.REGENERATIVO);
        treino.setDuracaoMin(Duration.ofMinutes(60));
        treino.setAtleta(atletaSemLimiar);
        treino.setEtapasRealizadas(List.of(
                criarEtapa(1, FC_LIMIAR)
        ));

        Optional<SugestaoReclassificacao> sugestao = assertDoesNotThrow(
                () -> validator.validarEstrutura(treino),
                "Atleta sem fcLimiar não deve lançar exceção"
        );

        assertTrue(sugestao.isEmpty(), "Sem fcLimiar não é possível calcular IF — retorna empty");
    }

    @Test
    @DisplayName("treino null — lança IllegalArgumentException")
    void treinoNull_lancaIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validarEstrutura(null),
                "Treino null deve lançar IllegalArgumentException");
    }

    @Test
    @DisplayName("tipo não coberto pelas regras estruturais — retorna Optional.empty()")
    void tipoNaoCoberto_retornaVazio() {
        // INTERVALADO, FARTLEK, LONGO etc. não têm regras estruturais definidas ainda
        TreinoRealizado treino = criarTreinoComEtapas(TipoTreino.INTERVALADO,
                List.of(0.90, 0.55, 0.90, 0.55));

        Optional<SugestaoReclassificacao> sugestao = validator.validarEstrutura(treino);

        assertTrue(sugestao.isEmpty(),
                "Tipos sem regras estruturais definidas devem retornar Optional.empty()");
    }

    // =========================================================================
    // Helpers de construção
    // =========================================================================

    /**
     * Cria um TreinoRealizado com etapas cujas fcMedia são calculadas a partir
     * dos IFs fornecidos: fcMedia = IF * FC_LIMIAR.
     *
     * <p>O atleta padrão tem fcLimiar = 170.
     */
    private TreinoRealizado criarTreinoComEtapas(TipoTreino tipo, List<Double> ifs) {
        Atleta atleta = Atleta.builder()
                .nome("Atleta Teste")
                .objetivo("Corrida")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .fcLimiar(FC_LIMIAR)
                .build();

        List<EtapaRealizada> etapas = IntStream.range(0, ifs.size())
                .mapToObj(i -> criarEtapa(i + 1, (int) Math.round(ifs.get(i) * FC_LIMIAR)))
                .toList();

        TreinoRealizado treino = new TreinoRealizado();
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(tipo);
        treino.setDuracaoMin(Duration.ofMinutes(60));
        treino.setAtleta(atleta);
        treino.setEtapasRealizadas(etapas);
        return treino;
    }

    private TreinoRealizado criarTreinoSemEtapas(TipoTreino tipo) {
        Atleta atleta = Atleta.builder()
                .nome("Atleta Teste")
                .objetivo("Corrida")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .fcLimiar(FC_LIMIAR)
                .build();

        TreinoRealizado treino = new TreinoRealizado();
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(tipo);
        treino.setDuracaoMin(Duration.ofMinutes(60));
        treino.setAtleta(atleta);
        treino.setEtapasRealizadas(List.of());
        return treino;
    }

    private EtapaRealizada criarEtapa(int ordem, int fcMedia) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .duracao(Duration.ofMinutes(15))
                .fcMedia(fcMedia)
                .build();
    }
}
