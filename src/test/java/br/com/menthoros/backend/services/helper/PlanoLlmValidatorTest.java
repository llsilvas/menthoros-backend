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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de {@link PlanoLlmValidator#validarEstrutura3Etapas} — extraído de {@code IaServiceImpl}
 * (refactor-iaservice-decomposition, seção 5). Chamada direta ao colaborador, sem reflexão.
 */
@DisplayName("PlanoLlmValidator — validarEstrutura3Etapas")
class PlanoLlmValidatorTest {

    private PlanoLlmValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PlanoLlmValidator(new SimpleMeterRegistry(), new PaceValidator());
    }

    @Nested
    @DisplayName("validarEstrutura3Etapas (caracterização do hard-fail atual)")
    class Estrutura3Etapas {

        @Test
        @DisplayName("≠ 3 etapas → LLMException (hoje derruba o plano inteiro)")
        void numeroEtapasErrado() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL")); // 2 etapas
            assertThatThrownBy(() -> validator.validarEstrutura3Etapas(treino, "REGENERATIVO", "atleta", true))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("3 etapas na ordem canônica → ok")
        void ordemCanonica() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("PRINCIPAL"), etapa("DESAQUECIMENTO"));
            assertThatNoException().isThrownBy(
                    () -> validator.validarEstrutura3Etapas(treino, "REGENERATIVO", "atleta", true));
        }

        @Test
        @DisplayName("fora de ordem com validarOrdem=true → LLMException")
        void ordemTrocada() {
            var treino = treino("REGENERATIVO", etapa("PRINCIPAL"), etapa("AQUECIMENTO"), etapa("DESAQUECIMENTO"));
            assertThatThrownBy(() -> validator.validarEstrutura3Etapas(treino, "REGENERATIVO", "atleta", true))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("LONGO (validarOrdem=false) ignora só a posição de aquec/desaq — meio continua PRINCIPAL")
        void longoIgnoraOrdemDeAquecDesaqMasExigeMeioPrincipal() {
            // validarOrdem=false pula a checagem de posição 0/2 (AQUECIMENTO/DESAQUECIMENTO
            // trocados), mas a etapa central segue tendo que ser PRINCIPAL (fix IA-04).
            var treino = treino("LONGO", etapa("DESAQUECIMENTO"), etapa("PRINCIPAL"), etapa("AQUECIMENTO"));
            assertThatNoException().isThrownBy(
                    () -> validator.validarEstrutura3Etapas(treino, "LONGO", "atleta", false));
        }

        @Test
        @DisplayName("IA-04: etapa central que não é PRINCIPAL → LLMException (validarOrdem=true)")
        void etapaCentralNaoPrincipal_validarOrdemTrue_lancaExcecao() {
            var treino = treino("REGENERATIVO", etapa("AQUECIMENTO"), etapa("RECUPERACAO"), etapa("DESAQUECIMENTO"));
            assertThatThrownBy(() -> validator.validarEstrutura3Etapas(treino, "REGENERATIVO", "atleta", true))
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("IA-04 (achado do Codex): etapa central que não é PRINCIPAL → LLMException também em LONGO (validarOrdem=false)")
        void etapaCentralNaoPrincipal_validarOrdemFalse_lancaExcecao() {
            var treino = treino("LONGO", etapa("AQUECIMENTO"), etapa("RECUPERACAO"), etapa("DESAQUECIMENTO"));
            assertThatThrownBy(() -> validator.validarEstrutura3Etapas(treino, "LONGO", "atleta", false))
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
