package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.RejeitarSugestaoRequestDto;
import br.com.menthoros.backend.dto.output.SugestaoCoachOutputDto;
import br.com.menthoros.backend.entity.SugestaoCoach;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.SugestaoCoachMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.SugestaoCoachRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.SugestaoCoachService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SugestaoCoachServiceImpl implements SugestaoCoachService {

    private final SugestaoCoachRepository repository;
    private final SugestaoCoachMapper mapper;
    private final AuthenticatedPrincipalResolver principalResolver;
    private final UsuarioRepository usuarioRepository;

    /**
     * Lista as sugestões mais recentes de um atleta no tenant corrente.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId().
     *
     * @param atletaId ID do atleta
     * @return lista de sugestões ordenadas por createdAt DESC, limitada pela query
     */
    @Override
    @Transactional(readOnly = true)
    public List<SugestaoCoachOutputDto> listarPorAtleta(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("listarPorAtleta: atletaId={}, tenantId={}", atletaId, tenantId);
        return repository.findAllByAtletaIdAndTenantId(atletaId, tenantId, Instant.now())
                .stream().map(mapper::toOutputDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SugestaoCoachOutputDto> listar(StatusSugestao status) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("listar: tenantId={}, status={}", tenantId, status);

        List<SugestaoCoach> sugestoes = repository.findByTenantIdAndStatus(tenantId, status);

        if (status == StatusSugestao.PENDING) {
            Instant agora = Instant.now();
            sugestoes = sugestoes.stream()
                    .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(agora))
                    .toList();
        }

        return sugestoes.stream().map(mapper::toOutputDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SugestaoCoachOutputDto detalhe(UUID id) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("detalhe: id={}, tenantId={}", id, tenantId);

        SugestaoCoach sugestao = buscarOuLancar(id, tenantId);
        return mapper.toOutputDto(sugestao);
    }

    @Override
    @Transactional
    public SugestaoCoachOutputDto aprovar(UUID id) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("aprovar: id={}, tenantId={}", id, tenantId);

        SugestaoCoach sugestao = buscarOuLancar(id, tenantId);

        switch (sugestao.getStatus()) {
            case APPROVED -> {
                log.debug("aprovar: sugestão {} já está APPROVED — no-op", id);
                return mapper.toOutputDto(sugestao);
            }
            case REJECTED -> throw new DomainRuleViolationException(
                    "Sugestão " + id + " está REJECTED — transição para APPROVED não permitida");
            case PENDING -> {
                UUID reviewedBy = resolverReviewedBy(tenantId);
                Instant agora = Instant.now();
                int linhas = repository.decidirSePendente(id, tenantId, StatusSugestao.PENDING,
                        StatusSugestao.APPROVED, agora, reviewedBy, null);
                if (linhas == 0) {
                    throw new DomainConflictException(
                            "Sugestão " + id + " já foi decidida por outra requisição concorrente");
                }
                sugestao.setStatus(StatusSugestao.APPROVED);
                sugestao.setReviewedAt(agora);
                sugestao.setReviewedBy(reviewedBy);
                sugestao.setRejectionReason(null);
                log.info("aprovar: sugestão {} aprovada para tenant={}, reviewedBy={}", id, tenantId, reviewedBy);
                return mapper.toOutputDto(sugestao);
            }
        }
        throw new IllegalStateException("Status desconhecido: " + sugestao.getStatus());
    }

    @Override
    @Transactional
    public SugestaoCoachOutputDto rejeitar(UUID id, RejeitarSugestaoRequestDto request) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("rejeitar: id={}, tenantId={}", id, tenantId);

        SugestaoCoach sugestao = buscarOuLancar(id, tenantId);

        switch (sugestao.getStatus()) {
            case REJECTED -> {
                log.debug("rejeitar: sugestão {} já está REJECTED — no-op", id);
                return mapper.toOutputDto(sugestao);
            }
            case APPROVED -> throw new DomainRuleViolationException(
                    "Sugestão " + id + " está APPROVED — transição para REJECTED não permitida");
            case PENDING -> {
                UUID reviewedBy = resolverReviewedBy(tenantId);
                String rejectionReason = request == null ? null : request.rejectionReason();
                Instant agora = Instant.now();
                int linhas = repository.decidirSePendente(id, tenantId, StatusSugestao.PENDING,
                        StatusSugestao.REJECTED, agora, reviewedBy, rejectionReason);
                if (linhas == 0) {
                    throw new DomainConflictException(
                            "Sugestão " + id + " já foi decidida por outra requisição concorrente");
                }
                sugestao.setStatus(StatusSugestao.REJECTED);
                sugestao.setReviewedAt(agora);
                sugestao.setReviewedBy(reviewedBy);
                sugestao.setRejectionReason(rejectionReason);
                log.info("rejeitar: sugestão {} rejeitada para tenant={}, reviewedBy={}", id, tenantId, reviewedBy);
                return mapper.toOutputDto(sugestao);
            }
        }
        throw new IllegalStateException("Status desconhecido: " + sugestao.getStatus());
    }

    private SugestaoCoach buscarOuLancar(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "SugestaoCoach não encontrada: id=" + id + ", tenantId=" + tenantId));
    }

    /**
     * Resolve o {@code Usuario.id} do técnico/admin autenticado (nunca o {@code sub} do Keycloak
     * direto) — mesmo padrão de {@code UsuarioServiceImpl.getCurrentUser} (design D1).
     */
    private UUID resolverReviewedBy(UUID tenantId) {
        String sub = principalResolver.getCurrentSubject();
        Usuario usuario = usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Usuário autenticado não encontrado no tenant atual"));
        return usuario.getId();
    }
}
