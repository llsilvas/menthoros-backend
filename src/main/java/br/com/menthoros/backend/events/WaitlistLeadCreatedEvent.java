package br.com.menthoros.backend.events;

import java.util.UUID;

/**
 * Publicado por {@code WaitlistServiceImpl.registrar} só quando um lead é criado de verdade
 * ({@code Resultado.CRIADO}) — nunca em reenvio ({@code JA_INSCRITO}) nem em honeypot
 * ({@code IGNORADO}). Consumido assincronamente por {@code WaitlistNotificationListener}.
 */
public record WaitlistLeadCreatedEvent(UUID waitlistId) {}
