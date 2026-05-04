package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.services.StravaWebhookService;
import br.com.menthoros.backend.services.StravaActivityService;
import br.com.menthoros.backend.dto.strava.StravaWebhookEventDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StravaWebhookServiceImpl implements StravaWebhookService {

    private final IntegracaoExternaRepository integracaoExternaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final StravaActivityService stravaActivityService;

    @Async("stravaWebhookExecutor")
    @Transactional
    public void handleEventAsync(StravaWebhookEventDto event) {
        if (event == null || event.objectType() == null || event.ownerId() == null || event.objectId() == null) {
            return;
        }

        if (!"activity".equalsIgnoreCase(event.objectType())) {
            log.info("Evento webhook Strava ignorado por tipo não suportado: {}", event.objectType());
            return;
        }

        IntegracaoExterna integracao = findActiveIntegrationByOwnerId(event.ownerId());
        if (integracao == null) {
            log.warn("Webhook Strava descartado: owner_id={} não encontrado", event.ownerId());
            return;
        }

        try {
            TenantContext.setTenantId(integracao.getTenantId());
            String aspectType = event.aspectType() == null ? "" : event.aspectType().toLowerCase();
            switch (aspectType) {
                case "create" -> processCreateEvent(event.objectId(), event.ownerId());
                case "update" -> processUpdateEvent(event.objectId(), event.ownerId());
                case "delete" -> processDeleteEvent(event.objectId(), event.ownerId());
                default -> log.info("Webhook Strava ignorado: aspect_type={} object_id={}", event.aspectType(), event.objectId());
            }
        } catch (Exception ex) {
            log.error(
                    "Erro ao processar webhook Strava (aspect_type={}, object_id={}, owner_id={}): {}",
                    event.aspectType(),
                    event.objectId(),
                    event.ownerId(),
                    ex.getMessage(),
                    ex
            );
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void processCreateEvent(Long objectId, Long ownerId) {
        IntegracaoExterna integracao = requireIntegration(ownerId);
        stravaActivityService.syncSingleActivityById(integracao.getAtleta(), integracao, objectId);
    }

    @Transactional
    public void processUpdateEvent(Long objectId, Long ownerId) {
        IntegracaoExterna integracao = requireIntegration(ownerId);
        stravaActivityService.syncSingleActivityById(integracao.getAtleta(), integracao, objectId);
    }

    @Transactional
    public void processDeleteEvent(Long objectId, Long ownerId) {
        IntegracaoExterna integracao = requireIntegration(ownerId);
        treinoRealizadoRepository
                .findByExternalIdAndAtletaId(String.valueOf(objectId), integracao.getAtleta().getId())
                .ifPresent(this::markAsCanceled);
    }

    private IntegracaoExterna requireIntegration(Long ownerId) {
        IntegracaoExterna integracao = findActiveIntegrationByOwnerId(ownerId);
        if (integracao == null) {
            throw new IllegalArgumentException("owner_id não corresponde a integração ativa");
        }
        return integracao;
    }

    private IntegracaoExterna findActiveIntegrationByOwnerId(Long ownerId) {
        return integracaoExternaRepository
                .findActiveByExternalAthleteIdAndPlataforma(String.valueOf(ownerId), FonteDados.STRAVA)
                .orElse(null);
    }

    private void markAsCanceled(TreinoRealizado treino) {
        treino.setStatusSincronizacao(StatusSincronizacao.CANCELADO);
        String metadata = treino.getMetadadosSincronizacao();
        if (metadata == null || metadata.isBlank()) {
            metadata = "{}";
        }
        if (metadata.endsWith("}")) {
            metadata = metadata.substring(0, metadata.length() - 1) + ",\"deletedOnStrava\":true}";
        }
        treino.setMetadadosSincronizacao(metadata);
        treinoRealizadoRepository.save(treino);
    }
}
