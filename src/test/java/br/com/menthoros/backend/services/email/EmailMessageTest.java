package br.com.menthoros.backend.services.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmailMessage: campos obrigatórios, header injection e sigilo do corpo")
class EmailMessageTest {

    @ParameterizedTest(name = "destinatário com {0} é recusado")
    @ValueSource(strings = {"a@x.io\r\nBcc: b@y.io", "a@x.io\nBcc: b@y.io", "a@x.io\r"})
    void recusaQuebraDeLinhaNoDestinatario(String to) {
        assertThatThrownBy(() -> new EmailMessage(to, "Assunto", "<p>x</p>", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quebra de linha");
    }

    @Test
    @DisplayName("assunto com CR/LF é recusado — seria uma segunda linha de cabeçalho")
    void recusaQuebraDeLinhaNoAssunto() {
        assertThatThrownBy(() -> new EmailMessage("a@x.io", "Oi\r\nX-Injected: 1", "<p>x</p>", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("quebra de linha no corpo é normal")
    void corpoPodeTerQuebraDeLinha() {
        var m = new EmailMessage("a@x.io", "Assunto", "<p>x</p>\n<p>y</p>", "x\ny");

        assertThat(m.text()).contains("\n");
    }

    @Test
    @DisplayName("toString mascara o corpo")
    void toStringMascarado() {
        var m = new EmailMessage("a@x.io", "Assunto", "<p>segredo-html</p>", "segredo-texto");

        assertThat(m.toString()).contains("a@x.io").doesNotContain("segredo");
    }
}
