package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.external.IntervalsIcuProperties;
import br.com.menthoros.backend.dto.intervalsicu.IcuAthleteDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.IntervalsIcuOAuthService.Resultado;
import br.com.menthoros.backend.services.helper.IntervalsIcuStateSigner;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IntervalsIcuOAuthServiceImplTest {

    private static final String SECRET = "segredo-do-app-663";
    private static final String CLIENT_ID = "663";
    private static final Instant AGORA = Instant.parse("2026-08-21T12:00:00Z");
    private static final String EXTERNAL_ATHLETE_ID = "i641775";

    private WireMockServer wireMock;
    private IntervalsIcuProperties properties;
    private IntervalsIcuStateSigner signer;

    private AtletaRepository atletaRepository;
    private IntegracaoExternaRepository integracaoRepository;
    private AtletaProgressService atletaProgressService;
    private IntervalsIcuConnectionService connectionService;
    private br.com.menthoros.backend.services.IntervalsIcuClient intervalsIcuClient;

    private IntervalsIcuOAuthServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        properties = new IntervalsIcuProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setClientId(CLIENT_ID);
        properties.setClientSecret(SECRET);
        properties.setRedirectUri("http://localhost:8099/api/v1/integracoes/intervals-icu/callback");
        properties.setAuthorizationUri("https://intervals.icu/oauth/authorize");
        properties.setTokenUri(wireMock.baseUrl() + "/api/oauth/token");
        properties.setScope("ACTIVITY:READ,CALENDAR:WRITE");

        signer = new IntervalsIcuStateSigner(properties, Clock.fixed(AGORA, ZoneOffset.UTC));

        atletaRepository = mock(AtletaRepository.class);
        integracaoRepository = mock(IntegracaoExternaRepository.class);
        atletaProgressService = mock(AtletaProgressService.class);
        connectionService = mock(IntervalsIcuConnectionService.class);
        intervalsIcuClient = mock(br.com.menthoros.backend.services.IntervalsIcuClient.class);

        WebClient webClient = WebClient.builder().baseUrl(wireMock.baseUrl()).build();

        service = new IntervalsIcuOAuthServiceImpl(
                properties, signer, webClient, atletaRepository, integracaoRepository,
                atletaProgressService, connectionService, intervalsIcuClient);

        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        wireMock.stop();
    }

    private Atleta atletaComTenant() {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        return Atleta.builder().id(atletaId).assessoria(assessoria).build();
    }

    private void stubTokenOk() {
        wireMock.stubFor(post(urlEqualTo("/api/oauth/token"))
                .willReturn(okJson("""
                        {"token_type":"Bearer","access_token":"tok-abc",
                         "scope":"ACTIVITY:READ,CALENDAR:WRITE",
                         "athlete":{"id":"%s","name":"Leandro"}}
                        """.formatted(EXTERNAL_ATHLETE_ID))));
    }

    @Nested
    @DisplayName("getAuthorizationUrl")
    class GetAuthorizationUrl {

        @Test
        @DisplayName("monta a URL com client_id, redirect_uri, scope e state assinado")
        void montaUrlCompleta() {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);

            String url = service.getAuthorizationUrl();

            assertThat(url).startsWith("https://intervals.icu/oauth/authorize");
            assertThat(url).contains("client_id=663");
            assertThat(url).contains("scope=ACTIVITY:READ,CALENDAR:WRITE");
            assertThat(url).contains("state=");
        }

        // D4: os dois escopos desde o primeiro dia. CALENDAR:WRITE cobre o push de treino
        // planejado, que já roda em produção — pedir só ACTIVITY:READ quebraria esse canal, e
        // o sintoma só apareceria na primeira aprovação de plano depois do deploy.
        @Test
        @DisplayName("pede ACTIVITY:READ e CALENDAR:WRITE")
        void pedeOsDoisEscopos() {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);

            assertThat(service.getAuthorizationUrl()).contains("ACTIVITY:READ", "CALENDAR:WRITE");
        }

        @Test
        @DisplayName("o state gerado é válido e aponta para o atleta autenticado")
        void stateGeradoEhValido() {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);

            String url = service.getAuthorizationUrl();
            String state = url.substring(url.indexOf("state=") + 6);

            assertThat(signer.validar(state)).contains(atletaId);
        }

        @Test
        @DisplayName("não persiste nada")
        void naoPersisteNada() {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);

            service.getAuthorizationUrl();

            verify(integracaoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("exchangeCodeForToken")
    class ExchangeCodeForToken {

        @Test
        @DisplayName("caminho feliz persiste accessToken, scopes e externalAthleteId")
        void caminhoFeliz() {
            stubTokenOk();
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atletaComTenant()));
            when(integracaoRepository.findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(
                    EXTERNAL_ATHLETE_ID, FonteDados.INTERVALS_ICU, tenantId, atletaId)).thenReturn(List.of());
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(
                    atletaId, FonteDados.INTERVALS_ICU, tenantId)).thenReturn(Optional.empty());
            when(integracaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Resultado resultado = service.exchangeCodeForToken("code-ok", signer.assinar(atletaId));

            assertThat(resultado).isEqualTo(Resultado.SUCESSO);

            var captor = org.mockito.ArgumentCaptor.forClass(IntegracaoExterna.class);
            verify(integracaoRepository).save(captor.capture());
            IntegracaoExterna salva = captor.getValue();
            assertThat(salva.getAccessToken()).isEqualTo("tok-abc");
            assertThat(salva.getScopes()).isEqualTo("ACTIVITY:READ,CALENDAR:WRITE");
            assertThat(salva.getExternalAthleteId()).isEqualTo(EXTERNAL_ATHLETE_ID);
            assertThat(salva.isAtivo()).isTrue();
            assertThat(salva.getLastSyncError()).isNull();
        }

        // D3: o provedor não emite refresh_token nem expires_in. Este teste existe para que
        // alguém que "conserte" os nulos quebre a suíte.
        @Test
        @DisplayName("refreshToken e tokenExpiraEm permanecem nulos — é o contrato do provedor")
        void refreshETokenExpiraEmNulos() {
            stubTokenOk();
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atletaComTenant()));
            when(integracaoRepository.findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(
                    any(), any(), any(), any())).thenReturn(List.of());
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(
                    atletaId, FonteDados.INTERVALS_ICU, tenantId)).thenReturn(Optional.empty());
            when(integracaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.exchangeCodeForToken("code-ok", signer.assinar(atletaId));

            var captor = org.mockito.ArgumentCaptor.forClass(IntegracaoExterna.class);
            verify(integracaoRepository).save(captor.capture());
            assertThat(captor.getValue().getRefreshToken()).isNull();
            assertThat(captor.getValue().getTokenExpiraEm()).isNull();
        }

        // CA9: o callback não tem JWT, então o tenant NÃO pode vir do request.
        @Test
        @DisplayName("tenantId vem do atleta resolvido pelo state, não do TenantContext")
        void tenantVemDoAtleta() {
            stubTokenOk();
            UUID tenantDoAtleta = UUID.randomUUID();
            Assessoria assessoria = new Assessoria();
            assessoria.setId(tenantDoAtleta);
            Atleta atleta = Atleta.builder().id(atletaId).assessoria(assessoria).build();

            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atleta));
            when(integracaoRepository.findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(
                    any(), any(), any(), any())).thenReturn(List.of());
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(
                    atletaId, FonteDados.INTERVALS_ICU, tenantDoAtleta)).thenReturn(Optional.empty());
            when(integracaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.exchangeCodeForToken("code-ok", signer.assinar(atletaId));

            var captor = org.mockito.ArgumentCaptor.forClass(IntegracaoExterna.class);
            verify(integracaoRepository).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo(tenantDoAtleta);
        }

        @Test
        @DisplayName("reconexão reusa o registro existente da unique (atleta, plataforma)")
        void reconexaoReusaRegistro() {
            stubTokenOk();
            IntegracaoExterna existente = new IntegracaoExterna();
            existente.setLastSyncError("erro antigo");
            existente.setAtivo(false);

            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atletaComTenant()));
            when(integracaoRepository.findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(
                    any(), any(), any(), any())).thenReturn(List.of());
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(
                    atletaId, FonteDados.INTERVALS_ICU, tenantId)).thenReturn(Optional.of(existente));
            when(integracaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.exchangeCodeForToken("code-ok", signer.assinar(atletaId));

            assertThat(existente.isAtivo()).isTrue();
            assertThat(existente.getLastSyncError()).isNull();
            verify(integracaoRepository).save(existente);
        }

        @Test
        @DisplayName("hook D5.2 é chamado no sucesso")
        void hookD52Chamado() {
            stubTokenOk();
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atletaComTenant()));
            when(integracaoRepository.findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(
                    any(), any(), any(), any())).thenReturn(List.of());
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(
                    atletaId, FonteDados.INTERVALS_ICU, tenantId)).thenReturn(Optional.empty());
            when(integracaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.exchangeCodeForToken("code-ok", signer.assinar(atletaId));

            verify(connectionService).pausarStravaAutomaticamente(atletaId, tenantId);
        }
    }

    @Nested
    @DisplayName("exchangeCodeForTokenCaminhosDeFalha")
    class CaminhosDeFalha {

        @Test
        @DisplayName("state com assinatura inválida não persiste nada")
        void stateInvalidoNaoPersiste() {
            Resultado resultado = service.exchangeCodeForToken("code-ok", "forjado.123.abc");

            assertThat(resultado).isEqualTo(Resultado.STATE_INVALIDO);
            verify(integracaoRepository, never()).save(any());
            verifyNoInteractions(atletaRepository);
        }

        @Test
        @DisplayName("state nulo não persiste nada")
        void stateNuloNaoPersiste() {
            assertThat(service.exchangeCodeForToken("code-ok", null)).isEqualTo(Resultado.STATE_INVALIDO);
            verify(integracaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("code nulo não persiste nada")
        void codeNuloNaoPersiste() {
            // O atleta precisa existir para o fluxo chegar ao passo do code: a ordem é
            // state → atleta → troca. Sem este stub, o teste passaria por
            // ATLETA_NAO_ENCONTRADO e não exercitaria o caminho do code ausente.
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atletaComTenant()));

            assertThat(service.exchangeCodeForToken(null, signer.assinar(atletaId)))
                    .isEqualTo(Resultado.FALHA_NA_TROCA);
            verify(integracaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("atleta do state não existe mais — nada persistido")
        void atletaInexistente() {
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.empty());

            Resultado resultado = service.exchangeCodeForToken("code-ok", signer.assinar(atletaId));

            assertThat(resultado).isEqualTo(Resultado.ATLETA_NAO_ENCONTRADO);
            verify(integracaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("provedor recusa o code (400) — nada persistido")
        void provedorRecusaCode() {
            wireMock.stubFor(post(urlEqualTo("/api/oauth/token"))
                    .willReturn(aResponse().withStatus(400).withBody("invalid_grant")));
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atletaComTenant()));

            Resultado resultado = service.exchangeCodeForToken("code-expirado", signer.assinar(atletaId));

            assertThat(resultado).isEqualTo(Resultado.FALHA_NA_TROCA);
            verify(integracaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("provedor fora do ar (500) — nada persistido e não lança")
        void provedorForaDoAr() {
            wireMock.stubFor(post(urlEqualTo("/api/oauth/token"))
                    .willReturn(aResponse().withStatus(500)));
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atletaComTenant()));

            assertThatCode(() -> assertThat(
                    service.exchangeCodeForToken("code-ok", signer.assinar(atletaId)))
                    .isEqualTo(Resultado.FALHA_NA_TROCA))
                    .doesNotThrowAnyException();
            verify(integracaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("resposta sem athlete.id — nada persistido")
        void respostaSemAthleteId() {
            wireMock.stubFor(post(urlEqualTo("/api/oauth/token"))
                    .willReturn(okJson("{\"token_type\":\"Bearer\",\"access_token\":\"tok\"}")));
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atletaComTenant()));

            Resultado resultado = service.exchangeCodeForToken("code-ok", signer.assinar(atletaId));

            assertThat(resultado).isEqualTo(Resultado.FALHA_NA_TROCA);
            verify(integracaoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("guardContaJaVinculada")
    class GuardContaJaVinculada {

        // D12/CA12: sem este guard, dois atletas do mesmo tenant podem autorizar a MESMA conta
        // intervals.icu e passar a receber treino planejado no mesmo calendário e no mesmo
        // relógio. O dano chega a uma pessoa real antes de qualquer import rodar.
        @Test
        @DisplayName("conta já vinculada a outro atleta do tenant — nada persistido")
        void contaJaVinculadaNaoPersiste() {
            stubTokenOk();
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atletaComTenant()));
            when(integracaoRepository.findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(
                    EXTERNAL_ATHLETE_ID, FonteDados.INTERVALS_ICU, tenantId, atletaId))
                    .thenReturn(List.of(new IntegracaoExterna()));

            Resultado resultado = service.exchangeCodeForToken("code-ok", signer.assinar(atletaId));

            assertThat(resultado).isEqualTo(Resultado.CONTA_JA_VINCULADA);
            verify(integracaoRepository, never()).save(any());
        }

        // O achado JPA: o guard tem que rodar ANTES do find-or-create. Uma entidade obtida por
        // findBy... é managed, e mutá-la a persiste no flush mesmo sem save() explícito. Como
        // o retorno é normal (D14 proíbe lançar), a transação commita e "nada é persistido"
        // seria falso.
        @Test
        @DisplayName("o guard roda antes de buscar ou mutar a integração")
        void guardRodaAntesDoFindOrCreate() {
            stubTokenOk();
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atletaComTenant()));
            when(integracaoRepository.findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(
                    EXTERNAL_ATHLETE_ID, FonteDados.INTERVALS_ICU, tenantId, atletaId))
                    .thenReturn(List.of(new IntegracaoExterna()));

            service.exchangeCodeForToken("code-ok", signer.assinar(atletaId));

            // Se o find-or-create tivesse rodado, a entidade managed já estaria mutada.
            verify(integracaoRepository, never())
                    .findByAtletaIdAndPlataformaAndTenantId(any(), any(), any());
        }

        // O guard filtra por atleta.id <> :atletaId no repositório. Um guard que barrasse a
        // reconexão do próprio atleta seria pior que a ausência dele.
        @Test
        @DisplayName("reconexão do próprio atleta não é barrada")
        void reconexaoDoProprioAtletaPassa() {
            stubTokenOk();
            when(atletaRepository.findByIdBasic(atletaId)).thenReturn(Optional.of(atletaComTenant()));
            when(integracaoRepository.findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(
                    EXTERNAL_ATHLETE_ID, FonteDados.INTERVALS_ICU, tenantId, atletaId))
                    .thenReturn(List.of());
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(
                    atletaId, FonteDados.INTERVALS_ICU, tenantId)).thenReturn(Optional.empty());
            when(integracaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.exchangeCodeForToken("code-ok", signer.assinar(atletaId)))
                    .isEqualTo(Resultado.SUCESSO);
        }
    }

    @Nested
    @DisplayName("revogarEDesconectar")
    class RevogarEDesconectar {

        @Test
        @DisplayName("revoga no provedor com o token e depois desconecta localmente")
        void revogaEDesconecta() {
            IntegracaoExterna conexao = new IntegracaoExterna();
            conexao.setAccessToken("tok-abc");
            when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao));

            service.revogarEDesconectar(atletaId);

            // A ordem importa (D7): remoto primeiro, com o token que ainda existe; a desconexão
            // local o apaga, e depois dela não haveria mais com que revogar.
            org.mockito.InOrder ordem = inOrder(intervalsIcuClient, connectionService);
            ordem.verify(intervalsIcuClient).revogarAcesso("tok-abc");
            ordem.verify(connectionService).desconectar(atletaId);
        }

        @Test
        @DisplayName("sem conexão ativa ainda assim desconecta localmente (no-op seguro)")
        void semConexaoAtivaDesconectaLocal() {
            when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.empty());

            service.revogarEDesconectar(atletaId);

            verify(intervalsIcuClient, never()).revogarAcesso(any());
            verify(connectionService).desconectar(atletaId);
        }

        // D7: a intenção do atleta é sair. Travar a desconexão local porque o provedor está fora
        // do ar deixaria o Menthoros usando um token que ele já quis descartar.
        @Test
        @DisplayName("falha na revogação remota não impede a desconexão local")
        void falhaRemotaNaoImpedeLocal() {
            IntegracaoExterna conexao = new IntegracaoExterna();
            conexao.setAccessToken("tok-abc");
            when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao));
            doThrow(new RuntimeException("provedor fora do ar"))
                    .when(intervalsIcuClient).revogarAcesso("tok-abc");

            assertThatCode(() -> service.revogarEDesconectar(atletaId)).doesNotThrowAnyException();

            verify(connectionService).desconectar(atletaId);
        }
    }
}
