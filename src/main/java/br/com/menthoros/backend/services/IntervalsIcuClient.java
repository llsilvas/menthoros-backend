package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.intervalsicu.IcuAthleteDto;
import br.com.menthoros.backend.dto.intervalsicu.IcuEventDto;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Client HTTP do intervals.icu. Credencial (API key) é por atleta e vai por chamada.
 */
public interface IntervalsIcuClient {

    /** GET /api/v1/athlete/0 — 200 retorna o atleta; 401/403 retorna vazio. */
    Optional<IcuAthleteDto> validarApiKey(String apiKey);

    /** POST /api/v1/athlete/{id}/events. */
    IcuEventDto criarEvento(String apiKey, String externalAthleteId, JsonNode payload);

    /** PUT /api/v1/athlete/{id}/events/{eventId} — 404 lança IntervalsIcuApiException(NOT_FOUND). */
    IcuEventDto atualizarEvento(String apiKey, String externalAthleteId, long eventId, JsonNode payload);

    /** GET /api/v1/athlete/{id}/events?oldest=&newest=. */
    List<IcuEventDto> listarEventos(String apiKey, String externalAthleteId, LocalDate oldest, LocalDate newest);

    /** DELETE /api/v1/athlete/{id}/events/{eventId}. */
    void deletarEvento(String apiKey, String externalAthleteId, long eventId);
}
