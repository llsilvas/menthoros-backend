package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.garmin.GarminSchedule;
import br.com.menthoros.backend.dto.garmin.GarminTokenResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;

/**
 * Client HTTP do canal direto Garmin (D1). Token OAuth2 Bearer é por atleta e vai por chamada.
 * Erro HTTP vira {@link br.com.menthoros.backend.exception.GarminApiException} com status;
 * falha de transporte vira a mesma exceção com status nulo. Nenhuma mensagem carrega token.
 * Workout e schedule (Training API V2) e download do arquivo de atividade (Activity API) estão aqui.
 */
public interface GarminClient {

    /** POST token endpoint, {@code grant_type=authorization_code} + {@code code_verifier} (PKCE). */
    GarminTokenResponse trocarCode(String code, String codeVerifier);

    /** POST token endpoint, {@code grant_type=refresh_token}. O refresh token devolvido é novo (rotativo). */
    GarminTokenResponse renovar(String refreshToken);

    /** GET /partner-gateway/rest/user/id — identificador estável do usuário Garmin. */
    String userId(String token);

    /** GET /partner-gateway/rest/user/permissions — ex.: {@code ACTIVITY_EXPORT}, {@code WORKOUT_IMPORT}. */
    List<String> permissoes(String token);

    /**
     * DELETE /partner-gateway/rest/user/registration — remove o registro do usuário no app
     * (exigência da licença ao desconectar). Lança em falha; quem desconecta decide o best-effort.
     */
    void removerRegistro(String token);

    /** POST workout — devolve o {@code workoutId}. 412 = atleta sem {@code WORKOUT_IMPORT}; 429 = cota. */
    Long criarWorkout(String token, JsonNode workout);

    /** PUT workout/{id}. */
    void atualizarWorkout(String token, Long workoutId, JsonNode workout);

    /** DELETE workout/{id}. 404 sobe como {@code GarminApiException}; quem apaga decide tratar como já apagado. */
    void deletarWorkout(String token, Long workoutId);

    /** GET workout/{id} — usado para conferir {@code workoutProvider} antes de apagar um órfão. */
    JsonNode buscarWorkout(String token, Long workoutId);

    /** POST schedule {workoutId, date} — devolve o {@code scheduleId}. */
    Long criarSchedule(String token, Long workoutId, LocalDate data);

    /** PUT schedule/{id}. */
    void atualizarSchedule(String token, Long scheduleId, Long workoutId, LocalDate data);

    /** DELETE schedule/{id}. 404 sobe como {@code GarminApiException}. */
    void deletarSchedule(String token, Long scheduleId);

    /** GET schedule?startDate&endDate (inclusive nas duas pontas). */
    List<GarminSchedule> listarSchedules(String token, LocalDate inicio, LocalDate fim);

    /**
     * GET no {@code callbackURL} de um ping {@code activityFiles}, com o access token do atleta no
     * {@code Authorization} — sem ele o Garmin responde 400. O link vale uma vez (segundo download = 410). Antes de qualquer requisição exige {@code https}, host
     * {@code apis.garmin.com} e path {@code /wellness-api/rest/activityFile}; fora disso lança
     * {@link IllegalArgumentException} sem chamar nada (SSRF: a URL vem de um request externo).
     */
    byte[] baixarArquivo(String callbackUrl, String token);
}
