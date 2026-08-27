package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import br.com.menthoros.backend.services.ManualReconciliationService;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.TreinoReconciliacao;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.ReconciliationActionType;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.TreinoReconciliacaoRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Serviço de ações manuais de reconciliação.
 * Gerencia: vincular manualmente, marcar não planejado, desfazer vínculo.
 * Com auditoria completa em tb_treino_reconciliacao.
 */
@Service
public class ManualReconciliationServiceImpl implements ManualReconciliationService {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoReconciliacaoRepository treinoReconciliacaoRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;

    public ManualReconciliationServiceImpl(
            TreinoRealizadoRepository treinoRealizadoRepository,
            TreinoReconciliacaoRepository treinoReconciliacaoRepository,
            TreinoPlanejadoRepository treinoPlanejadoRepository,
            IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService) {
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.treinoReconciliacaoRepository = treinoReconciliacaoRepository;
        this.treinoPlanejadoRepository = treinoPlanejadoRepository;
        this.ingestaoTreinoRealizadoService = ingestaoTreinoRealizadoService;
    }

    /**
     * Vincula manualmente uma atividade realizada a um treino planejado.
     * Valida existência do TreinoPlanejado (tenant + atleta) antes de vincular.
     * Registra evento de auditoria e persiste estado.
     *
     * <p><strong>Idempotent:</strong> NO — reatribui o vínculo a cada chamada.
     * <p><strong>Side Effects:</strong> Database update (vínculo + status) + evento de auditoria;
     * dispara {@link IngestaoTreinoRealizadoService#reprocessar} para o seam de ingestão.
     * <p><strong>Tenant-aware:</strong> YES — {@code treinoRealizadoId}/{@code treinoPlanejadoId}
     * resolvidos via {@code findAndValidate} tenant-scoped.
     */
    @Transactional
    public TreinoRealizado linkManually(
            UUID treinoRealizadoId,
            UUID treinoPlanejadoId,
            UUID tenantId,
            String actorId) {

        TreinoRealizado realizado = findAndValidate(treinoRealizadoId, tenantId);

        if (treinoPlanejadoId == null) {
            throw new IllegalArgumentException("treinoPlanejadoId não pode ser nulo");
        }

        ReconciliationStatus beforeStatus = realizado.getReconciliationStatus();
        UUID beforePlannedId = realizado.getTreinoPlanejadoId();

        // Carregar e validar TreinoPlanejado
        TreinoPlanejado planejado = treinoPlanejadoRepository
                .findById(treinoPlanejadoId)
                .orElseThrow(() -> new IllegalArgumentException("TreinoPlanejado não encontrado: " + treinoPlanejadoId));

        if (!planejado.getAtleta().getId().equals(realizado.getAtleta().getId())) {
            throw new IllegalArgumentException("TreinoPlanejado deve ser do mesmo atleta");
        }

        // Vincular via relacionamento JPA (não via campo read-only)
        planejado.setStatusTreino(TreinoExecucaoStatus.REALIZADO);
        planejado.limparPulo();
        planejado.setStatusSincronizacao(StatusSincronizacao.SINCRONIZADO);
        realizado.setTreinoPlanejado(planejado);
        realizado.setReconciliationStatus(ReconciliationStatus.VINCULADO_MANUAL);
        realizado.setReconciledAt(Instant.now());
        realizado.setReconciledBy(actorId);

        TreinoRealizado saved = treinoRealizadoRepository.save(realizado);

        createAuditEvent(
                saved,
                ReconciliationActionType.VINCULAR_MANUALMENTE,
                beforeStatus,
                ReconciliationStatus.VINCULADO_MANUAL,
                beforePlannedId,
                treinoPlanejadoId,
                "MANUAL_LINK",
                "Vínculo manual executado pelo treinador",
                actorId,
                tenantId
        );

        // O seam único é quem decide se algo realmente mudou na carga (D2/D9) — chamado após
        // toda mutação de TreinoRealizado por completude, mesmo quando este gesto específico não
        // altera tssCalculado/carga hoje.
        ingestaoTreinoRealizadoService.reprocessar(saved.getId(), null);

        return findByIdWithEtapas(saved.getId(), tenantId);
    }

    /**
     * Marca uma atividade realizada como não planejada (orfã).
     * Desvincula de qualquer treino planejado e registra auditoria.
     *
     * <p><strong>Idempotent:</strong> NO — reatribui o status a cada chamada.
     * <p><strong>Side Effects:</strong> Database update (desvínculo + status) + evento de
     * auditoria; dispara {@link IngestaoTreinoRealizadoService#reprocessar}.
     * <p><strong>Tenant-aware:</strong> YES — {@code treinoRealizadoId} resolvido via
     * {@code findAndValidate} tenant-scoped.
     */
    @Transactional
    public TreinoRealizado markAsNotPlanned(
            UUID treinoRealizadoId,
            UUID tenantId,
            String actorId) {

        TreinoRealizado realizado = findAndValidate(treinoRealizadoId, tenantId);

        ReconciliationStatus beforeStatus = realizado.getReconciliationStatus();
        UUID beforePlannedId = realizado.getTreinoPlanejadoId();

        realizado.setTreinoPlanejado(null);
        realizado.setReconciliationStatus(ReconciliationStatus.NAO_PLANEJADO);
        realizado.setReconciledAt(Instant.now());
        realizado.setReconciledBy(actorId);

        TreinoRealizado saved = treinoRealizadoRepository.save(realizado);

        createAuditEvent(
                saved,
                ReconciliationActionType.MARCAR_NAO_PLANEJADO,
                beforeStatus,
                ReconciliationStatus.NAO_PLANEJADO,
                beforePlannedId,
                null,
                "MARKED_NOT_PLANNED",
                "Marcado como atividade não planejada",
                actorId,
                tenantId
        );

        ingestaoTreinoRealizadoService.reprocessar(saved.getId(), null);

        return findByIdWithEtapas(saved.getId(), tenantId);
    }

    /**
     * Desfaz o vínculo de uma atividade realizada com um treino planejado.
     * Volta para estado PENDENTE e registra auditoria.
     *
     * <p><strong>Idempotent:</strong> NO — reatribui o status a cada chamada.
     * <p><strong>Side Effects:</strong> Database update (desvínculo + status) + evento de
     * auditoria; dispara {@link IngestaoTreinoRealizadoService#reprocessar}.
     * <p><strong>Tenant-aware:</strong> YES — {@code treinoRealizadoId} resolvido via
     * {@code findAndValidate} tenant-scoped.
     */
    @Transactional
    public TreinoRealizado unlinkManually(
            UUID treinoRealizadoId,
            UUID tenantId,
            String actorId) {

        TreinoRealizado realizado = findAndValidate(treinoRealizadoId, tenantId);

        ReconciliationStatus beforeStatus = realizado.getReconciliationStatus();
        UUID beforePlannedId = realizado.getTreinoPlanejadoId();

        realizado.setTreinoPlanejado(null);
        realizado.setReconciliationStatus(ReconciliationStatus.PENDENTE);
        realizado.setReconciledAt(Instant.now());
        realizado.setReconciledBy(actorId);

        TreinoRealizado saved = treinoRealizadoRepository.save(realizado);

        createAuditEvent(
                saved,
                ReconciliationActionType.DESFAZER_VINCULO,
                beforeStatus,
                ReconciliationStatus.PENDENTE,
                beforePlannedId,
                null,
                "UNLINKED",
                "Vínculo desfeito pelo treinador",
                actorId,
                tenantId
        );

        ingestaoTreinoRealizadoService.reprocessar(saved.getId(), null);

        return findByIdWithEtapas(saved.getId(), tenantId);
    }

    /**
     * Recupera estado de reconciliação de uma atividade.
     */
    @Transactional(readOnly = true)
    public TreinoRealizado getReconciliationState(UUID treinoRealizadoId, UUID tenantId) {
        return findAndValidate(treinoRealizadoId, tenantId);
    }

    // ===== Private Helpers =====

    private TreinoRealizado findAndValidate(UUID id, UUID tenantId) {
        TreinoRealizado realizado = treinoRealizadoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TreinoRealizado não encontrado: " + id));

        Hibernate.initialize(realizado.getEtapasRealizadas());
        if (!realizado.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Tenant mismatch para TreinoRealizado");
        }

        return realizado;
    }

    private TreinoRealizado findByIdWithEtapas(UUID id, UUID tenantId) {
        TreinoRealizado realizado = treinoRealizadoRepository.findByIdWithEtapas(id)
                .orElseThrow(() -> new IllegalArgumentException("TreinoRealizado não encontrado: " + id));

        if (!realizado.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Tenant mismatch para TreinoRealizado");
        }

        return realizado;
    }

    private void createAuditEvent(
            TreinoRealizado realizado,
            ReconciliationActionType actionType,
            ReconciliationStatus beforeStatus,
            ReconciliationStatus afterStatus,
            UUID beforePlannedIdUuid,
            UUID afterPlannedIdUuid,
            String reasonCode,
            String reasonText,
            String actorId,
            UUID tenantId) {

        TreinoReconciliacao event = new TreinoReconciliacao();
        event.setTreinoRealizado(realizado);
        event.setActionType(actionType);
        event.setBeforeStatus(beforeStatus);
        event.setAfterStatus(afterStatus);
        event.setBeforePlannedIdUuid(beforePlannedIdUuid);
        event.setAfterPlannedIdUuid(afterPlannedIdUuid);
        event.setReasonCode(reasonCode);
        event.setReasonText(reasonText);
        event.setActorId(actorId);
        event.setTenantId(tenantId.toString());
        event.setOccurredAt(Instant.now());

        treinoReconciliacaoRepository.save(event);
    }

}
