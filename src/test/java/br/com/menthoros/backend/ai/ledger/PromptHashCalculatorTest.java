package br.com.menthoros.backend.ai.ledger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O hash do template estático viaja em cada Chamada LLM da rota plano (add-plan-generation-ledger,
 * D9). O arquivo {@code golden/plano-prompt/prompt.sha256} é regenerado junto do golden-master:
 * se o template mudar sem bump de {@link br.com.menthoros.backend.domain.compliance.PromptVersion},
 * {@link ArquivoDoGolden#bateComOClasspath} é o teste que falha.
 */
class PromptHashCalculatorTest {

    private static final String TEMPLATE_PLANO = "plano-treino-system.txt";

    @Nested
    @DisplayName("sha256")
    class Sha256 {

        @Test
        @DisplayName("é determinístico e usa o texto em UTF-8")
        void deterministico() {
            // Vetor conhecido: sha256("abc")
            assertThat(PromptHashCalculator.sha256("abc"))
                    .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
            assertThat(PromptHashCalculator.sha256("ação")).isEqualTo(PromptHashCalculator.sha256("ação"));
        }
    }

    @Nested
    @DisplayName("valor")
    class Valor {

        @Test
        @DisplayName("é calculado uma vez a partir do template do classpath")
        void calculadoDoClasspath() throws IOException {
            var calculator = new PromptHashCalculator(new DefaultResourceLoader(), TEMPLATE_PLANO);

            String conteudo = new ClassPathResource("prompts/" + TEMPLATE_PLANO)
                    .getContentAsString(StandardCharsets.UTF_8);
            assertThat(calculator.valor()).isEqualTo(PromptHashCalculator.sha256(conteudo));
            assertThat(calculator.valor()).hasSize(64).isSameAs(calculator.valor());
        }

        @Test
        @DisplayName("template inexistente falha no startup, não na primeira chamada")
        void templateInexistenteFalhaCedo() {
            assertThatThrownBy(() -> new PromptHashCalculator(new DefaultResourceLoader(), "nao-existe.txt"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nao-existe.txt");
        }
    }

    @Nested
    @DisplayName("arquivo do golden")
    class ArquivoDoGolden {

        @Test
        @DisplayName("prompt.sha256 bate com o template do classpath — mudou o template, regenere o golden e suba a versão")
        void bateComOClasspath() throws IOException {
            String registrado = Files.readString(Path.of("src/test/resources/golden/plano-prompt/prompt.sha256")).trim();
            var calculator = new PromptHashCalculator(new DefaultResourceLoader(), TEMPLATE_PLANO);

            assertThat(calculator.valor())
                    .as("template estático alterado sem regenerar golden/plano-prompt/prompt.sha256 e sem bump de PromptVersion")
                    .isEqualTo(registrado);
        }
    }

    @Nested
    @DisplayName("schema v2 (semantic-session-schema)")
    class SchemaV2 {

        private static final String TEMPLATE_PLANO_V2 = "plano-treino-system-v2.txt";

        @Test
        @DisplayName("template v2 existe no classpath e carrega — sem afetar o hash de v1")
        void templateV2CarregaSemAfetarV1() throws IOException {
            var calculatorV1 = new PromptHashCalculator(new DefaultResourceLoader(), TEMPLATE_PLANO);
            var calculatorV2 = new PromptHashCalculator(new DefaultResourceLoader(), TEMPLATE_PLANO_V2);

            String conteudoV2 = new ClassPathResource("prompts/" + TEMPLATE_PLANO_V2)
                    .getContentAsString(StandardCharsets.UTF_8);
            assertThat(calculatorV2.valor()).isEqualTo(PromptHashCalculator.sha256(conteudoV2));
            assertThat(calculatorV2.valor()).hasSize(64);
            // Templates diferentes → hashes diferentes; carregar v2 não recalcula nem muda v1.
            assertThat(calculatorV2.valor()).isNotEqualTo(calculatorV1.valor());
        }

        @Test
        @DisplayName("template v2 descreve o schema de blocos e não pede etapas expandidas")
        void templateV2DescreveSchemaDeBlocos() throws IOException {
            String conteudoV2 = new ClassPathResource("prompts/" + TEMPLATE_PLANO_V2)
                    .getContentAsString(StandardCharsets.UTF_8);

            assertThat(conteudoV2)
                    .contains("`blocos`", "`papel`", "`repeticoes`", "`quantidadePorRepeticao`", "`zona`")
                    // v1 pedia a LLM expandir etapa por etapa com fórmula "3 + 2×tiros" — não existe em v2
                    .doesNotContain("2 × número_de_tiros", "2 × número_de_variações", "ESTRUTURA OBRIGATÓRIA DO TREINO INTERVALADO");
        }
    }
}
