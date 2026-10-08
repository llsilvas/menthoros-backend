package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.WaitlistInputDto;
import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.events.WaitlistLeadCreatedEvent;
import br.com.menthoros.backend.repository.WaitlistRepository;
import br.com.menthoros.backend.services.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

/**
 * Implementação da {@link WaitlistService}.
 *
 * <p>Sem {@code @Transactional} no método: cada chamada ao repositório roda na própria transação.
 * Isso permite capturar a {@link DataIntegrityViolationException} da corrida (índice único) sem
 * deixar uma transação externa marcada como rollback-only.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WaitlistServiceImpl implements WaitlistService {

    private static final String ORIGEM_LANDING = "landing";
    private static final String UK_EMAIL_NORMALIZED = "uk_waitlist_email_normalized";

    private final WaitlistRepository waitlistRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Resultado registrar(WaitlistInputDto dto) {
        // Validação defensiva: não confiar apenas no @Valid do controller.
        if (dto == null) {
            throw new IllegalArgumentException("WaitlistInputDto não pode ser nulo");
        }

        // Honeypot: campo oculto preenchido => provável bot. Descarta silenciosamente.
        if (dto.website() != null && !dto.website().isBlank()) {
            log.info("Submissão de waitlist ignorada (honeypot acionado)");
            return Resultado.IGNORADO;
        }

        if (dto.email() == null || dto.email().isBlank()) {
            throw new IllegalArgumentException("E-mail é obrigatório");
        }
        if (dto.nome() == null || dto.nome().isBlank()) {
            throw new IllegalArgumentException("Nome é obrigatório");
        }

        String emailNormalizado = normalizarEmail(dto.email());
        log.info("Inscrição na waitlist recebida: perfil={}", dto.perfil());

        if (waitlistRepository.existsByEmailNormalized(emailNormalizado)) {
            log.info("Waitlist: e-mail já inscrito");
            return Resultado.JA_INSCRITO;
        }

        Waitlist waitlist = Waitlist.builder()
                .nome(dto.nome().trim())
                .email(dto.email().trim())
                .emailNormalized(emailNormalizado)
                .telefone(dto.telefone())
                .perfil(dto.perfil())
                .qtdAtletas(dto.perfil() == PerfilWaitlist.TREINADOR ? dto.qtdAtletas() : null)
                .aceiteLgpd(Boolean.TRUE.equals(dto.aceiteLgpd()))
                .origem(ORIGEM_LANDING)
                .utmSource(dto.utmSource())
                .utmMedium(dto.utmMedium())
                .utmCampaign(dto.utmCampaign())
                .utmContent(dto.utmContent())
                .build();

        try {
            Waitlist salvo = waitlistRepository.saveAndFlush(waitlist);
            log.info("Waitlist: inscrição criada id={}", salvo.getId());
            publicarEventoDeNotificacao(salvo.getId());
            return Resultado.CRIADO;
        } catch (DataIntegrityViolationException e) {
            // Só trata como duplicata se a violação for do índice único de e-mail (corrida entre
            // o existsBy e o insert). Qualquer outra violação de integridade é erro real — propaga.
            Throwable causa = e.getMostSpecificCause();
            if (causa.getMessage() != null && causa.getMessage().contains(UK_EMAIL_NORMALIZED)) {
                log.info("Waitlist: e-mail já inscrito (corrida resolvida pelo índice único)");
                return Resultado.JA_INSCRITO;
            }
            throw e;
        }
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Publica o evento que dispara o e-mail — melhor esforço, nunca derruba um cadastro que já
     * commitou. {@code fallbackExecution=true} do listener despacha o {@code @Async} de forma
     * síncrona nesta mesma chamada (ver design.md D2); se o executor dedicado estiver saturado,
     * {@code publishEvent} pode lançar (ex.: {@code TaskRejectedException}) depois que a linha já
     * foi gravada — sem este catch, o inscrito veria 500 para uma inscrição que na verdade deu
     * certo.
     */
    private void publicarEventoDeNotificacao(UUID waitlistId) {
        try {
            eventPublisher.publishEvent(new WaitlistLeadCreatedEvent(waitlistId));
        } catch (RuntimeException e) {
            log.error("Waitlist: falha ao publicar evento de notificação para o lead {}", waitlistId, e);
        }
    }
}
