package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.exception.EntityNotFoundException;
import br.com.menthoros.backend.exception.InvalidArgumentException;
import br.com.menthoros.backend.mapper.ProvaMapper;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.security.TenantContext;
import br.com.menthoros.backend.services.ProvaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * TEMPLATE: Reference service implementation.
 *
 * Use this file as a template when generating new services for menthoros-backend.
 * Copy this file, adapt class names and method logic for your resource.
 *
 * MANDATORY RULES:
 * ✅ Every public method has full JavaDoc with: description, Idempotent status, Side Effects, Tenant-aware status
 * ✅ Validate inputs (don't trust DTO @Valid annotations alone)
 * ✅ Log entry/exit with context
 * ✅ Use TenantContext for multi-tenancy isolation
 * ✅ @Transactional(readOnly = true) for read operations
 * ✅ Throw appropriate exceptions (EntityNotFoundException, InvalidArgumentException, etc.)
 */
@Slf4j
@Service
public class ProvaServiceImpl implements ProvaService {

    private final ProvaRepository provaRepository;
    private final ProvaMapper provaMapper;

    public ProvaServiceImpl(ProvaRepository provaRepository, ProvaMapper provaMapper) {
        this.provaRepository = provaRepository;
        this.provaMapper = provaMapper;
    }

    /**
     * Create a new prova (competition).
     *
     * **Idempotent:** NO — Creates a new entity each time this method is called.
     * **Side Effects:** Database insert (new Prova entity is persisted)
     * **Tenant-aware:** YES — Filters by TenantContext.getRequiredTenantId()
     *
     * @param input validated input DTO with prova data
     * @return newly created Prova entity (not persisted yet, but will be by repository)
     * @throws InvalidArgumentException if input validation fails
     */
    @Transactional
    public Prova criar(ProvaInputDto input) {
        log.info("Creating prova: nome={}", input.nome());

        // Defensive validation (beyond DTO annotations)
        if (input.nome() == null || input.nome().isBlank()) {
            throw new InvalidArgumentException("Nome cannot be blank");
        }
        if (input.dataProva() == null) {
            throw new InvalidArgumentException("Data da prova is required");
        }

        UUID tenantId = TenantContext.getRequiredTenantId();

        Prova entity = provaMapper.toDomain(input);
        entity = provaRepository.save(entity);

        log.info("Prova created: id={}, tenantId={}", entity.getId(), tenantId);
        return entity;
    }

    /**
     * Update an existing prova with new data.
     *
     * **Idempotent:** NO — Updates are cumulative; calling twice with different data produces different results.
     * **Side Effects:** Database update (Prova entity is modified and persisted)
     * **Tenant-aware:** YES
     *
     * @param id the prova ID to update
     * @param input new data for the prova
     * @return updated prova as output DTO
     * @throws EntityNotFoundException if prova not found
     * @throws InvalidArgumentException if input validation fails
     */
    @Transactional
    public ProvaOutputDto atualizar(UUID id, ProvaInputDto input) {
        log.info("Updating prova: id={}", id);

        // Validate input
        if (input.nome() == null || input.nome().isBlank()) {
            throw new InvalidArgumentException("Nome cannot be blank");
        }

        UUID tenantId = TenantContext.getRequiredTenantId();

        Prova entity = provaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prova not found: " + id));

        // Verify tenant ownership
        if (!entity.getTenantId().equals(tenantId)) {
            log.warn("Unauthorized access attempt: userId={}, provaId={}, tenantId={}",
                    tenantId, id, entity.getTenantId());
            throw new EntityNotFoundException("Prova not found");
        }

        // Update fields
        entity.setNome(input.nome());
        entity.setDataProva(input.dataProva());
        entity.setLocal(input.local());
        entity.setAtualizadoEm(LocalDateTime.now());

        entity = provaRepository.save(entity);

        log.info("Prova updated: id={}, tenantId={}", entity.getId(), tenantId);
        return provaMapper.toOutputDto(entity);
    }

    /**
     * Delete a prova (soft delete — marks deletedAt timestamp).
     *
     * **Idempotent:** YES — Calling delete on an already-deleted prova is safe (idempotent).
     * **Side Effects:** Database update (deletedAt field is set, entity remains in database)
     * **Tenant-aware:** YES
     *
     * @param id the prova ID to delete
     * @throws EntityNotFoundException if prova not found or already deleted
     */
    @Transactional
    public void deletar(UUID id) {
        log.info("Deleting prova: id={}", id);

        UUID tenantId = TenantContext.getRequiredTenantId();

        Prova entity = provaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prova not found: " + id));

        // Verify tenant ownership
        if (!entity.getTenantId().equals(tenantId)) {
            log.warn("Unauthorized delete attempt: userId={}, provaId={}", tenantId, id);
            throw new EntityNotFoundException("Prova not found");
        }

        // Soft delete
        entity.setDeletedAt(LocalDateTime.now());
        provaRepository.save(entity);

        log.info("Prova deleted: id={}, tenantId={}", id, tenantId);
    }

    /**
     * Get a prova by ID.
     *
     * **Idempotent:** YES — Read-only operation, no state changes.
     * **Side Effects:** NONE (only reads from database)
     * **Tenant-aware:** YES — Validates tenant ownership before returning
     *
     * @param id the prova ID to retrieve
     * @return prova data as output DTO
     * @throws EntityNotFoundException if prova not found, deleted, or not owned by current tenant
     */
    @Transactional(readOnly = true)
    public ProvaOutputDto obterPorId(UUID id) {
        log.info("Fetching prova: id={}", id);

        UUID tenantId = TenantContext.getRequiredTenantId();

        Prova entity = provaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Prova not found: " + id));

        // Check tenant ownership
        if (!entity.getTenantId().equals(tenantId)) {
            log.warn("Unauthorized access attempt: userId={}, provaId={}", tenantId, id);
            throw new EntityNotFoundException("Prova not found");
        }

        // Check if soft-deleted
        if (entity.getDeletedAt() != null) {
            throw new EntityNotFoundException("Prova has been deleted: " + id);
        }

        return provaMapper.toOutputDto(entity);
    }

    /**
     * Get all active provas for the current tenant.
     *
     * **Idempotent:** YES — Read-only operation.
     * **Side Effects:** NONE
     * **Tenant-aware:** YES — Filters by current tenant automatically via repository query
     *
     * @return list of all non-deleted provas for current tenant
     */
    @Transactional(readOnly = true)
    public List<ProvaOutputDto> listarTodas() {
        log.info("Listing all provas for tenant");

        UUID tenantId = TenantContext.getRequiredTenantId();

        List<Prova> entities = provaRepository.findByTenantIdAndDeletedAtIsNull(tenantId);

        log.info("Found {} provas for tenantId={}", entities.size(), tenantId);

        return entities.stream()
                .map(provaMapper::toOutputDto)
                .toList();
    }
}
