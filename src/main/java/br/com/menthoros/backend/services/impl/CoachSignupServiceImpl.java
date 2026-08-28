package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.CoachSignupInputDto;
import br.com.menthoros.backend.dto.output.CoachSignupOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.FoundingInvite;
import br.com.menthoros.backend.entity.SignupProvisioning;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.SignupProvisioningStatus;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.exception.SignupRateLimitException;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.FoundingInviteRepository;
import br.com.menthoros.backend.repository.SignupProvisioningRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.CoachSignupService;
import br.com.menthoros.backend.services.FoundingInviteService;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import br.com.menthoros.backend.services.NovoUsuarioKeycloak;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Orquestra o auto-cadastro de assessoria entre Postgres e Keycloak.
 *
 * <p><strong>Deliberadamente SEM {@code @Transactional}.</strong> Duas razões, e as duas são
 * eliminatórias:</p>
 * <ol>
 *   <li>Não existe transação que abranja Postgres e Keycloak. Uma transação local daria a
 *       <em>ilusão</em> de atomicidade e ainda deixaria organização e usuário órfãos no Keycloak
 *       quando o rollback acontecesse.</li>
 *   <li>O rastro em {@code tb_signup_provisioning} precisa <strong>sobreviver</strong> à falha. Sob
 *       uma transação, o rollback levaria junto exatamente a linha que registra o que deu errado.</li>
 * </ol>
 *
 * <p>No lugar disso: cada passo registra seu avanço, e a falha desfaz os recursos já criados na
 * <strong>ordem inversa</strong>.</p>
 *
 * <p>Duas portas de entrada, um fluxo: o {@link ProvisioningMode} é resolvido antes da saga e cada
 * passo lê nele o que muda (plano, verificação de e-mail, limites, consumo do convite).</p>
 */
@Slf4j
@Service
public class CoachSignupServiceImpl implements CoachSignupService {

    /**
     * O fundador nasce dono da assessoria, não técnico contratado. {@code PROPRIETARIO} é
     * composite de {@code TECNICO} no realm, então o token continua trazendo as duas — trocar
     * esta constante não tira nenhum acesso que o coach já tinha.
     */
    private static final String ROLE_COACH = "PROPRIETARIO";

    private static final String METRICA = "signup.coach";
    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_SIGNUP_STATUS = "signupStatus";

    private final AssessoriaRepository assessoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SignupProvisioningRepository provisioningRepository;
    private final KeycloakOrganizationGateway keycloak;
    private final FoundingInviteService foundingInviteService;
    private final FoundingInviteRepository foundingInviteRepository;

    private final MeterRegistry meterRegistry;
    private final int limitePorEmailPorDia;
    private final int tetoDiarioGlobal;

    public CoachSignupServiceImpl(
            AssessoriaRepository assessoriaRepository,
            UsuarioRepository usuarioRepository,
            SignupProvisioningRepository provisioningRepository,
            KeycloakOrganizationGateway keycloak,
            FoundingInviteService foundingInviteService,
            FoundingInviteRepository foundingInviteRepository,
            MeterRegistry meterRegistry,
            @Value("${app.coach-signup.rate-limit.per-email-per-day:3}") int limitePorEmailPorDia,
            @Value("${app.coach-signup.rate-limit.daily-cap:20}") int tetoDiarioGlobal) {
        this.assessoriaRepository = assessoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.provisioningRepository = provisioningRepository;
        this.keycloak = keycloak;
        this.foundingInviteService = foundingInviteService;
        this.foundingInviteRepository = foundingInviteRepository;
        this.meterRegistry = meterRegistry;
        this.limitePorEmailPorDia = limitePorEmailPorDia;
        this.tetoDiarioGlobal = tetoDiarioGlobal;
    }

    /**
     * Provisiona assessoria, organização, usuário no Keycloak e usuário local.
     *
     * <p><strong>Idempotent:</strong> YES — pela {@code idempotencyKey}: o reenvio da mesma
     * requisição devolve o resultado original sem criar nada. No modo convite a chave do header é
     * ignorada e a idempotência é por tentativa ({@code "<token_hash>:<n>"}): rastro {@code ACTIVE}
     * devolve o resultado; só {@code FAILED} abre tentativa nova.
     * <p><strong>Side Effects:</strong> Database insert + External API (Keycloak) — cria
     * organização e usuário.
     * <p><strong>Tenant-aware:</strong> NO — roda antes de o tenant existir; é ele que o cria.
     *
     * @throws DuplicateResourceException   slug/e-mail em uso, ou chave de idempotência reusada com
     *                                      outro payload
     * @throws DomainNotFoundException      token de convite inexistente, expirado, invalidado ou convertido
     * @throws DomainRuleViolationException e-mail do formulário diverge do e-mail convidado
     * @throws DomainConflictException      convite com tentativa em curso ou resíduo que exige reparo manual
     */
    @Override
    public CoachSignupOutputDto cadastrar(CoachSignupInputDto input, String idempotencyKey, String correlationId) {
        // O honeypot responde como se tivesse dado certo. Devolver erro ensinaria o bot qual campo
        // o denunciou; devolver 201 sem criar nada não ensina nada.
        if (input.honeypotPreenchido()) {
            contar("honeypot");
            log.info("Cadastro descartado pelo honeypot: correlationId={}", correlationId);
            return CoachSignupOutputDto.de(input.slug(), input.email());
        }

        ProvisioningMode modo = resolverModo(input);
        String hash = hashDoPayload(input);
        String chave = modo.porConvite() ? chavePorTentativa(modo.invite(), correlationId) : idempotencyKey;

        var existente = provisioningRepository.findByIdempotencyKey(chave);
        if (existente.isPresent()) {
            return resolverReenvio(existente.get(), hash, correlationId);
        }

        if (modo.aplicarLimites()) {
            verificarLimites(input.email(), correlationId);
        }
        verificarDisponibilidade(input);

        SignupProvisioning operacao = provisioningRepository.save(SignupProvisioning.builder()
                .idempotencyKey(chave)
                .requestHash(hash)
                .email(input.email())
                .slug(input.slug())
                .status(SignupProvisioningStatus.STARTED)
                .correlationId(correlationId)
                .origin(modo.origin())
                .inviteId(modo.porConvite() ? modo.invite().getId() : null)
                .build());

        // Pilha porque a compensação é LIFO: desfaz na ordem inversa da criação.
        Deque<Runnable> desfazer = new ArrayDeque<>();

        try {
            Assessoria assessoria = criarAssessoria(input, modo);
            desfazer.push(() -> assessoriaRepository.deleteById(assessoria.getId()));
            // A partir daqui existe tenant: todo log seguinte o carrega sem repetir o parâmetro.
            MDC.put(MDC_TENANT_ID, assessoria.getId().toString());
            avancar(operacao, SignupProvisioningStatus.ASSESSORIA_CREATED,
                    op -> op.setAssessoriaId(assessoria.getId()));

            String organizationId = keycloak.criarOrganization(
                    input.nomeAssessoria(), input.slug(), assessoria.getId());
            desfazer.push(() -> keycloak.removerOrganization(organizationId));
            assessoria.setKeycloakOrganizationId(organizationId);
            assessoriaRepository.save(assessoria);
            avancar(operacao, SignupProvisioningStatus.ORGANIZATION_CREATED,
                    op -> op.setKeycloakOrganizationId(organizationId));

            // Habilitado. No cadastro público, com VERIFY_EMAIL pendente — criar desabilitado
            // impediria o próprio envio do e-mail (400 "User is disabled", verificado em 26.7). No
            // convite, sem required action e com emailVerified: o token já provou a posse.
            String usuarioKeycloakId = keycloak.criarUsuario(new NovoUsuarioKeycloak(
                    input.email(), input.nome(), input.senha(), true,
                    modo.acoesObrigatorias(), modo.emailVerificado()));
            desfazer.push(() -> keycloak.removerUsuario(usuarioKeycloakId));
            avancar(operacao, SignupProvisioningStatus.KEYCLOAK_USER_CREATED,
                    op -> op.setKeycloakUserId(usuarioKeycloakId));

            keycloak.atribuirRoleDeRealm(usuarioKeycloakId, ROLE_COACH);
            keycloak.adicionarMembroNaOrganization(organizationId, usuarioKeycloakId);

            Usuario usuario = criarUsuarioLocal(input, assessoria, usuarioKeycloakId, modo);
            desfazer.push(() -> usuarioRepository.deleteById(usuario.getId()));
            avancar(operacao, SignupProvisioningStatus.LOCAL_USER_CREATED, op -> {});

            CoachSignupOutputDto resposta;
            if (modo.enviarVerificacao()) {
                keycloak.enviarVerificacaoDeEmail(usuarioKeycloakId);
                avancar(operacao, SignupProvisioningStatus.VERIFICATION_EMAIL_SENT, op -> {});
                resposta = CoachSignupOutputDto.de(input.slug(), input.email());
            } else {
                resposta = CoachSignupOutputDto.prontoParaEntrar(input.slug(), input.email());
            }
            avancar(operacao, SignupProvisioningStatus.ACTIVE, op -> op.setResult(serializar(resposta)));

            if (modo.porConvite()) {
                consumirConvite(modo.invite(), assessoria.getId());
            }

            contar(modo.porConvite() ? "sucesso_convite" : "sucesso");
            log.info("Auto-cadastro concluído: tenantId={}, origin={}, correlationId={}",
                    assessoria.getId(), modo.origin(), correlationId);
            return resposta;

        } catch (RuntimeException falha) {
            compensar(operacao, desfazer, falha, correlationId);
            throw falha;
        } finally {
            MDC.remove(MDC_TENANT_ID);
        }
    }

    /**
     * Sem token: cadastro público. Com token: precisa estar ativo (senão 404, indistinguível de
     * "cadastro não existe") e o e-mail do formulário precisa ser o convidado — um token vazado não
     * pode abrir conta para outro endereço.
     */
    private ProvisioningMode resolverModo(CoachSignupInputDto input) {
        if (input.inviteToken() == null) {
            return ProvisioningMode.publicSignup();
        }
        FoundingInvite invite = foundingInviteService.findActive(input.inviteToken())
                .orElseThrow(() -> {
                    contar("convite_invalido");
                    return new DomainNotFoundException("Convite inválido ou expirado");
                });
        if (!invite.getEmail().equalsIgnoreCase(input.email())) {
            contar("convite_email_divergente");
            throw new DomainRuleViolationException("O e-mail do cadastro precisa ser o mesmo do convite");
        }
        return ProvisioningMode.foundingInvite(invite);
    }

    /**
     * Chave por tentativa. O {@link #resolverReenvio} existente rejeita qualquer rastro que não
     * esteja {@code ACTIVE} — inclusive {@code FAILED} já compensado — então reusar o hash puro
     * travaria a fundadora para sempre depois de uma falha no Keycloak. Regras:
     * <ul>
     *   <li>{@code ACTIVE} → a chave dele, para o reenvio devolver o resultado;</li>
     *   <li>{@code RECONCILIATION_REQUIRED} → 409: resíduo que exige mão humana, não retentar;</li>
     *   <li>estado intermediário → 409: a primeira tentativa ainda corre (duplo clique);</li>
     *   <li>só {@code FAILED} → tentativa nova, {@code "<hash>:<n+1>"}.</li>
     * </ul>
     */
    private String chavePorTentativa(FoundingInvite invite, String correlationId) {
        List<SignupProvisioning> tentativas = provisioningRepository.findByInviteIdOrderByCreatedAtAsc(invite.getId());
        for (SignupProvisioning tentativa : tentativas) {
            switch (tentativa.getStatus()) {
                case ACTIVE -> {
                    return tentativa.getIdempotencyKey();
                }
                case RECONCILIATION_REQUIRED -> {
                    contar("convite_reconciliacao_pendente");
                    log.warn("Convite com resíduo a reconciliar; nova tentativa bloqueada: inviteId={}, correlationId={}",
                            invite.getId(), correlationId);
                    throw new DomainConflictException(
                            "Seu cadastro precisa de atenção da nossa equipe. Fale com o suporte.");
                }
                case FAILED -> { /* compensado: não bloqueia */ }
                default -> {
                    contar("convite_tentativa_em_curso");
                    throw new DomainConflictException("Seu cadastro já está em andamento. Aguarde alguns instantes.");
                }
            }
        }
        return invite.getTokenHash() + ":" + (tentativas.size() + 1);
    }

    /** Sucesso é o único momento em que o token deixa de valer. A compensação nunca chega aqui. */
    private void consumirConvite(FoundingInvite invite, UUID assessoriaId) {
        invite.setConvertedAt(OffsetDateTime.now());
        invite.setAssessoriaId(assessoriaId);
        foundingInviteRepository.save(invite);
        log.info("Convite de fundadora convertido: inviteId={}", invite.getId());
    }

    /**
     * Reenvio: mesmo payload devolve o resultado original; payload diferente é conflito.
     *
     * <p>Sem o hash, uma chave reusada com outro corpo devolveria em silêncio o cadastro anterior —
     * o cliente acharia que criou a assessoria B e teria recebido a A.</p>
     */
    private CoachSignupOutputDto resolverReenvio(SignupProvisioning operacao, String hash, String correlationId) {
        if (!operacao.getRequestHash().equals(hash)) {
            throw new DuplicateResourceException(
                    "Chave de idempotência já usada com outro conteúdo");
        }
        if (operacao.getStatus() != SignupProvisioningStatus.ACTIVE) {
            // A tentativa original falhou ou ainda corre. Repetir agora duplicaria efeito externo.
            throw new DuplicateResourceException(
                    "Cadastro com esta chave de idempotência não foi concluído; use uma nova chave");
        }
        log.info("Reenvio idempotente atendido do rastro: correlationId={}", correlationId);
        return CoachSignupOutputDto.de(operacao.getSlug(), operacao.getEmail());
    }

    /**
     * Dimensões que o filtro de rate-limit não consegue ver.
     *
     * <p>Um filtro de servlet não enxerga o e-mail sem consumir o corpo da requisição, e o teto
     * global precisa de contagem persistida — memória de processo zeraria a cada deploy e se
     * multiplicaria por instância. Por isso estas duas vivem aqui, e não lá: a divisão é por o que
     * cada camada consegue ver, não por política.</p>
     *
     * <p>O recurso escasso não é linha de banco: é a <strong>cota de e-mail</strong>. Esgotá-la faz
     * a verificação dos cadastros <em>legítimos</em> parar de sair, sem erro visível para ninguém.</p>
     */
    private void verificarLimites(String email, String correlationId) {
        OffsetDateTime desdeOntem = OffsetDateTime.now().minusDays(1);

        long doEmail = provisioningRepository.countByEmailAndCreatedAtAfter(email, desdeOntem);
        if (doEmail >= limitePorEmailPorDia) {
            contar("limite_email");
            log.warn("Limite diário por e-mail atingido: correlationId={}", correlationId);
            throw new SignupRateLimitException("Limite diário por e-mail atingido");
        }

        long doDia = provisioningRepository.countByCreatedAtAfter(desdeOntem);
        if (doDia >= tetoDiarioGlobal) {
            contar("limite_teto_global");
            // ERROR, não WARN: o teto global é baixo de propósito, então batê-lo significa ou abuso
            // em curso ou crescimento real. Nos dois casos alguém precisa olhar hoje.
            log.error("TETO DIÁRIO GLOBAL DE CADASTROS ATINGIDO ({}) — abuso em curso ou "
                    + "crescimento real; ambos exigem ação. correlationId={}", tetoDiarioGlobal, correlationId);
            throw new SignupRateLimitException("Teto diário de cadastros atingido");
        }
    }

    /**
     * Pré-checagem de slug e e-mail.
     *
     * <p>Não fecha a janela de corrida — entre checar e inserir sempre há espaço. Ela existe para
     * devolver 409 <strong>antes</strong> de tocar o Keycloak no caso comum. Quem serializa a
     * corrida de verdade é a UNIQUE de {@code tb_assessoria.dominio}.</p>
     */
    private void verificarDisponibilidade(CoachSignupInputDto input) {
        if (assessoriaRepository.findByDominio(input.slug()).isPresent()) {
            contar("conflito_slug");
            throw new DuplicateResourceException("Identificador já está em uso");
        }
        if (usuarioRepository.existsByEmail(input.email())
                || keycloak.buscarUsuarioIdPorEmail(input.email()).isPresent()) {
            contar("conflito_email");
            throw new DuplicateResourceException("E-mail já cadastrado");
        }
    }

    private Assessoria criarAssessoria(CoachSignupInputDto input, ProvisioningMode modo) {
        try {
            return assessoriaRepository.save(Assessoria.builder()
                    .nome(input.nomeAssessoria())
                    .dominio(input.slug())
                    .emailContato(input.email())
                    .plano(modo.plano())
                    .maxAtletas(modo.maxAtletas())
                    .maxTecnicos(modo.maxTecnicos())
                    .founding(modo.founding())
                    .foundingConvertedAt(modo.founding() ? OffsetDateTime.now() : null)
                    .ativo(true)
                    .build());
        } catch (DataIntegrityViolationException corrida) {
            // Dois cadastros simultâneos com o mesmo slug: a UNIQUE decidiu. Quem perdeu recebe 409,
            // e nada foi criado no Keycloak ainda — este é o primeiro passo de propósito.
            throw new DuplicateResourceException("Identificador já está em uso");
        }
    }

    /** O id do {@code Usuario} local é o {@code sub} do Keycloak — não há id local independente. */
    private Usuario criarUsuarioLocal(CoachSignupInputDto input, Assessoria assessoria, String keycloakId,
                                      ProvisioningMode modo) {
        return usuarioRepository.save(Usuario.builder()
                .id(UUID.fromString(keycloakId))
                .keycloakId(keycloakId)
                .assessoria(assessoria)
                .email(input.email())
                .nome(input.nome())
                // TECNICO, não PROPRIETARIO: `role` é single-valued e alimenta a contagem de
                // técnicos do plano (maxTecnicos = 1). A propriedade vive em `owner`.
                .role(UserRole.TECNICO)
                .owner(true)
                // Único lugar do sistema que grava `false`: o fundador é quem precisa do wizard.
                // Todo o resto herda o DEFAULT true do banco (V80) — inclusive o técnico convidado,
                // que vai direto ao dashboard por decisão de produto (D1 da change).
                .onboardingConcluido(false)
                .ativo(true)
                .emailVerificado(modo.emailVerificado())
                .build());
    }

    /**
     * Desfaz na ordem inversa. Se a própria compensação falhar, o estado vira
     * {@code RECONCILIATION_REQUIRED} — e não é retentado aqui: insistir num Keycloak que acabou de
     * falhar troca um recurso órfão registrado por um loop dentro do request do usuário.
     */
    private void compensar(SignupProvisioning operacao, Deque<Runnable> desfazer,
                           RuntimeException falha, String correlationId) {
        log.warn("Falha no auto-cadastro, compensando: correlationId={}, causa={}",
                correlationId, falha.toString());

        MDC.put(MDC_SIGNUP_STATUS, "compensando");
        while (!desfazer.isEmpty()) {
            Runnable passo = desfazer.pop();
            try {
                passo.run();
            } catch (RuntimeException erroNaCompensacao) {
                log.error("Compensação falhou — recurso órfão exige reconciliação: correlationId={}",
                        correlationId, erroNaCompensacao);
                contar("reconciliacao_necessaria");
                finalizar(operacao, SignupProvisioningStatus.RECONCILIATION_REQUIRED,
                        resumo(falha) + " | compensação: " + resumo(erroNaCompensacao));
                MDC.remove(MDC_SIGNUP_STATUS);
                return;
            }
        }
        contar("falha_compensada");
        finalizar(operacao, SignupProvisioningStatus.FAILED, resumo(falha));
        MDC.remove(MDC_SIGNUP_STATUS);
    }

    /**
     * Uma métrica só, com tag de desfecho. Variar o conjunto de tags do mesmo meter quebraria o
     * registry, então todo desfecho passa por aqui.
     *
     * <p>Nada de e-mail, senha ou token vira tag: cardinalidade alta derruba o Prometheus, e e-mail
     * em métrica é dado pessoal exposto em endpoint de scraping.</p>
     */
    private void contar(String desfecho) {
        Counter.builder(METRICA).tag("desfecho", desfecho).register(meterRegistry).increment();
    }

    private void avancar(SignupProvisioning operacao, SignupProvisioningStatus status,
                         java.util.function.Consumer<SignupProvisioning> ajuste) {
        operacao.setStatus(status);
        ajuste.accept(operacao);
        provisioningRepository.save(operacao);
    }

    private void finalizar(SignupProvisioning operacao, SignupProvisioningStatus status, String detalhe) {
        operacao.setStatus(status);
        operacao.setErrorDetail(detalhe);
        provisioningRepository.save(operacao);
    }

    /** Tipo + mensagem. Nunca o payload, que carrega a senha. */
    private String resumo(RuntimeException e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /**
     * Hash do payload <strong>sem a senha e sem o token</strong>. Incluí-los faria o segredo
     * atravessar o banco em forma derivada, sem necessidade: o que a idempotência precisa distinguir
     * é a identidade do cadastro, e os segredos não fazem parte dela.
     */
    private String hashDoPayload(CoachSignupInputDto input) {
        String material = String.join("\n",
                input.nome(), input.email(), input.nomeAssessoria(), input.slug());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    private String serializar(CoachSignupOutputDto resposta) {
        return """
                {"slug":"%s","email":"%s","proximoPasso":"%s"}"""
                .formatted(resposta.slug(), resposta.email(), resposta.proximoPasso());
    }
}
