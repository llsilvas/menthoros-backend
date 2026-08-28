package br.com.menthoros.backend.services.email;

import br.com.menthoros.backend.config.email.EmailProperties;
import br.com.menthoros.backend.exception.EmailDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Envio real por SMTP (Resend no Railway, porta 2587/STARTTLS — 465 e 587 são bloqueadas lá).
 *
 * <p>Único {@link EmailSender} fora de {@code local}/{@code test}/{@code integration}. Sem {@code spring.mail.host}
 * o Spring não cria o {@link JavaMailSender}, este bean não sobe e o contexto falha — de
 * propósito: degradar para arquivo ou log na nuvem colocaria links com segredo em lugar errado.</p>
 */
@Slf4j
@Service
@Profile("!local & !test & !integration")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getFrom(), properties.getFromName());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mime);
            log.info("E-mail enviado por SMTP: subject={}", message.subject());
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            // Nem o corpo nem o destinatário completo: só o assunto identifica a mensagem no log.
            throw new EmailDeliveryException(
                    "Falha ao enviar e-mail por SMTP (subject=" + message.subject() + ")", e);
        }
    }
}
