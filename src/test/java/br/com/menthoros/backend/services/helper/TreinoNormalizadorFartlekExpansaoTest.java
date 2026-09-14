package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expansão de etapas comprimidas pelo LLM ("Nx (AccelMin + RecovMin)", "NxDist") —
 * {@link TreinoNormalizador#expandirEtapasAgregadas} (refactor-iaservice-decomposition, seção 3).
 */
@DisplayName("TreinoNormalizador — expansão de etapas agregadas")
class TreinoNormalizadorFartlekExpansaoTest {

    private TreinoNormalizador normalizador;
    private List<ZonaFC> zonasFC160;

    @BeforeEach
    void setUp() {
        normalizador = new TreinoNormalizador();
        zonasFC160 = List.of(
                new ZonaFC(1, "Recuperação", 120, 136),
                new ZonaFC(2, "Aeróbico",    136, 142),
                new ZonaFC(3, "Tempo",       142, 150),
                new ZonaFC(4, "Limiar",      150, 160),
                new ZonaFC(5, "VO2max",      160, 170)
        );
    }

    @Nested
    @DisplayName("expandirEtapasAgregadas")
    class ExpandirEtapasAgregadas {

        @Test
        @DisplayName("expande fartlek comprimido quando o LLM tipa a etapa como PRINCIPAL")
        void expandeFartlekTipadoComoPrincipal() {
            // Arrange — caso real relatado em 2026-08-17: o LLM tipou a etapa como PRINCIPAL,
            // não como INTERVALADO, e o "4x" ficou preso no texto da descrição.
            TreinoPlanejadoLlmDto treino = fartlek(etapa(
                    "PRINCIPAL", "Fartlek leve Z2-Z3, 4x (1min forte + 2min leve)", 12, 2.0));

            TreinoPlanejadoLlmDto resultado = normalizador.expandirEtapasAgregadas(treino, zonasFC160);

            // Assert — 4 pares aceleração/recuperação no lugar da etapa única
            assertThat(resultado.etapas()).hasSize(8);
            assertThat(resultado.etapas())
                    .extracting(EtapaTreinoLlmDto::tipoEtapa)
                    .containsExactly("INTERVALADO", "RECUPERACAO", "INTERVALADO", "RECUPERACAO",
                                     "INTERVALADO", "RECUPERACAO", "INTERVALADO", "RECUPERACAO");
            assertThat(resultado.etapas())
                    .extracting(EtapaTreinoLlmDto::duracaoMin)
                    .containsExactly(1, 2, 1, 2, 1, 2, 1, 2);
        }

        @Test
        @DisplayName("preserva etapa PRINCIPAL sem padrão de compressão na descrição")
        void preservaPrincipalContinua() {
            EtapaTreinoLlmDto original = etapa("PRINCIPAL", "Corrida contínua em Z2", 30, 5.0);
            TreinoPlanejadoLlmDto treino = fartlek(original);

            TreinoPlanejadoLlmDto resultado = normalizador.expandirEtapasAgregadas(treino, zonasFC160);

            // Assert — nada é criado, nada é reescrito
            assertThat(resultado.etapas()).hasSize(1);
            assertThat(resultado.etapas().getFirst()).isEqualTo(original);
        }

        @Test
        @DisplayName("não lê '2 min' como 2 metros quando a série é por tempo")
        void naoConfundeMinutoComMetro() {
            // Arrange — sem o lookahead (?!\s*min) no REPETICOES_PATTERN, o "m" de "min" casava a
            // unidade e a série virava 5 tiros de 2 metros pelo caminho NxDist.
            TreinoPlanejadoLlmDto treino = fartlek(etapa(
                    "PRINCIPAL", "Fartlek: 5 x 2 min forte + 2 min leve", 20, 3.0));

            TreinoPlanejadoLlmDto resultado = normalizador.expandirEtapasAgregadas(treino, zonasFC160);

            // Assert — caminho por tempo: 5 pares de 2min/2min
            assertThat(resultado.etapas()).hasSize(10);
            assertThat(resultado.etapas())
                    .extracting(EtapaTreinoLlmDto::duracaoMin)
                    .containsOnly(2);
        }

        @Test
        @DisplayName("mantém o caminho NxDist de INTERVALADO inalterado")
        void naoRegridePathNxDist() {
            TreinoPlanejadoLlmDto treino = fartlek(etapa("INTERVALADO", "6x400m Z5", 12, 2.4));

            TreinoPlanejadoLlmDto resultado = normalizador.expandirEtapasAgregadas(treino, zonasFC160);

            // Assert — 6 pares tiro/recuperação, distância unitária 400m lida da descrição
            assertThat(resultado.etapas()).hasSize(12);
            assertThat(resultado.etapas().getFirst().tipoEtapa()).isEqualTo("INTERVALADO");
            assertThat(resultado.etapas().getFirst().distanciaKm()).isEqualTo(0.4);
        }
    }

    // ---------- helpers ----------

    private EtapaTreinoLlmDto etapa(String tipo, String descricao, Integer duracaoMin, Double distanciaKm) {
        return new EtapaTreinoLlmDto(1, tipo, descricao, duracaoMin, distanciaKm, "136-150 bpm", 1, null);
    }

    private TreinoPlanejadoLlmDto fartlek(EtapaTreinoLlmDto... etapas) {
        return new TreinoPlanejadoLlmDto(
                "QUINTA", "FARTLEK", "121-133 bpm", 25, 0.9, 5,
                "Estimulação leve para manter a ativação muscular.",
                "25", 4.0, "6:20-6:40/km", List.of(etapas));
    }
}
