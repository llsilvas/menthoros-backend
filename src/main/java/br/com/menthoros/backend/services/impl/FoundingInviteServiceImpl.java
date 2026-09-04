package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.FoundingInviteLookupOutputDto;
import br.com.menthoros.backend.dto.output.FoundingInviteOutputDto;
import br.com.menthoros.backend.entity.FoundingInvite;
import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.repository.FoundingInviteRepository;
import br.com.menthoros.backend.repository.WaitlistRepository;
import br.com.menthoros.backend.security.InviteToken;
import br.com.menthoros.backend.services.FoundingInviteService;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Emissão e consulta do convite de assessoria fundadora.
 *
 * <p><strong>Deliberadamente SEM {@code @Transactional}</strong> em {@link #invite}: o envio do
 * e-mail é chamada externa e não pode segurar transação. A ordem das escritas torna a falha parcial
 * inofensiva — invalidar o anterior e só então inserir: se o insert falhar, o inscrito fica sem
 * convite aberto e o próximo reenvio conserta; se o e-mail falhar, o convite fica sem
 * {@code sentAt} e o próximo reenvio o invalida.</p>
 */
@Slf4j
@Service
public class FoundingInviteServiceImpl implements FoundingInviteService {

    /** Limite de {@code CoachSignupInputDto.email} e de {@code tb_usuario.email}. */
    static final int MAX_EMAIL_LENGTH_FOR_SIGNUP = 100;
    static final String SUBJECT = "Seu convite para a turma fundadora do Menthoros";
    static final String INVITE_PATH = "/#/cadastro?convite=";

    private final WaitlistRepository waitlistRepository;
    private final FoundingInviteRepository inviteRepository;
    private final KeycloakOrganizationGateway keycloak;
    private final EmailSender emailSender;
    private final EmailTemplateRenderer templates;
    private final Clock clock;
    private final String frontendUrl;
    private final int validityDays;

    public FoundingInviteServiceImpl(
            WaitlistRepository waitlistRepository,
            FoundingInviteRepository inviteRepository,
            KeycloakOrganizationGateway keycloak,
            EmailSender emailSender,
            EmailTemplateRenderer templates,
            Clock clock,
            @Value("${app.frontend.url}") String frontendUrl,
            @Value("${app.founding-invite.validity-days:7}") int validityDays) {
        this.waitlistRepository = waitlistRepository;
        this.inviteRepository = inviteRepository;
        this.keycloak = keycloak;
        this.emailSender = emailSender;
        this.templates = templates;
        this.clock = clock;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.validityDays = validityDays;
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Idempotent:</strong> NO — cada chamada gera token novo, invalida o anterior e envia
     * outro e-mail.
     * <p><strong>Side Effects:</strong> Database insert/update + External API (SMTP).
     * <p><strong>Tenant-aware:</strong> NO — roda antes de o tenant existir.
     */
    @Override
    public FoundingInviteOutputDto invite(UUID waitlistId, String invitedBy) {
        log.info("Emitindo convite de fundadora: waitlistId={}", waitlistId);

        Waitlist inscrito = waitlistRepository.findById(waitlistId)
                .orElseThrow(() -> new DomainNotFoundException("Inscrito não encontrado na waitlist"));
        validar(inscrito);

        OffsetDateTime now = OffsetDateTime.now(clock);
        inviteRepository.findOpenByWaitlistId(waitlistId).ifPresent(anterior -> {
            anterior.setInvalidatedAt(now);
            inviteRepository.save(anterior);
            log.info("Convite anterior invalidado: inviteId={}", anterior.getId());
        });

        InviteToken token = InviteToken.generate();
        FoundingInvite convite;
        try {
            convite = inviteRepository.save(FoundingInvite.builder()
                    .waitlistId(waitlistId)
                    .tokenHash(token.hash())
                    .email(inscrito.getEmail())
                    .expiresAt(now.plusDays(validityDays))
                    .invitedBy(invitedBy)
                    .build());
        } catch (DataIntegrityViolationException corrida) {
            // Dois convites simultâneos para o mesmo inscrito: entre o findOpen e o insert não há
            // lock, e a UNIQUE parcial decidiu. Quem perdeu recebe 409; nenhum e-mail saiu. Só
            // funciona porque a classe não é @Transactional (ver JavaDoc).
            throw new DomainConflictException("Já existe um convite sendo emitido para este inscrito; tente de novo");
        }

        // Fora de qualquer transação e depois do insert: se o SMTP recusar, o convite já existe sem
        // sentAt e a exceção sobe como 502 — o founder reenvia, e o reenvio invalida este.
        emailSender.send(mensagem(inscrito.getNome(), inscrito.getEmail(), token));

        convite.setSentAt(OffsetDateTime.now(clock));
        inviteRepository.save(convite);

        log.info("Convite de fundadora enviado: inviteId={}, waitlistId={}, expiresAt={}",
                convite.getId(), waitlistId, convite.getExpiresAt());
        return new FoundingInviteOutputDto(convite.getId(), waitlistId, convite.getExpiresAt());
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Idempotent:</strong> YES — leitura.
     * <p><strong>Side Effects:</strong> NONE.
     * <p><strong>Tenant-aware:</strong> NO.
     */
    @Override
    public FoundingInviteLookupOutputDto lookup(String rawToken) {
        FoundingInvite convite = findActive(rawToken)
                .orElseThrow(() -> new DomainNotFoundException("Convite inválido ou expirado"));
        Waitlist inscrito = waitlistRepository.findById(convite.getWaitlistId())
                .orElseThrow(() -> new DomainNotFoundException("Convite inválido ou expirado"));
        return new FoundingInviteLookupOutputDto(inscrito.getNome(), convite.getEmail());
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Idempotent:</strong> YES — leitura.
     * <p><strong>Side Effects:</strong> NONE.
     * <p><strong>Tenant-aware:</strong> NO.
     */
    @Override
    public Optional<FoundingInvite> findActive(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        return inviteRepository.findByTokenHash(InviteToken.hashOf(rawToken))
                .filter(convite -> convite.isActive(now));
    }

    private void validar(Waitlist inscrito) {
        if (inscrito.getPerfil() != PerfilWaitlist.TREINADOR) {
            throw new DomainRuleViolationException("Só inscritos com perfil TREINADOR podem ser convidados");
        }
        if (inscrito.getEmail().length() > MAX_EMAIL_LENGTH_FOR_SIGNUP) {
            // A waitlist aceita 180; o cadastro e tb_usuario, 100. Falhar aqui, na mão do founder,
            // e não na tela da fundadora.
            throw new DomainRuleViolationException(
                    "E-mail com mais de " + MAX_EMAIL_LENGTH_FOR_SIGNUP + " caracteres não cabe no cadastro");
        }
        if (inviteRepository.existsByWaitlistIdAndConvertedAtIsNotNull(inscrito.getId())) {
            throw new DomainConflictException("Este inscrito já converteu um convite");
        }
        if (keycloak.buscarUsuarioIdPorEmail(inscrito.getEmail()).isPresent()) {
            throw new DomainConflictException("Este e-mail já possui conta");
        }
    }

    private EmailMessage mensagem(String nome, String email, InviteToken token) {
        Map<String, String> valores = Map.of(
                "nome", nome,
                "link", frontendUrl + INVITE_PATH + token.value(),
                "validade", validityDays + (validityDays == 1 ? " dia" : " dias"));
        return new EmailMessage(email, SUBJECT,
                templates.render("founding-invite.html", valores),
                templates.render("founding-invite.txt", valores));
    }
}
