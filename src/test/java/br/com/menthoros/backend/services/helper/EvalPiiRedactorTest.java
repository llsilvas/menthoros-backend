package br.com.menthoros.backend.services.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvalPiiRedactor")
class EvalPiiRedactorTest {

    private final EvalPiiRedactor redactor = new EvalPiiRedactor();

    @Nested
    @DisplayName("redigir")
    class Redigir {

        @Test
        @DisplayName("redige nome completo do atleta")
        void redigeNome() {
            var alvo = new EvalPiiRedactor.PiiAlvo("Maria Souza", null, null, null, null);
            String texto = "Plano para Maria Souza esta semana.";

            assertThat(redactor.redigir(texto, alvo)).isEqualTo("Plano para [ATLETA] esta semana.");
        }

        @Test
        @DisplayName("redige idade exata quando aparece junto de 'anos'")
        void redigeIdade() {
            var alvo = new EvalPiiRedactor.PiiAlvo(null, 32, null, null, null);
            String texto = "Atleta de 32 anos, iniciante.";

            assertThat(redactor.redigir(texto, alvo)).isEqualTo("Atleta de [REDIGIDO], iniciante.");
        }

        @Test
        @DisplayName("não redige número que não é a idade informada")
        void naoRedigeNumeroDiferente() {
            var alvo = new EvalPiiRedactor.PiiAlvo(null, 32, null, null, null);
            String texto = "Treina há 10 anos, tem 32 anos.";

            assertThat(redactor.redigir(texto, alvo)).isEqualTo("Treina há 10 anos, tem [REDIGIDO].");
        }

        @Test
        @DisplayName("redige nome de prova")
        void redigeNomeProva() {
            var alvo = new EvalPiiRedactor.PiiAlvo(null, null, "Maratona de São Paulo", null, null);
            String texto = "Preparação para Maratona de São Paulo em outubro.";

            assertThat(redactor.redigir(texto, alvo))
                    .isEqualTo("Preparação para [REDIGIDO] em outubro.");
        }

        @Test
        @DisplayName("redige cidade e clube")
        void redigeCidadeEClube() {
            var alvo = new EvalPiiRedactor.PiiAlvo(null, null, null, "Curitiba", "Clube Atlético");
            String texto = "Atleta de Curitiba, treina no Clube Atlético.";

            assertThat(redactor.redigir(texto, alvo))
                    .isEqualTo("Atleta de [REDIGIDO], treina no [REDIGIDO].");
        }

        @Test
        @DisplayName("redige todos os campos combinados no mesmo texto")
        void redigeTodosOsCampos() {
            var alvo = new EvalPiiRedactor.PiiAlvo("João Lima", 45, "Corrida de Rua de Curitiba",
                    "Curitiba", "Clube Atlético");
            String texto = "João Lima, 45 anos, mora em Curitiba, treina no Clube Atlético e mira a "
                    + "Corrida de Rua de Curitiba.";

            String resultado = redactor.redigir(texto, alvo);

            assertThat(resultado).doesNotContain("João Lima", "45 anos", "Curitiba", "Clube Atlético",
                    "Corrida de Rua de Curitiba");
        }

        @Test
        @DisplayName("texto null retorna null, sem lançar")
        void textoNuloRetornaNulo() {
            var alvo = new EvalPiiRedactor.PiiAlvo("Maria", null, null, null, null);

            assertThat(redactor.redigir(null, alvo)).isNull();
        }

        @Test
        @DisplayName("texto em branco retorna como está")
        void textoEmBrancoRetornaComoEsta() {
            var alvo = new EvalPiiRedactor.PiiAlvo("Maria", null, null, null, null);

            assertThat(redactor.redigir("   ", alvo)).isEqualTo("   ");
        }

        @Test
        @DisplayName("alvo nulo retorna o texto original, sem lançar")
        void alvoNuloRetornaTextoOriginal() {
            String texto = "Plano para Maria Souza.";

            assertThat(redactor.redigir(texto, null)).isEqualTo(texto);
        }

        @Test
        @DisplayName("alvo com todos os campos nulos não altera o texto")
        void alvoComCamposNulosNaoAlteraTexto() {
            var alvo = new EvalPiiRedactor.PiiAlvo(null, null, null, null, null);
            String texto = "Plano para Maria Souza, 45 anos, em Curitiba.";

            assertThat(redactor.redigir(texto, alvo)).isEqualTo(texto);
        }
    }
}
