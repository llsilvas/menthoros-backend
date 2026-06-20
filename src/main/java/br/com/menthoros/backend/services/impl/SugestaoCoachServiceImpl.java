package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.SugestaoCoachOutputDto;
import br.com.menthoros.backend.entity.SugestaoCoach;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.SugestaoCoachMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.SugestaoCoachRepository;
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

    @Override
    @Transactional(readOnly = true)
    public List<SugestaoCoachOutputDto> listarPorAtleta(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("listarPorAtleta: atletaId={}, tenantId={}", atletaId, tenantId);
        return repository.findAllByAtletaIdAndTenantId(atletaId, tenantId)
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
                sugestao.setStatus(StatusSugestao.APPROVED);
                sugestao.setReviewedAt(Instant.now());
                repository.save(sugestao);
                log.info("aprovar: sugestão {} aprovada para tenant={}", id, tenantId);
                return mapper.toOutputDto(sugestao);
            }
        }
        throw new IllegalStateException("Status desconhecido: " + sugestao.getStatus());
    }

    @Override
    @Transactional
    public SugestaoCoachOutputDto rejeitar(UUID id) {
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
                sugestao.setStatus(StatusSugestao.REJECTED);
                sugestao.setReviewedAt(Instant.now());
                repository.save(sugestao);
                log.info("rejeitar: sugestão {} rejeitada para tenant={}", id, tenantId);
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
}
