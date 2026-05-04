package br.com.menthoros.backend.services;

import br.com.menthoros.backend.config.StravaProperties;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.impl.StravaOAuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StravaOAuthServiceTest {

    @Mock
    private AtletaRepository atletaRepository;
    @Mock
    private IntegracaoExternaRepository integracaoExternaRepository;
    @Mock
    private WebClient stravaWebClient;

    private StravaProperties stravaProperties;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        stravaProperties = new StravaProperties();
        stravaProperties.setClientId("123");
        stravaProperties.setClientSecret("secret");
        stravaProperties.setRedirectUri("http://localhost:8080/api/strava/callback");
        stravaProperties.setAuthorizationUri("https://www.strava.com/oauth/authorize");
        stravaProperties.setTokenUri("https://www.strava.com/oauth/token");
        stravaProperties.setApiBaseUrl("https://www.strava.com/api/v3");

        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Deve gerar URL de autorização OAuth com parâmetros obrigatórios")
    void shouldBuildAuthorizationUrl() {
        UUID atletaId = UUID.randomUUID();
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(mockAtleta(atletaId, tenantId)));

        StravaOAuthService service = new StravaOAuthServiceImpl(
                stravaProperties,
                atletaRepository,
                integracaoExternaRepository,
                stravaWebClient
        );

        String url = service.getAuthorizationUrl(atletaId);

        assertTrue(url.startsWith("https://www.strava.com/oauth/authorize"));
        assertTrue(url.contains("client_id=123"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("scope=read,activity:read_all"));
        assertTrue(url.contains("state=" + atletaId));
    }

    @Test
    @DisplayName("Deve retornar token atual quando não está próximo de expirar")
    void shouldReturnCurrentTokenWhenValid() {
        UUID atletaId = UUID.randomUUID();
        IntegracaoExterna integracao = new IntegracaoExterna();
        integracao.setAtivo(true);
        integracao.setAccessToken("token-atual");
        integracao.setTokenExpiraEm(Instant.now().plusSeconds(3600));

        when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        StravaOAuthService service = spy(new StravaOAuthServiceImpl(
                stravaProperties,
                atletaRepository,
                integracaoExternaRepository,
                stravaWebClient
        ));

        String token = service.getValidToken(atletaId);

        assertEquals("token-atual", token);
        verify(service, never()).refreshAccessToken(any());
    }

    @Test
    @DisplayName("Deve renovar token quando expiração está dentro da janela de 5 minutos")
    void shouldRefreshTokenWhenNearExpiration() {
        UUID atletaId = UUID.randomUUID();
        IntegracaoExterna integracao = new IntegracaoExterna();
        integracao.setAtivo(true);
        integracao.setAccessToken("token-antigo");
        integracao.setTokenExpiraEm(Instant.now().plusSeconds(60));

        when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        StravaOAuthService service = spy(new StravaOAuthServiceImpl(
                stravaProperties,
                atletaRepository,
                integracaoExternaRepository,
                stravaWebClient
        ));
        doReturn("token-novo").when(service).refreshAccessToken(integracao);

        String token = service.getValidToken(atletaId);

        assertEquals("token-novo", token);
        verify(service).refreshAccessToken(integracao);
    }

    @Test
    @DisplayName("Deve desativar integração e limpar tokens no disconnect")
    void shouldDisconnectAndClearTokens() {
        UUID atletaId = UUID.randomUUID();
        IntegracaoExterna integracao = new IntegracaoExterna();
        integracao.setAtivo(true);
        integracao.setAccessToken("a");
        integracao.setRefreshToken("r");
        integracao.setScopes("read");

        when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        StravaOAuthService service = new StravaOAuthServiceImpl(
                stravaProperties,
                atletaRepository,
                integracaoExternaRepository,
                stravaWebClient
        );

        service.disconnect(atletaId);

        assertFalse(integracao.isAtivo());
        assertNull(integracao.getAccessToken());
        assertNull(integracao.getRefreshToken());
        assertNull(integracao.getScopes());
        assertNull(integracao.getTokenExpiraEm());
        verify(integracaoExternaRepository).save(integracao);
    }

    private Atleta mockAtleta(UUID atletaId, UUID assessoriaId) {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(assessoriaId);
        Atleta atleta = new Atleta();
        atleta.setId(atletaId);
        atleta.setAssessoria(assessoria);
        return atleta;
    }
}
