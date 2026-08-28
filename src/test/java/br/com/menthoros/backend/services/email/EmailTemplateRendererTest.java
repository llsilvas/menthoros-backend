package br.com.menthoros.backend.services.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmailTemplateRenderer: placeholders, escape e templates do convite")
class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    private static Map<String, String> valores() {
        return Map.of(
                "nome", "Maria",
                "link", "https://app.menthoros.com/#/cadastro?convite=abc_DEF-123",
                "validade", "7 dias");
    }

    @Nested
    @DisplayName("render")
    class Render {

        @Test
        @DisplayName("HTML do convite: nenhum placeholder sobra e o link aparece duas vezes (botão e texto)")
        void htmlCompleto() {
            String html = renderer.render("founding-invite.html", valores());

            assertThat(html)
                    .doesNotContain("{{")
                    .contains("Maria")
                    .contains("7 dias");
            assertThat(html.split("convite=abc_DEF-123", -1)).hasSize(3);
        }

        @Test
        @DisplayName("texto puro do convite: nenhum placeholder sobra e o link está inteiro")
        void textoCompleto() {
            String texto = renderer.render("founding-invite.txt", valores());

            assertThat(texto)
                    .doesNotContain("{{")
                    .contains("https://app.menthoros.com/#/cadastro?convite=abc_DEF-123")
                    .contains("Maria");
        }

        @Test
        @DisplayName("no HTML o nome é escapado — um inscrito com <script> no nome não vira injeção")
        void escapaHtml() {
            var valores = Map.of("nome", "<script>alert(1)</script>", "link", "https://x", "validade", "7 dias");

            String html = renderer.render("founding-invite.html", valores);

            assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
        }

        @Test
        @DisplayName("no texto puro o valor vai cru — não há HTML para escapar")
        void textoNaoEscapa() {
            var valores = Map.of("nome", "Ana & Cia", "link", "https://x?a=1&b=2", "validade", "7 dias");

            String texto = renderer.render("founding-invite.txt", valores);

            assertThat(texto).contains("Ana & Cia").contains("https://x?a=1&b=2");
        }

        @Test
        @DisplayName("placeholder sem valor é erro, não string vazia")
        void placeholderSemValor() {
            assertThatThrownBy(() -> renderer.render("founding-invite.html", Map.of("nome", "Maria")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("link");
        }

        @Test
        @DisplayName("template inexistente falha alto")
        void templateInexistente() {
            assertThatThrownBy(() -> renderer.render("nao-existe.html", valores()))
                    .isInstanceOf(UncheckedIOException.class);
        }
    }
}
