package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
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

    /** PUT /api/v1/athlete/{id}/events/{eventId} — erro HTTP vira IntervalsIcuApiException(status, mensagem). */
    IcuEventDto atualizarEvento(String apiKey, String externalAthleteId, long eventId, JsonNode payload);

    /**
     * GET /api/v1/activity/{id}?intervals={comIntervalos} — erro HTTP vira
     * IntervalsIcuApiException(status, mensagem).
     *
     * <p>Sem {@code comIntervalos=true} a chave {@code icu_intervals} <b>nem aparece</b> no corpo —
     * não é uma lista vazia, é ausência. Não há sobrecarga sem o parâmetro de propósito: um
     * chamador que esquecesse de pedir os intervalos reintroduziria silenciosamente o defeito de
     * treinos sem etapas.
     */
    IcuActivityDto buscarAtividade(String apiKey, String activityId, boolean comIntervalos);

    /** GET /api/v1/athlete/{id}/events?oldest=&newest=. */
    List<IcuEventDto> listarEventos(String apiKey, String externalAthleteId, LocalDate oldest, LocalDate newest);

    /** DELETE /api/v1/athlete/{id}/events/{eventId}. */
    void deletarEvento(String apiKey, String externalAthleteId, long eventId);
}
