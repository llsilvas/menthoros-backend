package br.com.menthoros.backend.services.email;

import br.com.menthoros.backend.config.email.EmailProperties;
import br.com.menthoros.backend.exception.EmailDeliveryException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmtpEmailSender: monta o MIME com remetente configurado e traduz falhas")
class SmtpEmailSenderTest {

    @Mock private JavaMailSender mailSender;

    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        var props = new EmailProperties();
        props.setFrom("nao-responda@menthoros.com");
        props.setFromName("Menthoros");
        sender = new SmtpEmailSender(mailSender, props);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    private static EmailMessage mensagem() {
        return new EmailMessage("maria@exemplo.com", "Seu convite", "<p>oi</p>", "oi");
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("remetente, destinatário e assunto vêm da mensagem e das properties")
        void montaOMime() throws Exception {
            sender.send(mensagem());

            var captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());
            MimeMessage mime = captor.getValue();
            assertThat(((InternetAddress) mime.getFrom()[0]).getAddress()).isEqualTo("nao-responda@menthoros.com");
            assertThat(((InternetAddress) mime.getFrom()[0]).getPersonal()).isEqualTo("Menthoros");
            assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("maria@exemplo.com");
            assertThat(mime.getSubject()).isEqualTo("Seu convite");
        }

        @Test
        @DisplayName("recusa do SMTP vira EmailDeliveryException com o assunto, sem o corpo")
        void traduzFalha() {
            doThrow(new MailSendException("recusado")).when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() -> sender.send(mensagem()))
                    .isInstanceOf(EmailDeliveryException.class)
                    .hasMessageContaining("Seu convite")
                    .hasMessageNotContaining("<p>oi</p>");
        }
    }
}
