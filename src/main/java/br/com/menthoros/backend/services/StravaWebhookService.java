package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.strava.StravaWebhookEventDto;

/**
 * Asynchronous processing of Strava webhook events.
 * Receives notifications for activity create/update/delete and processes asynchronously.
 */
public interface StravaWebhookService {
    void handleEventAsync(StravaWebhookEventDto event);
    void processCreateEvent(Long objectId, Long ownerId);
    void processUpdateEvent(Long objectId, Long ownerId);
    void processDeleteEvent(Long objectId, Long ownerId);
}
