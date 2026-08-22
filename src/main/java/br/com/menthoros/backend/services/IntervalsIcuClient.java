package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.dto.intervalsicu.IcuAthleteDto;
import br.com.menthoros.backend.dto.intervalsicu.IcuEventDto;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Client HTTP do intervals.icu. Credencial (token OAuth2 Bearer) é por atleta e vai por chamada.
 */
public interface IntervalsIcuClient {

    /** GET /api/v1/athlete/0 — 200 retorna o atleta; 401/403 retorna vazio. */
    Optional<IcuAthleteDto> validarToken(String token);

    /** POST /api/v1/athlete/{id}/events. */
    IcuEventDto criarEvento(String token, String externalAthleteId, JsonNode payload);

    /** PUT /api/v1/athlete/{id}/events/{eventId} — erro HTTP vira IntervalsIcuApiException(status, mensagem). */
    IcuEventDto atualizarEvento(String token, String externalAthleteId, long eventId, JsonNode payload);

    /**
     * GET /api/v1/activity/{id}?intervals={comIntervalos} — erro HTTP vira
     * IntervalsIcuApiException(status, mensagem).
     *
     * <p>Sem {@code comIntervalos=true} a chave {@code icu_intervals} <b>nem aparece</b> no corpo —
     * não é uma lista vazia, é ausência. Não há sobrecarga sem o parâmetro de propósito: um
     * chamador que esquecesse de pedir os intervalos reintroduziria silenciosamente o defeito de
     * treinos sem etapas.
     */
    IcuActivityDto buscarAtividade(String token, String activityId, boolean comIntervalos);

    /** GET /api/v1/athlete/{id}/events?oldest=&newest=. */
    List<IcuEventDto> listarEventos(String token, String externalAthleteId, LocalDate oldest, LocalDate newest);

    /**
     * GET /api/v1/athlete/{id}/activities?oldest=&newest= — erro HTTP vira
     * IntervalsIcuApiException(status, mensagem), sem tradução aqui: retryable vs. permanente é
     * decisão do scheduler.
     *
     * <p>Contrato observado contra a API real (gate 0.2, 2026-08-22): sem paginação, filtro por
     * data local inclusivo nas duas pontas, ordem <b>decrescente</b>, e o corpo é o summary completo
     * <b>sem</b> {@code icu_intervals} — os laps só vêm por {@link #buscarAtividade} com
     * {@code comIntervalos=true}.
     */
    List<IcuActivityDto> listarAtividades(String token, String externalAthleteId, LocalDate oldest, LocalDate newest);

    /** DELETE /api/v1/athlete/{id}/events/{eventId}. */
    void deletarEvento(String token, String externalAthleteId, long eventId);

    /**
     * DELETE /api/v1/disconnect-app — revoga o acesso deste app no provedor, com o Bearer do
     * próprio atleta.
     *
     * <p><b>Best-effort por decisão (D7): nunca lança.</b> Quem chama está desconectando o atleta,
     * e a intenção dele é sair. Se o provedor estiver fora do ar, propagar a falha travaria a
     * desconexão local e deixaria o Menthoros continuar usando um token que o atleta já quis
     * descartar — o pior dos dois mundos. A falha é logada (sem o token) e engolida.
     */
    void revogarAcesso(String token);
}
