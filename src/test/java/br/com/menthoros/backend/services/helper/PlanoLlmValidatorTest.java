package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.exception.LLMException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de {@code validarEstrutura3Etapas} — a regra vive em {@link NormalizacaoDeTreino} desde
 * pipeline-normalizacao-treino (seção 2); a seção 3 migra estes casos para a interface
 * {@code normalizar} (família TRES_ETAPAS, passo {@code validar-por-tipo}). Os cenários de ordem
 * (padding/duração) que estavam aqui migraram para {@code NormalizacaoDeTreinoTest}.
 */
@DisplayName("validarEstrutura3Etapas")
class PlanoLlmValidatorTest {

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
    @DisplayName("validarEstrutura3Etapas (caracterização do hard-fail atual)")
    class Estrutura3Etapas {

        @Test
        @DisplayName("≠ 3 etapas → LLMException (hoje derruba o plano inteiro)")
        void numeroEtapasErrado() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL")); // 2 etapas
            assertThatThrownBy(() -> normalizacao.validarEstrutura3Etapas(treino, "REGENERATIVO", UUID.randomUUID(), true))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("3 etapas na ordem canônica → ok")
        void ordemCanonica() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL"), etapa("DESAQUECIMENTO"));
            assertThatNoException().isThrownBy(
                    () -> normalizacao.validarEstrutura3Etapas(treino, "REGENERATIVO", UUID.randomUUID(), true));
        }

        @Test
        @DisplayName("fora de ordem com validarOrdem=true → LLMException")
        void ordemTrocada() {
            var treino = treino("REGENERATIVO", etapa("PRINCIPAL"), etapa("AQUECIMENTO"), etapa("DESAQUECIMENTO"));
            assertThatThrownBy(() -> normalizacao.validarEstrutura3Etapas(treino, "REGENERATIVO", UUID.randomUUID(), true))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("LONGO (validarOrdem=false) ignora só a posição de aquec/desaq — meio continua PRINCIPAL")
        void longoIgnoraOrdemDeAquecDesaqMasExigeMeioPrincipal() {
            var treino = treino("LONGO", etapa("DESAQUECIMENTO"), etapa("PRINCIPAL"), etapa("AQUECIMENTO"));
            assertThatNoException().isThrownBy(
                    () -> normalizacao.validarEstrutura3Etapas(treino, "LONGO", UUID.randomUUID(), false));
        }

        @Test
        @DisplayName("IA-04: etapa central que não é PRINCIPAL → LLMException (validarOrdem=true)")
        void etapaCentralNaoPrincipal_validarOrdemTrue_lancaExcecao() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("RECUPERACAO"), etapa("DESAQUECIMENTO"));
            assertThatThrownBy(() -> normalizacao.validarEstrutura3Etapas(treino, "REGENERATIVO", UUID.randomUUID(), true))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("IA-04 (achado do Codex): etapa central que não é PRINCIPAL → LLMException também em LONGO (validarOrdem=false)")
        void etapaCentralNaoPrincipal_validarOrdemFalse_lancaExcecao() {
            var treino = treino("LONGO", etapa("AQUECIMENTO"), etapa("RECUPERACAO"), etapa("DESAQUECIMENTO"));
            assertThatThrownBy(() -> normalizacao.validarEstrutura3Etapas(treino, "LONGO", UUID.randomUUID(), false))
                    .isInstanceOf(LLMException.class);
        }

        private EtapaTreinoLlmDto etapa(String tipo) {
            return new EtapaTreinoLlmDto(1, tipo, "x", 10, 1.0, null, 1, null);
        }

        private TreinoPlanejadoLlmDto treino(String tipo, EtapaTreinoLlmDto... etapas) {
            return new TreinoPlanejadoLlmDto("SEGUNDA", tipo, null, null, null, null, null, null, null, null, List.of(etapas));
        }
    }
}
