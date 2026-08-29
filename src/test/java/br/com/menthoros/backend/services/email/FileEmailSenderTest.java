package br.com.menthoros.backend.services.email;

import br.com.menthoros.backend.config.email.EmailProperties;
import br.com.menthoros.backend.exception.EmailDeliveryException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileEmailSender: grava .eml no outbox e nunca loga o corpo")
class FileEmailSenderTest {

    private static final String SEGREDO = "convite=TOKEN-SECRETO-XYZ";

    @TempDir
    Path outbox;

    private FileEmailSender sender;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        var props = new EmailProperties();
        props.setOutboxDir(outbox.resolve("emails").toString());
        sender = new FileEmailSender(props);

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(FileEmailSender.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(FileEmailSender.class)).detachAppender(logs);
    }

    private static EmailMessage mensagem() {
        return new EmailMessage("maria@exemplo.com", "Seu convite",
                "<p>Abra https://x/#/cadastro?" + SEGREDO + "</p>",
                "Abra https://x/#/cadastro?" + SEGREDO);
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("cria o diretório e grava um .eml multipart com texto e HTML")
        void gravaEml() throws IOException {
            sender.send(mensagem());

            Path arquivo = unicoArquivo();
            String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
            assertThat(arquivo.getFileName().toString()).endsWith(".eml");
            assertThat(conteudo)
                    .contains("To: maria@exemplo.com")
                    .contains("Subject: Seu convite")
                    .contains("multipart/alternative")
                    .contains("text/plain")
                    .contains("text/html")
                    .contains(SEGREDO);
        }

        @Test
        @DisplayName("loga só o caminho do arquivo — o token nunca aparece no log")
        void naoLogaOCorpo() {
            sender.send(mensagem());

            String tudo = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
            assertThat(tudo).contains(".eml").doesNotContain(SEGREDO).doesNotContain("maria@exemplo.com");
        }

        @Test
        @DisplayName("falha de escrita vira EmailDeliveryException")
        void falhaDeEscrita() throws IOException {
            // Um ARQUIVO no lugar do diretório do outbox: createDirectories falha.
            var props = new EmailProperties();
            Path bloqueio = outbox.resolve("bloqueio");
            Files.writeString(bloqueio, "x");
            props.setOutboxDir(bloqueio.toString());

            assertThatThrownBy(() -> new FileEmailSender(props).send(mensagem()))
                    .isInstanceOf(EmailDeliveryException.class);
        }
    }

    private Path unicoArquivo() throws IOException {
        try (Stream<Path> arquivos = Files.list(outbox.resolve("emails"))) {
            var lista = arquivos.toList();
            assertThat(lista).hasSize(1);
            return lista.get(0);
        }
    }
}
