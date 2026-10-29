package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.core.CacheConfig;
import br.com.menthoros.backend.config.core.CacheProperties;
import br.com.menthoros.backend.config.external.GarminProperties;
import br.com.menthoros.backend.dto.garmin.GarminTokenResponse;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.GarminApiException;
import br.com.menthoros.backend.exception.GarminCanalIndisponivelException;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminClient;
import br.com.menthoros.backend.services.GarminConnectionService;
import br.com.menthoros.backend.services.GarminOAuthService.Resultado;
import br.com.menthoros.backend.services.helper.GarminStateSigner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GarminOAuthServiceImplTest {

    private static final Instant AGORA = Instant.parse("2026-10-26T12:00:00Z");
    private static final String USER_ID = "d3315b1072421d0dd7c8f6b8e1de4df8";

    private GarminProperties props;
    private GarminStateSigner signer;
    private GarminClient client;
    private GarminConnectionService connection;
    private AtletaRepository atletaRepository;
    private IntegracaoExternaRepository integracaoRepository;
    private CaffeineCache pkce;
    private SimpleMeterRegistry meters;
    private GarminOAuthServiceImpl service;

    private UUID atletaId;
    private UUID tenantId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        props = new GarminProperties();
        props.setHabilitado(true);
        props.setClientId("client-id");
        props.setClientSecret("client-secret");
        props.setRedirectUri("http://localhost:8099/api/v1/integracoes/garmin/callback");
        props.setAuthorizationUri("https://connect.garmin.com/oauth2Confirm");

        Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        signer = new GarminStateSigner(props, clock);
        client = mock(GarminClient.class);
        connection = mock(GarminConnectionService.class);
        atletaRepository = mock(AtletaRepository.class);
        integracaoRepository = mock(IntegracaoExternaRepository.class);
        var cacheManager = new CacheConfig(new CacheProperties()).cacheManager();
        pkce = (CaffeineCache) cacheManager.getCache(CacheConfig.GARMIN_PKCE);
        meters = new SimpleMeterRegistry();
        service = new GarminOAuthServiceImpl(props, signer, client, connection, atletaRepository,
                integracaoRepository, cacheManager, meters, clock);

        atletaId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        atleta = Atleta.builder().id(atletaId).assessoria(assessoria).build();
    }

    @Nested
    @DisplayName("gerarAuthorizeUrl")
    class GerarAuthorizeUrl {

        @Test
        @DisplayName("monta a URL PKCE S256 e guarda o verifier sob o state")
        void montaUrlEGuardaVerifier() {
            String url = service.gerarAuthorizeUrl(atletaId);

            var params = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
            assertThat(url).startsWith("https://connect.garmin.com/oauth2Confirm?");
            assertThat(params.getFirst("client_id")).isEqualTo("client-id");
            assertThat(params.getFirst("response_type")).isEqualTo("code");
            assertThat(params.getFirst("code_challenge_method")).isEqualTo("S256");
            String state = params.getFirst("state");
            assertThat(signer.validar(state)).contains(atletaId);

            String verifier = (String) pkce.getNativeCache().asMap().get(state);
            assertThat(verifier).hasSize(64).matches("[A-Za-z0-9\\-._~]{64}");
            assertThat(params.getFirst("code_challenge")).isEqualTo(challenge(verifier));
        }

        @Test
        @DisplayName("flag desligada: lança e não grava no cache")
        void flagDesligada() {
            props.setHabilitado(false);

            assertThatThrownBy(() -> service.gerarAuthorizeUrl(atletaId))
                    .isInstanceOf(GarminCanalIndisponivelException.class);
            assertThat(pkce.getNativeCache().estimatedSize()).isZero();
        }

        @Test
        @DisplayName("sem client secret: lança e não grava no cache")
        void semSecret() {
            props.setClientSecret("");

            assertThatThrownBy(() -> service.gerarAuthorizeUrl(atletaId))
                    .isInstanceOf(GarminCanalIndisponivelException.class);
            assertThat(pkce.getNativeCache().estimatedSize()).isZero();
        }
    }

    @Nested
    @DisplayName("concluir")
    class Concluir {

        private String state;
        private String verifier;

        @BeforeEach
        void autorizar() {
            String url = service.gerarAuthorizeUrl(atletaId);
            state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
            verifier = (String) pkce.getNativeCache().asMap().get(state);
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atleta));
        }

        private void garminResponde() {
            when(client.trocarCode("code-1", verifier)).thenReturn(token());
            when(client.userId("acc")).thenReturn(USER_ID);
            when(client.permissoes("acc")).thenReturn(List.of("WORKOUT_IMPORT", "ACTIVITY_EXPORT"));
            when(integracaoRepository.findActiveByExternalAthleteIdAndPlataforma(USER_ID, FonteDados.GARMIN))
                    .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("sucesso: ativa a conexão e conta 'sucesso'")
        void sucesso() {
            garminResponde();

            assertThat(service.concluir(state, "code-1")).isEqualTo(Resultado.SUCESSO);

            verify(connection).ativar(eq(atleta), eq(tenantId), eq(token()), eq(USER_ID),
                    eq(List.of("WORKOUT_IMPORT", "ACTIVITY_EXPORT")));
            assertThat(meters.counter("garmin.oauth.callback", "resultado", "sucesso").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("corrida entre dois callbacks com o mesmo userId: o índice único vira CONTA_JA_VINCULADA")
        void corridaDeCallbacks() {
            garminResponde();
            doThrow(new org.springframework.dao.DataIntegrityViolationException("ux_integracao_garmin_user_ativo"))
                    .when(connection).ativar(any(), any(), any(), any(), any());

            assertThat(service.concluir(state, "code-1")).isEqualTo(Resultado.CONTA_JA_VINCULADA);
            assertThat(meters.counter("garmin.oauth.callback", "resultado", "erro").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("reconexão do mesmo atleta com o mesmo userId é aceita")
        void mesmoUserIdMesmoAtleta() {
            garminResponde();
            IntegracaoExterna propria = new IntegracaoExterna();
            propria.setAtleta(atleta);
            when(integracaoRepository.findActiveByExternalAthleteIdAndPlataforma(USER_ID, FonteDados.GARMIN))
                    .thenReturn(Optional.of(propria));

            assertThat(service.concluir(state, "code-1")).isEqualTo(Resultado.SUCESSO);
        }

        @Test
        @DisplayName("state inválido: nada acontece")
        void stateInvalido() {
            assertThat(service.concluir("lixo", "code-1")).isEqualTo(Resultado.STATE_INVALIDO);

            verifyNoInteractions(client, connection);
            assertThat(meters.counter("garmin.oauth.callback", "resultado", "erro").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("state válido sem verifier no cache: STATE_INVALIDO")
        void verifierAusente() {
            String semVerifier = signer.assinar(atletaId);

            assertThat(service.concluir(semVerifier, "code-1")).isEqualTo(Resultado.STATE_INVALIDO);

            verifyNoInteractions(client, connection);
        }

        @Test
        @DisplayName("segundo uso do mesmo state é rejeitado")
        void segundoUso() {
            garminResponde();
            service.concluir(state, "code-1");

            assertThat(service.concluir(state, "code-1")).isEqualTo(Resultado.STATE_INVALIDO);

            verify(client, times(1)).trocarCode(anyString(), anyString());
            verify(connection, times(1)).ativar(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("userId ativo em outro atleta (qualquer tenant): CONTA_JA_VINCULADA, nada gravado")
        void userIdDeOutroAtleta() {
            garminResponde();
            IntegracaoExterna alheia = new IntegracaoExterna();
            alheia.setAtleta(Atleta.builder().id(UUID.randomUUID()).build());
            alheia.setTenantId(UUID.randomUUID());
            when(integracaoRepository.findActiveByExternalAthleteIdAndPlataforma(USER_ID, FonteDados.GARMIN))
                    .thenReturn(Optional.of(alheia));

            assertThat(service.concluir(state, "code-1")).isEqualTo(Resultado.CONTA_JA_VINCULADA);

            verify(connection, never()).ativar(any(), any(), any(), any(), any());
            verify(client, never()).removerRegistro(anyString());
        }

        @Test
        @DisplayName("sem code (atleta recusou): DENIED, Garmin não é chamado, verifier consumido")
        void semCode() {
            assertThat(service.concluir(state, null)).isEqualTo(Resultado.DENIED);

            verifyNoInteractions(client, connection);
            assertThat(pkce.getNativeCache().asMap().get(state)).isNull();
            assertThat(meters.counter("garmin.oauth.callback", "resultado", "recusado").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Garmin recusa o code: FALHA_NA_TROCA, nada gravado")
        void garminRecusa() {
            when(client.trocarCode("code-1", verifier))
                    .thenThrow(new GarminApiException(HttpStatus.BAD_REQUEST, "Garmin retornou 400"));

            assertThat(service.concluir(state, "code-1")).isEqualTo(Resultado.FALHA_NA_TROCA);

            verifyNoInteractions(connection);
        }

        @Test
        @DisplayName("atleta inexistente: ATLETA_NAO_ENCONTRADO")
        void atletaInexistente() {
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.empty());

            assertThat(service.concluir(state, "code-1")).isEqualTo(Resultado.ATLETA_NAO_ENCONTRADO);

            verifyNoInteractions(client, connection);
        }
    }

    @Nested
    @DisplayName("tokenValido / renovar")
    class TokenValido {

        private IntegracaoExterna conexao(Instant tokenExpiraEm) {
            IntegracaoExterna c = new IntegracaoExterna();
            c.setAtleta(atleta);
            c.setPlataforma(FonteDados.GARMIN);
            c.setAtivo(true);
            c.setAccessToken("acc-velho");
            c.setRefreshToken("ref-velho");
            c.setTokenExpiraEm(tokenExpiraEm);
            c.setRefreshTokenExpiraEm(AGORA.plusSeconds(1000));
            return c;
        }

        @Test
        @DisplayName("token com mais de 600 s de vida: devolve o atual sem chamar o Garmin")
        void vigente() {
            IntegracaoExterna c = conexao(AGORA.plusSeconds(601));

            assertThat(service.tokenValido(c)).isEqualTo("acc-velho");

            verifyNoInteractions(client);
            verify(integracaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("token dentro da margem de 600 s: renova e grava o par novo num único save")
        void dentroDaMargem() {
            IntegracaoExterna c = conexao(AGORA.plusSeconds(600));
            when(client.renovar("ref-velho")).thenReturn(
                    new GarminTokenResponse("acc-novo", "ref-novo", "Bearer", 86400, 7775999L, null));

            assertThat(service.tokenValido(c)).isEqualTo("acc-novo");

            ArgumentCaptor<IntegracaoExterna> salvo = ArgumentCaptor.forClass(IntegracaoExterna.class);
            verify(integracaoRepository, times(1)).save(salvo.capture());
            assertThat(salvo.getValue().getAccessToken()).isEqualTo("acc-novo");
            assertThat(salvo.getValue().getRefreshToken()).isEqualTo("ref-novo");
            assertThat(salvo.getValue().getTokenExpiraEm()).isEqualTo(AGORA.plusSeconds(86400));
            assertThat(salvo.getValue().getRefreshTokenExpiraEm()).isEqualTo(AGORA.plusSeconds(7775999));
            assertThat(meters.counter("garmin.token.refresh", "resultado", "sucesso").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("tokenExpiraEm nulo é tratado como expirado")
        void expiracaoNula() {
            IntegracaoExterna c = conexao(null);
            when(client.renovar("ref-velho")).thenReturn(
                    new GarminTokenResponse("acc-novo", "ref-novo", "Bearer", 86400, null, null));

            assertThat(service.tokenValido(c)).isEqualTo("acc-novo");
            assertThat(c.getRefreshTokenExpiraEm()).isEqualTo(AGORA.plusSeconds(7776000));
        }

        @Test
        @DisplayName("400 e 401 no refresh: conexão inativa, credenciais e expirações nulas, 'falha' contada")
        void refreshRecusado() {
            for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED)) {
                IntegracaoExterna c = conexao(AGORA.minusSeconds(1));
                doThrow(new GarminApiException(status, "Garmin retornou")).when(client).renovar("ref-velho");

                assertThatThrownBy(() -> service.tokenValido(c)).isInstanceOf(IllegalStateException.class);

                assertThat(c.isAtivo()).isFalse();
                assertThat(c.getAccessToken()).isNull();
                assertThat(c.getRefreshToken()).isNull();
                assertThat(c.getTokenExpiraEm()).isNull();
                assertThat(c.getRefreshTokenExpiraEm()).isNull();
                assertThat(c.getLastSyncError()).isNotBlank();
            }
            verify(integracaoRepository, times(2)).save(any());
            assertThat(meters.counter("garmin.token.refresh", "resultado", "falha").count()).isEqualTo(2);
        }

        private IntegracaoExterna persistida(UUID id, String access, String refresh) {
            IntegracaoExterna c = conexao(AGORA.plusSeconds(80000));
            c.setId(id);
            c.setAccessToken(access);
            c.setRefreshToken(refresh);
            return c;
        }

        @Test
        @DisplayName("outro processo já renovou (linha no banco tem outro refresh token): usa o token dele, sem chamar o Garmin")
        void outroJaRenovouAntes() {
            UUID id = UUID.randomUUID();
            IntegracaoExterna velha = conexao(AGORA.minusSeconds(1));
            velha.setId(id);
            when(integracaoRepository.findById(id))
                    .thenReturn(Optional.of(persistida(id, "acc-do-outro", "ref-do-outro")));

            assertThat(service.tokenValido(velha)).isEqualTo("acc-do-outro");

            verifyNoInteractions(client);
            verify(integracaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("400 porque outro processo girou o refresh token no meio: não revoga, devolve o token novo")
        void corridaDeRefresh() {
            UUID id = UUID.randomUUID();
            IntegracaoExterna velha = conexao(AGORA.minusSeconds(1));
            velha.setId(id);
            // 1ª leitura: ainda o token velho; 2ª (após o 400): o outro processo já gravou o novo.
            when(integracaoRepository.findById(id)).thenReturn(
                    Optional.of(velha), Optional.of(persistida(id, "acc-do-outro", "ref-do-outro")));
            doThrow(new GarminApiException(HttpStatus.BAD_REQUEST, "Garmin retornou")).when(client).renovar("ref-velho");

            assertThat(service.tokenValido(velha)).isEqualTo("acc-do-outro");

            assertThat(velha.isAtivo()).isTrue();
            verify(integracaoRepository, never()).save(any());
            assertThat(meters.counter("garmin.token.refresh", "resultado", "falha").count()).isZero();
        }

        @Test
        @DisplayName("500 no refresh: propaga e deixa a conexão intacta")
        void falhaTransitoria() {
            IntegracaoExterna c = conexao(AGORA.minusSeconds(1));
            when(client.renovar("ref-velho"))
                    .thenThrow(new GarminApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Garmin retornou 500"));

            assertThatThrownBy(() -> service.tokenValido(c)).isInstanceOf(GarminApiException.class);

            assertThat(c.isAtivo()).isTrue();
            assertThat(c.getRefreshToken()).isEqualTo("ref-velho");
            verify(integracaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("renovar força o refresh mesmo com o access token vigente")
        void renovarForcado() {
            IntegracaoExterna c = conexao(AGORA.plusSeconds(80000));
            when(client.renovar("ref-velho")).thenReturn(
                    new GarminTokenResponse("acc-novo", "ref-novo", "Bearer", 86400, 7775999L, null));

            assertThat(service.renovar(c)).isEqualTo("acc-novo");
        }
    }

    private static GarminTokenResponse token() {
        return new GarminTokenResponse("acc", "ref", "Bearer", 86400, 7775999L, "PARTNER_READ");
    }

    private static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
