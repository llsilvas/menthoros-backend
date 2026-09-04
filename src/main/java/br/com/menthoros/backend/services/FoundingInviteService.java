package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.FoundingInviteLookupOutputDto;
import br.com.menthoros.backend.dto.output.FoundingInviteOutputDto;
import br.com.menthoros.backend.entity.FoundingInvite;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.EmailDeliveryException;

import java.util.Optional;
import java.util.UUID;

/** Convite das assessorias fundadoras: emissão pelo ADMIN e consulta pública pelo token. */
public interface FoundingInviteService {

    /**
     * Emite (ou reemite) o convite de um inscrito da waitlist e envia o e-mail.
     *
     * @param waitlistId inscrito
     * @param invitedBy  subject do ADMIN que emitiu
     * @throws DomainNotFoundException      inscrito inexistente
     * @throws DomainRuleViolationException inscrito não é treinador, ou e-mail maior que o signup aceita
     * @throws DomainConflictException      e-mail já tem conta no Keycloak, ou convite já convertido
     * @throws EmailDeliveryException       SMTP recusou; o convite fica persistido sem {@code sentAt}
     */
    FoundingInviteOutputDto invite(UUID waitlistId, String invitedBy);

    /**
     * Dados para pré-preencher o cadastro a partir de um token <strong>ativo</strong>.
     *
     * @throws DomainNotFoundException token inexistente, expirado, invalidado ou convertido — sem distinguir
     */
    FoundingInviteLookupOutputDto lookup(String rawToken);

    /** O convite ativo correspondente ao token, se houver. Usado pela saga de cadastro. */
    Optional<FoundingInvite> findActive(String rawToken);
}
