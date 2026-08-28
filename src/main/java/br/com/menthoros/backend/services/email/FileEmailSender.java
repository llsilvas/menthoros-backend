package br.com.menthoros.backend.services.email;

import br.com.menthoros.backend.config.email.EmailProperties;
import br.com.menthoros.backend.exception.EmailDeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Carteiro de desenvolvimento: grava cada mensagem como {@code .eml} em disco.
 *
 * <p>Em arquivo, <strong>nunca em log</strong>: o corpo pode carregar um link com token de convite,
 * e log agrega, é enviado a ferramentas externas e não tem dono.  * {@code local}/{@code test}/{@code integration} — na nuvem não há fallback, e a ausência de SMTP falha o startup.</p>
 */
@Slf4j
@Service
@Profile("!cloud & !dev")
public class FileEmailSender implements EmailSender {

    private static final DateTimeFormatter CARIMBO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path outbox;
    private final EmailProperties properties;

    public FileEmailSender(EmailProperties properties) {
        this.properties = properties;
        this.outbox = Path.of(properties.getOutboxDir());
    }

    @Override
    public void send(EmailMessage message) {
        Path arquivo = outbox.resolve(CARIMBO.format(OffsetDateTime.now()) + "-" + UUID.randomUUID() + ".eml");
        try {
            Files.createDirectories(outbox);
            Files.writeString(arquivo, eml(message), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new EmailDeliveryException("Falha ao gravar e-mail no outbox local (" + arquivo + ")", e);
        }
        // Só o caminho: quem quiser o link abre o arquivo.
        log.info("E-mail gravado no outbox local: arquivo={}", arquivo);
    }

    /** Multipart/alternative mínimo — abre em qualquer cliente de e-mail. */
    private String eml(EmailMessage message) {
        String boundary = "=_menthoros_" + UUID.randomUUID();
        return """
                From: %s <%s>
                To: %s
                Subject: %s
                MIME-Version: 1.0
                Content-Type: multipart/alternative; boundary="%s"

                --%s
                Content-Type: text/plain; charset=UTF-8

                %s

                --%s
                Content-Type: text/html; charset=UTF-8

                %s

                --%s--
                """.formatted(properties.getFromName(), properties.getFrom(), message.to(), message.subject(),
                boundary, boundary, message.text(), boundary, message.html(), boundary);
    }
}
