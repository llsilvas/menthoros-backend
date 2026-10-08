package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.FaixaAtletas;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.events.WaitlistLeadCreatedEvent;
import br.com.menthoros.backend.repository.WaitlistRepository;
import br.com.menthoros.backend.services.email.EmailMessage;
import br.com.menthoros.backend.services.email.EmailTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Reage a {@link WaitlistLeadCreatedEvent}: confirmação ao inscrito (por perfil) e, só para
 * {@code TREINADOR}, notificação ao founder. Cada envio em {@code try/catch} independente — a
 * falha de um não impede o outro, e nenhum propaga (a criação do lead já aconteceu antes do
 * evento; e-mail é melhor esforço, não parte da transação de negócio).
 *
 * <p>Ver design.md (D2) sobre {@code fallbackExecution = true}: {@code WaitlistServiceImpl.registrar}
 * não é {@code @Transactional}, então não há sincronização de transação ativa no momento da
 * publicação — sem o fallback, este listener nunca dispararia.</p>
 */
@Component
@Slf4j
public class WaitlistNotificationListener {

    private static final String SUBJECT_TREINADOR = "Recebemos sua solicitação — Menthoros";
    private static final String SUBJECT_ATLETA = "O Menthoros é para assessorias — Menthoros";
    private static final String SUBJECT_FOUNDER = "Novo lead qualificado na waitlist";

    private final WaitlistRepository waitlistRepository;
    private final WaitlistEmailSender waitlistEmailSender;
    private final EmailTemplateRenderer templates;
    private final String frontendUrl;
    private final String founderNotificationEmail;

    public WaitlistNotificationListener(
            WaitlistRepository waitlistRepository,
            WaitlistEmailSender waitlistEmailSender,
            EmailTemplateRenderer templates,
            @Value("${app.frontend.url}") String frontendUrl,
            @Value("${app.founder.notification-email:}") String founderNotificationEmail) {
        this.waitlistRepository = waitlistRepository;
        this.waitlistEmailSender = waitlistEmailSender;
        this.templates = templates;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.founderNotificationEmail = founderNotificationEmail;
    }

    /**
     * Idempotent: NO — reenviar o evento reenvia os e-mails.
     * Side Effects: External API call (e-mail ao lead e, para treinador, ao founder).
     * Tenant-aware: NO — {@code Waitlist} é entidade global, sem tenant.
     */
    @Async("waitlistNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoCriarLead(WaitlistLeadCreatedEvent event) {
        Waitlist lead = waitlistRepository.findById(event.waitlistId()).orElse(null);
        if (lead == null) {
            // Corrida improvável (lead apagado entre o commit e esta thread assíncrona) — não é
            // erro de envio, é ausência de destinatário.
            log.warn("Waitlist: lead {} não encontrado para notificação", event.waitlistId());
            return;
        }

        enviarConfirmacao(lead);
        if (lead.getPerfil() == PerfilWaitlist.TREINADOR) {
            notificarFounder(lead);
        }
    }

    private void enviarConfirmacao(Waitlist lead) {
        try {
            EmailMessage mensagem = lead.getPerfil() == PerfilWaitlist.TREINADOR
                    ? mensagemTreinador(lead)
                    : mensagemAtleta(lead);
            waitlistEmailSender.enviar(mensagem);
        } catch (Exception e) {
            log.error("Waitlist: falha ao enviar confirmação ao lead {}", lead.getId(), e);
        }
    }

    private void notificarFounder(Waitlist lead) {
        if (founderNotificationEmail == null || founderNotificationEmail.isBlank()) {
            log.warn("Waitlist: app.founder.notification-email não configurado — notificação de lead {} não enviada", lead.getId());
            return;
        }
        try {
            waitlistEmailSender.enviar(mensagemFounder(lead));
        } catch (Exception e) {
            log.error("Waitlist: falha ao notificar founder sobre o lead {}", lead.getId(), e);
        }
    }

    private EmailMessage mensagemTreinador(Waitlist lead) {
        Map<String, String> valores = valoresBase(lead);
        return new EmailMessage(lead.getEmail(), SUBJECT_TREINADOR,
                templates.render("waitlist-confirmation-treinador.html", valores),
                templates.render("waitlist-confirmation-treinador.txt", valores));
    }

    private EmailMessage mensagemAtleta(Waitlist lead) {
        Map<String, String> valores = valoresBase(lead);
        valores.put("linkWaitlist", frontendUrl + "/#/waitlist");
        return new EmailMessage(lead.getEmail(), SUBJECT_ATLETA,
                templates.render("waitlist-confirmation-atleta.html", valores),
                templates.render("waitlist-confirmation-atleta.txt", valores));
    }

    private EmailMessage mensagemFounder(Waitlist lead) {
        Map<String, String> valores = new HashMap<>();
        valores.put("nome", lead.getNome());
        valores.put("email", lead.getEmail());
        valores.put("faixaAtletas", faixaLegivel(lead.getQtdAtletas()));
        valores.put("telefone", lead.getTelefone() == null || lead.getTelefone().isBlank() ? "não informado" : lead.getTelefone());
        valores.put("origem", origemLegivel(lead));
        return new EmailMessage(founderNotificationEmail, SUBJECT_FOUNDER,
                templates.render("waitlist-founder-notification.html", valores),
                templates.render("waitlist-founder-notification.txt", valores));
    }

    private Map<String, String> valoresBase(Waitlist lead) {
        Map<String, String> valores = new HashMap<>();
        valores.put("nome", lead.getNome());
        // Logo do cabeçalho: mesmo host de assets do founding-invite/waitlist-docs-site.
        valores.put("assetsUrl", frontendUrl + "/email");
        return valores;
    }

    private String faixaLegivel(FaixaAtletas faixa) {
        if (faixa == null) {
            return "não informado";
        }
        return switch (faixa) {
            case ATE_10 -> "até 10 atletas";
            case DE_11_A_30 -> "11 a 30 atletas";
            case DE_31_A_100 -> "31 a 100 atletas";
            case MAIS_DE_100 -> "mais de 100 atletas";
        };
    }

    private String origemLegivel(Waitlist lead) {
        if (lead.getUtmSource() == null) {
            return "direto (sem UTM)";
        }
        StringBuilder sb = new StringBuilder(lead.getUtmSource());
        if (lead.getUtmMedium() != null) {
            sb.append(" / ").append(lead.getUtmMedium());
        }
        if (lead.getUtmCampaign() != null) {
            sb.append(" / ").append(lead.getUtmCampaign());
        }
        return sb.toString();
    }
}
