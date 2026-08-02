package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.dto.intervalsicu.IcuAthleteDto;
import br.com.menthoros.backend.dto.intervalsicu.IcuEventDto;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Client HTTP do intervals.icu. Basic auth por chamada (a credencial varia por atleta).
 * Regra de segurança: a API key nunca entra em log, mensagem de exceção ou header logado.
 */
@Slf4j
@Service
public class IntervalsIcuClientImpl implements IntervalsIcuClient {

    private final WebClient webClient;

    public IntervalsIcuClientImpl(@Qualifier("intervalsIcuWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Idempotent: YES — leitura pura na API externa.
     * Side Effects: External API call (GET /api/v1/athlete/0)
     * Tenant-aware: NO — credencial é do atleta, não do tenant.
     */
    @Override
    public Optional<IcuAthleteDto> validarApiKey(String apiKey) {
        try {
            IcuAthleteDto atleta = webClient.get()
                    .uri("/api/v1/athlete/0")
                    .headers(h -> basic(h, apiKey))
                    .retrieve()
                    .bodyToMono(IcuAthleteDto.class)
                    .block();
            return Optional.ofNullable(atleta);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                return Optional.empty();
            }
            throw traduz(e, "validar API key");
        } catch (Exception e) {
            throw new IntervalsIcuApiException("Falha de transporte ao validar API key", e);
        }
    }

    /**
     * Idempotent: NO — cria um evento novo a cada chamada (a API NÃO deduplica por external_id).
     * Side Effects: External API call (POST events)
     * Tenant-aware: NO
     */
    @Override
    public IcuEventDto criarEvento(String apiKey, String externalAthleteId, JsonNode payload) {
        return executa("criar evento", () -> webClient.post()
                .uri("/api/v1/athlete/{id}/events", externalAthleteId)
                .headers(h -> basic(h, apiKey))
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(IcuEventDto.class)
                .block());
    }

    /**
     * Idempotent: YES — PUT substitui o mesmo evento.
     * Side Effects: External API call (PUT events/{id}); 404 se o evento foi apagado pelo atleta.
     * Tenant-aware: NO
     */
    @Override
    public IcuEventDto atualizarEvento(String apiKey, String externalAthleteId, long eventId, JsonNode payload) {
        return executa("atualizar evento", () -> webClient.put()
                .uri("/api/v1/athlete/{id}/events/{eventId}", externalAthleteId, eventId)
                .headers(h -> basic(h, apiKey))
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(IcuEventDto.class)
                .block());
    }

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: External API call (GET events?oldest&newest)
     * Tenant-aware: NO
     */
    @Override
    public List<IcuEventDto> listarEventos(String apiKey, String externalAthleteId,
                                           LocalDate oldest, LocalDate newest) {
        return executa("listar eventos", () -> {
            IcuEventDto[] eventos = webClient.get()
                    .uri(uri -> uri.path("/api/v1/athlete/{id}/events")
                            .queryParam("oldest", oldest.toString())
                            .queryParam("newest", newest.toString())
                            .build(externalAthleteId))
                    .headers(h -> basic(h, apiKey))
                    .retrieve()
                    .bodyToMono(IcuEventDto[].class)
                    .block();
            return eventos == null ? List.<IcuEventDto>of() : Arrays.asList(eventos);
        });
    }

    /**
     * Idempotent: YES — deletar duas vezes é seguro (404 na segunda é tratado pelo chamador).
     * Side Effects: External API call (DELETE events/{id})
     * Tenant-aware: NO
     */
    @Override
    public void deletarEvento(String apiKey, String externalAthleteId, long eventId) {
        executa("deletar evento", () -> webClient.delete()
                .uri("/api/v1/athlete/{id}/events/{eventId}", externalAthleteId, eventId)
                .headers(h -> basic(h, apiKey))
                .retrieve()
                .toBodilessEntity()
                .block());
    }

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: External API call (GET /api/v1/activity/{id})
     * Tenant-aware: NO — credencial é do atleta, não do tenant.
     */
    @Override
    public IcuActivityDto buscarAtividade(String apiKey, String activityId, boolean comIntervalos) {
        return executa("buscar atividade", () -> webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/v1/activity/{id}");
                    if (comIntervalos) {
                        uriBuilder.queryParam("intervals", "true");
                    }
                    return uriBuilder.build(activityId);
                })
                .headers(h -> basic(h, apiKey))
                .retrieve()
                .bodyToMono(IcuActivityDto.class)
                .block());
    }

    private void basic(HttpHeaders headers, String apiKey) {
        headers.setBasicAuth("API_KEY", apiKey);
    }

    private <T> T executa(String operacao, java.util.function.Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (WebClientResponseException e) {
            throw traduz(e, operacao);
        } catch (IntervalsIcuApiException e) {
            throw e;
        } catch (Exception e) {
            // mensagem sem body/headers — nada da credencial ou payload vaza
            throw new IntervalsIcuApiException("Falha de transporte ao " + operacao, e);
        }
    }

    private IntervalsIcuApiException traduz(WebClientResponseException e, String operacao) {
        // NÃO incluir e.getResponseBodyAsString() cru em log de erro por padrão — body é
        // controlado por terceiro; status + operação bastam para diagnóstico.
        log.warn("intervals.icu respondeu {} ao {}", e.getStatusCode().value(), operacao);
        return new IntervalsIcuApiException(e.getStatusCode(),
                "intervals.icu retornou " + e.getStatusCode().value() + " ao " + operacao);
    }
}
