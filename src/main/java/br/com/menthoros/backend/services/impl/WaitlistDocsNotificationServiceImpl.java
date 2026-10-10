package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.WaitlistDocsNotificationResultDto;
import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.repository.WaitlistRepository;
import br.com.menthoros.backend.services.WaitlistDocsNotificationService;
import br.com.menthoros.backend.services.email.EmailMessage;
import br.com.menthoros.backend.services.email.EmailSender;
import br.com.menthoros.backend.services.email.EmailTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Aviso em massa, por e-mail, aos treinadores da waitlist de que a central de ajuda está no ar.
 *
 * <p><strong>Deliberadamente SEM {@code @Transactional}</strong> no laço de {@link #notificar()}:
 * cada reivindicação ({@link WaitlistRepository#reivindicarAvisoDocs}) e cada liberação
 * ({@link WaitlistRepository#liberarAvisoDocs}) já são atômicas sozinhas (mesmo padrão de
 * {@code AthleteInviteRepository#claim}/{@code liberarClaim}). Envolver o laço inteiro numa
 * transação arriscaria reverter reivindicações já confirmadas — de inscritos cujo e-mail já
 * saiu — se uma exceção no meio do laço escapasse sem ser capturada.</p>
 */
@Slf4j
@Service
public class WaitlistDocsNotificationServiceImpl implements WaitlistDocsNotificationService {

    static final String SUBJECT = "A central de ajuda do Menthoros está no ar";

    private final WaitlistRepository waitlistRepository;
    private final EmailSender emailSender;
    private final EmailTemplateRenderer templates;
    private final Clock clock;
    private final String docsUrl;
    private final String frontendUrl;

    public WaitlistDocsNotificationServiceImpl(
            WaitlistRepository waitlistRepository,
            EmailSender emailSender,
            EmailTemplateRenderer templates,
            Clock clock,
            @Value("${app.docs.url}") String docsUrl,
            @Value("${app.frontend.url}") String frontendUrl) {
        this.waitlistRepository = waitlistRepository;
        this.emailSender = emailSender;
        this.templates = templates;
        this.clock = clock;
        this.docsUrl = docsUrl;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Idempotent:</strong> YES — só reenvia a quem ainda não tem
     * {@code docsNotifiedAt}; chamadas repetidas sem novos inscritos não enviam nada.
     * <p><strong>Side Effects:</strong> Database update (por inscrito) + External API (SMTP, por
     * inscrito).
     * <p><strong>Tenant-aware:</strong> NO — {@code Waitlist} é entidade global, pré-signup.
     */
    @Override
    public WaitlistDocsNotificationResultDto notificar() {
        List<Waitlist> elegiveis = waitlistRepository.findAllByPerfilInAndDocsNotifiedAtIsNull(
                List.of(PerfilWaitlist.TREINADOR, PerfilWaitlist.PROPRIETARIO));
        log.info("Disparando aviso da central de ajuda à waitlist: elegiveis={}", elegiveis.size());

        int enviados = 0;
        int falhas = 0;
        for (Waitlist inscrito : elegiveis) {
            Instant agora = Instant.now(clock);
            // Reivindicação atômica: se outra chamada concorrente já pegou esta linha
            // (rowsUpdated == 0), pula sem enviar de novo — resolve a corrida entre chamadas.
            if (waitlistRepository.reivindicarAvisoDocs(inscrito.getId(), agora) == 0) {
                continue;
            }
            try {
                emailSender.send(mensagem(inscrito));
                enviados++;
            } catch (RuntimeException e) {
                // Qualquer falha aqui (SMTP recusado, timeout, erro inesperado) não pode derrubar
                // o lote nem perder a contagem dos demais inscritos.
                log.warn("Falha ao notificar inscrito da waitlist sobre a central de ajuda: waitlistId={}",
                        inscrito.getId(), e);
                waitlistRepository.liberarAvisoDocs(inscrito.getId());
                falhas++;
            }
        }

        log.info("Aviso da central de ajuda à waitlist concluído: elegiveis={}, enviados={}, falhas={}",
                elegiveis.size(), enviados, falhas);
        return new WaitlistDocsNotificationResultDto(elegiveis.size(), enviados, falhas);
    }

    private EmailMessage mensagem(Waitlist inscrito) {
        Map<String, String> valores = Map.of(
                "nome", inscrito.getNome(),
                "docsUrl", docsUrl,
                // Logo do cabeçalho: mesmo host de assets do founding-invite.
                "assetsUrl", frontendUrl + "/email");
        return new EmailMessage(inscrito.getEmail(), SUBJECT,
                templates.render("waitlist-docs-site.html", valores),
                templates.render("waitlist-docs-site.txt", valores));
    }
}
