package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.external.GarminProperties;
import br.com.menthoros.backend.dto.garmin.GarminTokenResponse;
import br.com.menthoros.backend.exception.GarminApiException;
import br.com.menthoros.backend.services.GarminClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
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
            throw new GarminApiException("Falha de transporte ao " + operacao, e);
        }
    }
}
