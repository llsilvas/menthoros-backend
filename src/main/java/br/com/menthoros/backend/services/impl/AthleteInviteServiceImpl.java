package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.AthleteInvite;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AthleteInviteRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.security.InviteToken;
import br.com.menthoros.backend.services.AthleteInviteService;
import br.com.menthoros.backend.services.email.EmailMessage;
import br.com.menthoros.backend.services.email.EmailSender;
import br.com.menthoros.backend.services.email.EmailTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Emissão do convite de atleta por token do backend.
 *
 * <p><strong>Deliberadamente SEM {@code @Transactional}</strong> em {@link #invite}: o envio do
 * e-mail é chamada externa e não pode segurar transação (mesma decisão do
 * {@link FoundingInviteServiceImpl}). A ordem das escritas torna a falha parcial inofensiva:
 * invalidar o anterior e só então inserir; se o e-mail falhar, o convite fica sem {@code sentAt}
 * e o próximo reenvio o invalida.</p>
 */
@Slf4j
@Service
public class AthleteInviteServiceImpl implements AthleteInviteService {

    static final String SUBJECT = "Seu treinador te convidou para o Menthoros";
    static final String INVITE_PATH = "/#/cadastro?convite=";

    private final AtletaRepository atletaRepository;
    private final AthleteInviteRepository inviteRepository;
    private final EmailSender emailSender;
    private final EmailTemplateRenderer templates;
    private final Clock clock;
    private final String frontendUrl;
    private final int validityDays;

    public AthleteInviteServiceImpl(
            AtletaRepository atletaRepository,
            AthleteInviteRepository inviteRepository,
            EmailSender emailSender,
            EmailTemplateRenderer templates,
            Clock clock,
            @Value("${app.frontend.url}") String frontendUrl,
            @Value("${app.athlete-invite.validity-days:7}") int validityDays) {
        this.atletaRepository = atletaRepository;
        this.inviteRepository = inviteRepository;
        this.emailSender = emailSender;
        this.templates = templates;
        this.clock = clock;
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.validityDays = validityDays;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invite(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("Emitindo convite de atleta: atletaId={}, tenantId={}", atletaId, tenantId);

        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado: " + atletaId));
        if (atleta.getEmail() == null || atleta.getEmail().isBlank()) {
            throw new DomainRuleViolationException("Atleta sem email não pode ser convidado");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        inviteRepository.findOpenByAtletaId(atletaId).ifPresent(anterior -> {
            anterior.setInvalidatedAt(now);
            inviteRepository.save(anterior);
            log.info("Convite de atleta anterior invalidado: inviteId={}", anterior.getId());
        });

        InviteToken token = InviteToken.generate();
        AthleteInvite convite;
        try {
            convite = inviteRepository.save(AthleteInvite.builder()
                    .atletaId(atletaId)
                    .tenantId(tenantId)
                    .tokenHash(token.hash())
                    .emailEnviado(atleta.getEmail())
                    .expiresAt(now.plusDays(validityDays))
                    .build());
        } catch (DataIntegrityViolationException corrida) {
            // Duas emissões simultâneas: entre o findOpen e o insert não há lock, e a UNIQUE
            // parcial decidiu. Quem perdeu recebe 409; nenhum e-mail saiu. Só funciona porque a
            // classe não é @Transactional (ver JavaDoc da classe).
            throw new DomainConflictException("Já existe um convite sendo emitido para este atleta; tente de novo");
        }

        // Fora de transação e depois do insert: se o SMTP recusar, o convite existe sem sentAt e a
        // exceção sobe como 502 — o coach reenvia, e o reenvio invalida este.
        emailSender.send(mensagem(atleta, token));

        convite.setSentAt(OffsetDateTime.now(clock));
        inviteRepository.save(convite);

        log.info("Convite de atleta enviado: inviteId={}, atletaId={}, expiresAt={}",
                convite.getId(), atletaId, convite.getExpiresAt());
    }

    private EmailMessage mensagem(Atleta atleta, InviteToken token) {
        Map<String, String> valores = Map.of(
                "nome", atleta.getNome(),
                "assessoria", atleta.getAssessoria().getNome(),
                "link", frontendUrl + INVITE_PATH + token.value(),
                "validade", validityDays + (validityDays == 1 ? " dia" : " dias"));
        return new EmailMessage(atleta.getEmail(), SUBJECT,
                templates.render("athlete-invite.html", valores),
                templates.render("athlete-invite.txt", valores));
    }
}
