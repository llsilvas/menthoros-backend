package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.impl.StravaOAuthServiceImpl;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo do canal Garmin direto contra o contexto real (Postgres, segurança, Flyway) e o
 * Garmin simulado por WireMock: authorize-url, callback, exclusividade com o intervals.icu, Strava
 * nascendo pausado, webhook de permissões e de desregistro (garmin-connection, CA1–CA8, CA13).
 */
@AutoConfigureMockMvc
@DisplayName("Canal Garmin direto — fluxo ponta a ponta")
class GarminConnectionFlowIT extends AbstractIntegrationTest {

    private static final String SEGREDO_WEBHOOK = "segredo-do-it-garmin";
    private static final String CALLBACK = "/api/v1/integracoes/garmin/callback";
    private static final String ME = "/api/v1/integracoes/me/garmin";

    private static WireMockServer provedor;

    @BeforeAll
    static void iniciarProvedor() {
        provedor = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        provedor.start();
    }

    @AfterAll
    static void pararProvedor() {
        provedor.stop();
    }

    // Sem estes overrides o teste sairia para diauth.garmin.com e strava.com.
    @DynamicPropertySource
    static void apontarProvedores(DynamicPropertyRegistry registry) {
        registry.add("app.garmin.habilitado", () -> "true");
        registry.add("app.garmin.client-id", () -> "client-id-it");
        registry.add("app.garmin.client-secret", () -> "client-secret-it");
        registry.add("app.garmin.redirect-uri", () -> "http://localhost/api/v1/integracoes/garmin/callback");
        registry.add("app.garmin.token-uri", () -> provedor.baseUrl() + "/di-oauth2-service/oauth/token");
        registry.add("app.garmin.api-base-url", provedor::baseUrl);
        registry.add("app.garmin.webhook-secret", () -> SEGREDO_WEBHOOK);
        registry.add("app.strava.token-uri", () -> provedor.baseUrl() + "/strava/oauth/token");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private IntegracaoExternaRepository integracaoRepository;
    @Autowired private StravaOAuthServiceImpl stravaOAuthService;
    @Autowired private MeterRegistry meterRegistry;

    private Assessoria assessoria;
    private Atleta atleta;
    private UUID subjectAtleta;
    private String garminUserId;

    @BeforeEach
    void seed() {
        provedor.resetAll();
        garminUserId = "garmin-uid-" + UUID.randomUUID();

        assessoria = new Assessoria();
        assessoria.setNome("Assessoria Garmin IT");
        assessoria.setDominio("garmin-it-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        atleta = new Atleta();
        atleta.setNome("Atleta Garmin IT");
        atleta.setEmail("garmin-it-" + UUID.randomUUID() + "@menthoros.test");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        // resolverAtletaIdAtual() resolve o atleta pelo Usuario vinculado, não pelo e-mail do token.
        subjectAtleta = UUID.randomUUID();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .id(subjectAtleta)
                .keycloakId(subjectAtleta.toString())
                .assessoria(assessoria)
                .email(subjectAtleta + "@menthoros.test")
                .nome("Usuario " + subjectAtleta)
                .role(UserRole.ATLETA)
                .ativo(true)
                .build());
        atleta.setUsuario(usuario);
        atleta = atletaRepository.save(atleta);
    }

    private RequestPostProcessor jwtAtleta() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ATLETA"))
                .jwt(j -> j.subject(subjectAtleta.toString())
                        .claim("tenant_id", assessoria.getId().toString())
                        .claim("email", subjectAtleta + "@menthoros.test")
                        .claim("roles", java.util.List.of("ATLETA")));
    }

    private void stubGarminOk() {
        provedor.stubFor(WireMock.post(urlEqualTo("/di-oauth2-service/oauth/token"))
                .willReturn(okJson("""
                        {"access_token":"g-access","refresh_token":"g-refresh","token_type":"bearer",
                         "expires_in":86400,"refresh_token_expires_in":7776000}
                        """)));
        provedor.stubFor(WireMock.get(urlEqualTo("/partner-gateway/rest/user/id"))
                .willReturn(okJson("{\"userId\":\"" + garminUserId + "\"}")));
        provedor.stubFor(WireMock.get(urlEqualTo("/partner-gateway/rest/user/permissions"))
                .willReturn(okJson("{\"permissions\":[\"WORKOUT_IMPORT\",\"ACTIVITY_EXPORT\"]}")));
        provedor.stubFor(WireMock.delete(
                urlEqualTo("/partner-gateway/rest/user/registration")).willReturn(aResponse().withStatus(204)));
    }

    private void stubStravaOk() {
        provedor.stubFor(WireMock.post(urlEqualTo("/strava/oauth/token"))
                .willReturn(okJson("""
                        {"token_type":"Bearer","expires_at":4102444800,"expires_in":21600,
                         "refresh_token":"s-refresh","access_token":"s-access","athlete":{"id":4242}}
                        """)));
    }

    private IntegracaoExterna seedIntervalsIcuAtivo() {
        IntegracaoExterna icu = new IntegracaoExterna();
        icu.setAtleta(atleta);
        icu.setPlataforma(FonteDados.INTERVALS_ICU);
        icu.setTenantId(assessoria.getId());
        icu.setExternalAthleteId("i641775");
        icu.setAccessToken("icu-token");
        icu.setAtivo(true);
        return integracaoRepository.save(icu);
    }

    /** authorize-url → extrai o state da URL devolvida → callback com code → devolve o redirect. */
    private MvcResult conectarGarmin() throws Exception {
        MvcResult autorizacao = mockMvc.perform(get(ME + "/authorize-url").with(jwtAtleta()))
                .andExpect(status().isOk())
                .andReturn();
        String url = com.jayway.jsonpath.JsonPath.read(autorizacao.getResponse().getContentAsString(), "$.authorizationUrl");
        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        assertThat(url).contains("code_challenge_method=S256").contains("client_id=client-id-it");
        assertThat(state).isNotBlank();

        return mockMvc.perform(get(CALLBACK).param("code", "code-valido").param("state", state))
                .andExpect(status().isFound())
                .andReturn();
    }

    private void webhook(String segredo, String corpo, int statusEsperado) throws Exception {
        mockMvc.perform(post("/api/v1/garmin/webhook/{segredo}", segredo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().is(statusEsperado));
    }

    @Test
    @DisplayName("conecta, desativa o intervals.icu, pausa o Strava e segue os webhooks do Garmin")
    void fluxoCompleto() throws Exception {
        stubGarminOk();
        stubStravaOk();
        seedIntervalsIcuAtivo();
        double antes = contador("garmin.oauth.callback", "sucesso");

        // CA1/CA2 — callback concluído; status Garmin ativo com as duas permissões.
        MvcResult retorno = conectarGarmin();
        assertThat(retorno.getResponse().getHeader("Location")).contains("garmin=success");
        mockMvc.perform(get(ME).with(jwtAtleta()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.workoutImport").value(true))
                .andExpect(jsonPath("$.activityExport").value(true));

        // Credencial gravada no tenant do atleta, userId só no banco (a API devolve mascarado).
        IntegracaoExterna garmin = integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(
                atleta.getId(), FonteDados.GARMIN, assessoria.getId()).orElseThrow();
        assertThat(garmin.getExternalAthleteId()).isEqualTo(garminUserId);
        assertThat(garmin.getRefreshTokenExpiraEm()).isNotNull();

        // Exclusividade de canal: o intervals.icu ficou inativo e sem credencial.
        IntegracaoExterna icu = integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(
                atleta.getId(), FonteDados.INTERVALS_ICU, assessoria.getId()).orElseThrow();
        assertThat(icu.isAtivo()).isFalse();
        assertThat(icu.getAccessToken()).isNull();

        // Strava conectado depois do Garmin nasce pausado.
        IntegracaoExterna strava = stravaOAuthService.exchangeCodeForToken("code-strava", atleta);
        assertThat(strava.isAutoSyncPausado()).isTrue();

        // CA13 — métrica do callback.
        assertThat(contador("garmin.oauth.callback", "sucesso")).isEqualTo(antes + 1);

        // Webhook: segredo errado é 404 e nada muda.
        webhook("segredo-errado", "{}", 404);

        // userPermissionsChange perde ACTIVITY_EXPORT.
        webhook(SEGREDO_WEBHOOK, """
                {"userPermissionsChange":[{"userId":"%s","permissions":["WORKOUT_IMPORT"]}]}
                """.formatted(garminUserId), 200);
        mockMvc.perform(get(ME).with(jwtAtleta()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.workoutImport").value(true))
                .andExpect(jsonPath("$.activityExport").value(false));

        // deregistrations: conexão inativa, credenciais zeradas, motivo legível.
        webhook(SEGREDO_WEBHOOK, """
                {"deregistrations":[{"userId":"%s"}]}
                """.formatted(garminUserId), 200);
        mockMvc.perform(get(ME).with(jwtAtleta()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.lastSyncError").isNotEmpty());
        IntegracaoExterna aposDesregistro = integracaoRepository.findById(garmin.getId()).orElseThrow();
        assertThat(aposDesregistro.getAccessToken()).isNull();
        assertThat(aposDesregistro.getRefreshToken()).isNull();
        // A pausa do Strava nunca é revertida.
        assertThat(integracaoRepository.findById(strava.getId()).orElseThrow().isAutoSyncPausado()).isTrue();
    }

    @Test
    @DisplayName("callback recusado pelo atleta redireciona com denied e não grava nada")
    void callbackRecusado() throws Exception {
        MvcResult autorizacao = mockMvc.perform(get(ME + "/authorize-url").with(jwtAtleta()))
                .andExpect(status().isOk())
                .andReturn();
        String url = com.jayway.jsonpath.JsonPath.read(autorizacao.getResponse().getContentAsString(), "$.authorizationUrl");
        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        double antes = contador("garmin.oauth.callback", "recusado");

        MvcResult retorno = mockMvc.perform(get(CALLBACK).param("error", "access_denied").param("state", state))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(retorno.getResponse().getHeader("Location")).contains("garmin=denied");
        assertThat(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(
                atleta.getId(), FonteDados.GARMIN, assessoria.getId())).isEmpty();
        assertThat(contador("garmin.oauth.callback", "recusado")).isEqualTo(antes + 1);
    }

    private double contador(String nome, String resultado) {
        var counter = meterRegistry.find(nome).tag("resultado", resultado).counter();
        return counter == null ? 0 : counter.count();
    }
}
