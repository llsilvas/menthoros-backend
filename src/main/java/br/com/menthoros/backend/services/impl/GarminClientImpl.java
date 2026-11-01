package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.external.GarminProperties;
import br.com.menthoros.backend.dto.garmin.GarminSchedule;
import br.com.menthoros.backend.dto.garmin.GarminTokenResponse;
import br.com.menthoros.backend.exception.GarminApiException;
import br.com.menthoros.backend.services.GarminClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Client HTTP do Garmin. Regra de segurança: token, code, verifier e client secret nunca entram em
 * log ou mensagem de exceção — só status + operação.
 */
@Slf4j
@Service
public class GarminClientImpl implements GarminClient {

    private final WebClient webClient;
    private final GarminProperties properties;

    public GarminClientImpl(@Qualifier("garminWebClient") WebClient webClient, GarminProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    /**
     * Idempotent: NO — o Garmin invalida o code no primeiro uso.
     * Side Effects: External API call (POST token endpoint)
     * Tenant-aware: NO
     */
    @Override
    public GarminTokenResponse trocarCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = credenciais();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("code_verifier", codeVerifier);
        form.add("redirect_uri", properties.getRedirectUri());
        return token("trocar code", form);
    }

    /**
     * Idempotent: NO — o refresh token é rotativo; o anterior morre na troca.
     * Side Effects: External API call (POST token endpoint)
     * Tenant-aware: NO
     */
    @Override
    public GarminTokenResponse renovar(String refreshToken) {
        MultiValueMap<String, String> form = credenciais();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return token("renovar token", form);
    }

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: External API call (GET /partner-gateway/rest/user/id)
     * Tenant-aware: NO
     */
    @Override
    public String userId(String token) {
        JsonNode corpo = executa("buscar userId", () -> webClient.get()
                .uri("/partner-gateway/rest/user/id")
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
        if (corpo == null || !corpo.hasNonNull("userId")) {
            throw new GarminApiException(null, "Garmin não devolveu userId");
        }
        return corpo.get("userId").asText();
    }

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: External API call (GET /partner-gateway/rest/user/permissions)
     * Tenant-aware: NO
     */
    @Override
    public List<String> permissoes(String token) {
        JsonNode corpo = executa("buscar permissões", () -> webClient.get()
                .uri("/partner-gateway/rest/user/permissions")
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
        List<String> permissoes = new ArrayList<>();
        if (corpo != null && corpo.has("permissions")) {
            corpo.get("permissions").forEach(p -> permissoes.add(p.asText()));
        }
        return permissoes;
    }

    /**
     * Idempotent: YES — remover duas vezes é seguro (a segunda encontra o registro ausente).
     * Side Effects: External API call (DELETE /partner-gateway/rest/user/registration)
     * Tenant-aware: NO
     */
    @Override
    public void removerRegistro(String token) {
        executa("remover registro", () -> webClient.delete()
                .uri("/partner-gateway/rest/user/registration")
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity()
                .block());
    }

    private static final String WORKOUT = "/training-api/workout/v2/{id}";
    private static final String SCHEDULE = "/training-api/schedule";
    private static final String SCHEDULE_POR_ID = SCHEDULE + "/{id}";

    /**
     * Idempotent: NO — cada chamada cria um workout novo.
     * Side Effects: External API call (POST create workout)
     * Tenant-aware: NO
     */
    @Override
    public Long criarWorkout(String token, JsonNode workout) {
        JsonNode corpo = executa("criar workout", () -> webClient.post()
                .uri(properties.getWorkoutCreatePath())
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(workout)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
        return idObrigatorio(corpo, "workoutId", "criar workout");
    }

    /**
     * Idempotent: YES — PUT com o mesmo corpo converge para o mesmo workout.
     * Side Effects: External API call (PUT workout)
     * Tenant-aware: NO
     */
    @Override
    public void atualizarWorkout(String token, Long workoutId, JsonNode workout) {
        executa("atualizar workout", () -> webClient.put()
                .uri(WORKOUT, workoutId)
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(workout)
                .retrieve()
                .toBodilessEntity()
                .block());
    }

    /**
     * Idempotent: YES — a segunda chamada devolve 404, que sobe para o chamador.
     * Side Effects: External API call (DELETE workout)
     * Tenant-aware: NO
     */
    @Override
    public void deletarWorkout(String token, Long workoutId) {
        executa("deletar workout", () -> webClient.delete()
                .uri(WORKOUT, workoutId)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity()
                .block());
    }

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: External API call (GET workout)
     * Tenant-aware: NO
     */
    @Override
    public JsonNode buscarWorkout(String token, Long workoutId) {
        return executa("buscar workout", () -> webClient.get()
                .uri(WORKOUT, workoutId)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
    }

    /**
     * Idempotent: NO — cada chamada cria um schedule novo.
     * Side Effects: External API call (POST schedule)
     * Tenant-aware: NO
     */
    @Override
    public Long criarSchedule(String token, Long workoutId, LocalDate data) {
        JsonNode corpo = executa("criar schedule", () -> webClient.post()
                .uri(SCHEDULE)
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("workoutId", workoutId, "date", data.toString()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
        return idObrigatorio(corpo, "scheduleId", "criar schedule");
    }

    /**
     * Idempotent: YES — PUT com o mesmo corpo converge para o mesmo schedule.
     * Side Effects: External API call (PUT schedule)
     * Tenant-aware: NO
     */
    @Override
    public void atualizarSchedule(String token, Long scheduleId, Long workoutId, LocalDate data) {
        executa("atualizar schedule", () -> webClient.put()
                .uri(SCHEDULE_POR_ID, scheduleId)
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("workoutId", workoutId, "date", data.toString()))
                .retrieve()
                .toBodilessEntity()
                .block());
    }

    /**
     * Idempotent: YES — a segunda chamada devolve 404, que sobe para o chamador.
     * Side Effects: External API call (DELETE schedule)
     * Tenant-aware: NO
     */
    @Override
    public void deletarSchedule(String token, Long scheduleId) {
        executa("deletar schedule", () -> webClient.delete()
                .uri(SCHEDULE_POR_ID, scheduleId)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity()
                .block());
    }

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: External API call (GET schedule)
     * Tenant-aware: NO
     */
    @Override
    public List<GarminSchedule> listarSchedules(String token, LocalDate inicio, LocalDate fim) {
        JsonNode corpo = executa("listar schedules", () -> webClient.get()
                .uri(u -> u.path(SCHEDULE)
                        .queryParam("startDate", inicio)
                        .queryParam("endDate", fim)
                        .build())
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());
        List<GarminSchedule> schedules = new ArrayList<>();
        if (corpo != null && corpo.isArray()) {
            corpo.forEach(n -> schedules.add(new GarminSchedule(
                    n.path("scheduleId").asLong(), n.path("workoutId").asLong(), LocalDate.parse(n.path("date").asText()))));
        }
        return schedules;
    }

    private Long idObrigatorio(JsonNode corpo, String campo, String operacao) {
        if (corpo == null || !corpo.hasNonNull(campo)) {
            // Só nomes de campo: o corpo é de terceiro e não entra no log.
            log.warn("Garmin respondeu 2xx sem {} ao {} (corpo {}, campos: {})", campo, operacao,
                    corpo == null ? "vazio" : corpo.getNodeType(), corpo == null ? List.of() : corpo.properties().stream().map(Map.Entry::getKey).toList());
            throw new GarminApiException(null, "Garmin não devolveu " + campo + " ao " + operacao);
        }
        return corpo.get(campo).asLong();
    }

    private MultiValueMap<String, String> credenciais() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        return form;
    }

    private GarminTokenResponse token(String operacao, MultiValueMap<String, String> form) {
        GarminTokenResponse resposta = executa(operacao, () -> webClient.post()
                .uri(properties.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(GarminTokenResponse.class)
                .block());
        if (resposta == null || resposta.accessToken() == null) {
            throw new GarminApiException(null, "Garmin não devolveu access_token ao " + operacao);
        }
        return resposta;
    }

    private <T> T executa(String operacao, Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (WebClientResponseException e) {
            // Sem body na mensagem: é controlado por terceiro e pode ecoar o form (token/secret).
            log.warn("Garmin respondeu {} ao {}", e.getStatusCode().value(), operacao);
            throw new GarminApiException(e.getStatusCode(),
                    "Garmin retornou " + e.getStatusCode().value() + " ao " + operacao);
        } catch (GarminApiException e) {
            throw e;
        } catch (Exception e) {
            // Só tipos: a mensagem de decode pode trazer trecho do corpo, e a de request, a URL.
            Throwable raiz = NestedExceptionUtils.getMostSpecificCause(e);
            log.warn("Falha de transporte ao {}: {} (causa: {})", operacao,
                    e.getClass().getSimpleName(), raiz.getClass().getSimpleName());
            throw new GarminApiException("Falha de transporte ao " + operacao, e);
        }
    }
}
