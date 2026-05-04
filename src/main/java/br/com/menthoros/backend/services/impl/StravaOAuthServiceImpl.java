package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.services.StravaOAuthService;

import br.com.menthoros.backend.config.StravaProperties;
import br.com.menthoros.backend.dto.strava.StravaTokenResponse;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StravaOAuthServiceImpl implements StravaOAuthService {

    private static final FonteDados STRAVA = FonteDados.STRAVA;

    private final StravaProperties stravaProperties;
    private final AtletaRepository atletaRepository;
    private final IntegracaoExternaRepository integracaoExternaRepository;

    @Qualifier("stravaWebClient")
    private final WebClient stravaWebClient;

    @Transactional(readOnly = true)
    public String getAuthorizationUrl(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado"));

        return UriComponentsBuilder.fromUriString(stravaProperties.getAuthorizationUri())
                .queryParam("client_id", stravaProperties.getClientId())
                .queryParam("redirect_uri", stravaProperties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("approval_prompt", "auto")
                .queryParam("scope", "read,activity:read_all")
                .queryParam("state", atletaId.toString())
                .build(true)
                .toUriString();
    }

    @Transactional
    public IntegracaoExterna exchangeCodeForToken(String code, Atleta atleta) {
        StravaTokenResponse tokenResponse = callTokenEndpoint(buildAuthorizationCodeBody(code));

        IntegracaoExterna integracao = integracaoExternaRepository
                .findByAtletaIdAndPlataforma(atleta.getId(), STRAVA)
                .orElse(new IntegracaoExterna());

        integracao.setAtleta(atleta);
        integracao.setPlataforma(STRAVA);
        integracao.setTenantId(atleta.getAssessoria().getId());
        integracao.setAtivo(true);
        applyTokenResponse(integracao, tokenResponse);

        return integracaoExternaRepository.save(integracao);
    }

    @Transactional
    public String refreshAccessToken(IntegracaoExterna integracao) {
        StravaTokenResponse tokenResponse = callTokenEndpoint(buildRefreshTokenBody(integracao.getRefreshToken()));
        applyTokenResponse(integracao, tokenResponse);
        integracao.setAtivo(true);
        integracaoExternaRepository.save(integracao);
        return tokenResponse.accessToken();
    }

    @Transactional
    public String getValidToken(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        IntegracaoExterna integracao = integracaoExternaRepository
                .findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, STRAVA, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Integração Strava ativa não encontrada para atleta"));

        Instant expiresAt = integracao.getTokenExpiraEm();
        Instant threshold = Instant.now().plusSeconds(300);
        if (expiresAt != null && !expiresAt.isAfter(threshold)) {
            try {
                return refreshAccessToken(integracao);
            } catch (RuntimeException ex) {
                log.warn("Falha ao renovar token Strava para atleta {}: {}", atletaId, ex.getMessage());
                integracao.setAtivo(false);
                integracao.setAccessToken(null);
                integracao.setRefreshToken(null);
                integracaoExternaRepository.save(integracao);
                throw new IllegalStateException("Token Strava expirado. O atleta precisa reconectar a integração.");
            }
        }

        return integracao.getAccessToken();
    }

    @Transactional(readOnly = true)
    public boolean isConnected(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return integracaoExternaRepository
                .findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, STRAVA, tenantId)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStatus(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return integracaoExternaRepository
                .findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, STRAVA, tenantId)
                .<Map<String, Object>>map(i -> Map.of(
                        "connected", true,
                        "externalAthleteId", i.getExternalAthleteId(),
                        "ultimaSincronizacao", i.getUltimaSincronizacao()
                ))
                .orElseGet(() -> Map.of("connected", false));
    }

    @Transactional
    public void disconnect(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        IntegracaoExterna integracao = integracaoExternaRepository
                .findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, STRAVA, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Integração Strava ativa não encontrada para atleta"));

        integracao.setAtivo(false);
        integracao.setAccessToken(null);
        integracao.setRefreshToken(null);
        integracao.setScopes(null);
        integracao.setTokenExpiraEm(null);
        integracaoExternaRepository.save(integracao);
    }

    @Transactional(readOnly = true)
    public Atleta findAtletaForCallback(UUID atletaId) {
        return atletaRepository.findByIdBasic(atletaId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado"));
    }

    private StravaTokenResponse callTokenEndpoint(MultiValueMap<String, String> formData) {
        return stravaWebClient.post()
                .uri(stravaProperties.getTokenUri())
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(StravaTokenResponse.class)
                .block();
    }

    private MultiValueMap<String, String> buildAuthorizationCodeBody(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", stravaProperties.getClientId());
        body.add("client_secret", stravaProperties.getClientSecret());
        body.add("code", code);
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", stravaProperties.getRedirectUri());
        return body;
    }

    private MultiValueMap<String, String> buildRefreshTokenBody(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", stravaProperties.getClientId());
        body.add("client_secret", stravaProperties.getClientSecret());
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);
        return body;
    }

    private void applyTokenResponse(IntegracaoExterna integracao, StravaTokenResponse tokenResponse) {
        if (tokenResponse == null) {
            throw new IllegalStateException("Resposta vazia ao obter token do Strava");
        }
        integracao.setAccessToken(tokenResponse.accessToken());
        integracao.setRefreshToken(tokenResponse.refreshToken());
        integracao.setTokenExpiraEm(tokenResponse.expiresAt() != null ? Instant.ofEpochSecond(tokenResponse.expiresAt()) : null);
        if (tokenResponse.athlete() != null && tokenResponse.athlete().id() != null) {
            integracao.setExternalAthleteId(String.valueOf(tokenResponse.athlete().id()));
        }
    }
}
