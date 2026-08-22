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
    public Optional<IcuAthleteDto> validarToken(String token) {
        try {
            IcuAthleteDto atleta = webClient.get()
                    .uri("/api/v1/athlete/0")
                    .headers(h -> bearer(h, token))
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
    public IcuEventDto criarEvento(String token, String externalAthleteId, JsonNode payload) {
        return executa("criar evento", () -> webClient.post()
                .uri("/api/v1/athlete/{id}/events", externalAthleteId)
                .headers(h -> bearer(h, token))
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
    public IcuEventDto atualizarEvento(String token, String externalAthleteId, long eventId, JsonNode payload) {
        return executa("atualizar evento", () -> webClient.put()
                .uri("/api/v1/athlete/{id}/events/{eventId}", externalAthleteId, eventId)
                .headers(h -> bearer(h, token))
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
    public List<IcuEventDto> listarEventos(String token, String externalAthleteId,
                                           LocalDate oldest, LocalDate newest) {
        return executa("listar eventos", () -> {
            IcuEventDto[] eventos = webClient.get()
                    .uri(uri -> uri.path("/api/v1/athlete/{id}/events")
                            .queryParam("oldest", oldest.toString())
                            .queryParam("newest", newest.toString())
                            .build(externalAthleteId))
                    .headers(h -> bearer(h, token))
                    .retrieve()
                    .bodyToMono(IcuEventDto[].class)
                    .block();
            return eventos == null ? List.<IcuEventDto>of() : Arrays.asList(eventos);
        });
    }

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: External API call (GET activities?oldest&newest)
     * Tenant-aware: NO
     */
    @Override
    public List<IcuActivityDto> listarAtividades(String token, String externalAthleteId,
                                                  LocalDate oldest, LocalDate newest) {
        return executa("listar atividades", () -> {
            IcuActivityDto[] atividades = webClient.get()
                    .uri(uri -> uri.path("/api/v1/athlete/{id}/activities")
                            .queryParam("oldest", oldest.toString())
                            .queryParam("newest", newest.toString())
                            .build(externalAthleteId))
                    .headers(h -> bearer(h, token))
                    .retrieve()
                    .bodyToMono(IcuActivityDto[].class)
                    .block();
            return atividades == null ? List.<IcuActivityDto>of() : Arrays.asList(atividades);
        });
    }

    /**
     * Idempotent: YES — deletar duas vezes é seguro (404 na segunda é tratado pelo chamador).
     * Side Effects: External API call (DELETE events/{id})
     * Tenant-aware: NO
     */
    @Override
    public void deletarEvento(String token, String externalAthleteId, long eventId) {
        executa("deletar evento", () -> webClient.delete()
                .uri("/api/v1/athlete/{id}/events/{eventId}", externalAthleteId, eventId)
                .headers(h -> bearer(h, token))
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
    public IcuActivityDto buscarAtividade(String token, String activityId, boolean comIntervalos) {
        return executa("buscar atividade", () -> webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/v1/activity/{id}");
                    if (comIntervalos) {
                        uriBuilder.queryParam("intervals", "true");
                    }
                    return uriBuilder.build(activityId);
                })
                .headers(h -> bearer(h, token))
                .retrieve()
                .bodyToMono(IcuActivityDto.class)
                .block());
    }

    /**
     * Ponto único de autenticação do client (D1). A doc do provedor é explícita: apps usados por
     * mais de uma pessoa devem usar OAuth e Bearer token. O Basic com o literal {@code API_KEY}
     * que vivia aqui saiu junto com o fluxo de API key — não há convivência entre os dois.
     */
    private void bearer(HttpHeaders headers, String token) {
        headers.setBearerAuth(token);
    }

    /**
     * Idempotent: YES — revogar duas vezes é seguro (a segunda encontra o app já desconectado).
     * Side Effects: External API call (DELETE /api/v1/disconnect-app)
     * Tenant-aware: NO — credencial é do atleta, não do tenant.
     *
     * <p><b>Não propaga falha (D7).</b> Ver JavaDoc da interface: quem chama está desconectando o
     * atleta, e travar isso porque o provedor está fora do ar é pior que não revogar agora.
     */
    @Override
    public void revogarAcesso(String token) {
        try {
            webClient.delete()
                    .uri("/api/v1/disconnect-app")
                    .headers(h -> bearer(h, token))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Acesso revogado no intervals.icu");
        } catch (Exception e) {
            // Sem o token e sem body na mensagem — mesmo cuidado de traduz() (CA10).
            log.warn("Falha ao revogar acesso no intervals.icu (best-effort, desconexão local segue): {}",
                    e.getClass().getSimpleName());
        }
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
