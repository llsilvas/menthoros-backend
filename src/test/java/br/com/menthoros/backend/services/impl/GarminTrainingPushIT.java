package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.events.PlanoAprovadoEvent;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.PlanoReviewService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Push de treinos para o Garmin Connect de ponta a ponta (garmin-training-push, CA1, CA3, CA4, CA9):
 * {@code PlanoAprovadoEvent} → listener real → composto → {@code GarminTrainingAdapter} → cliente
 * real contra a Training API simulada por WireMock, com Postgres e Flyway reais.
 *
 * <p>O listener é chamado direto (o {@code @TransactionalEventListener} só dispara dentro de uma
 * transação) e é {@code @Async}; o teste espera o efeito (status persistido, chamadas ao WireMock)
 * por polling em vez de depender de um latch, porque nenhum colaborador é mockado.
 */
@DisplayName("Push de treinos ao Garmin Connect — ponta a ponta")
class GarminTrainingPushIT extends AbstractIntegrationTest {

    private static final String CRIAR_WORKOUT = "/workoutportal/workout/v2";
    private static final String SCHEDULE = "/training-api/schedule";
    private static final String WORKOUT = "/training-api/workout/v2/";

    private static WireMockServer garmin;

    @BeforeAll
    static void iniciarProvedor() {
        garmin = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        garmin.start();
    }

    @AfterAll
    static void pararProvedor() {
        garmin.stop();
    }

    @DynamicPropertySource
    static void apontarGarmin(DynamicPropertyRegistry registry) {
        registry.add("app.garmin.habilitado", () -> "true");
        registry.add("app.garmin.client-id", () -> "client-id-it");
        registry.add("app.garmin.client-secret", () -> "client-secret-it");
        registry.add("app.garmin.redirect-uri", () -> "http://localhost/api/v1/integracoes/garmin/callback");
        registry.add("app.garmin.token-uri", () -> garmin.baseUrl() + "/di-oauth2-service/oauth/token");
        registry.add("app.garmin.api-base-url", garmin::baseUrl);
    }

    @Autowired private IntervalsIcuPushListener listener;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;
    @Autowired private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Autowired private IntegracaoExternaRepository integracaoRepository;
    @Autowired private PlanoReviewService planoReviewService;

    @BeforeEach
    void limparStubs() {
        garmin.resetAll();
    }

    @Test
    @DisplayName("aprova → treinos SINCRONIZADO com workoutId; re-aprova sem um treino → DELETE do órfão")
    void aprovaEReconciliaOrfao() {
        Atleta atleta = seedAtleta();
        UUID tenantId = atleta.getAssessoria().getId();
        PlanoSemanal plano = seedPlano(atleta);
        TreinoPlanejado t1 = seedTreino(atleta, plano);
        TreinoPlanejado t2 = seedTreino(atleta, plano);
        seedConexaoGarmin(atleta, tenantId);

        garmin.stubFor(post(urlPathEqualTo(CRIAR_WORKOUT)).inScenario("ids")
                .whenScenarioStateIs(Scenario.STARTED).willReturn(okJson("{\"workoutId\":1000042}"))
                .willSetStateTo("segundo"));
        garmin.stubFor(post(urlPathEqualTo(CRIAR_WORKOUT)).inScenario("ids")
                .whenScenarioStateIs("segundo").willReturn(okJson("{\"workoutId\":1000043}"))
                .willSetStateTo("fim"));
        garmin.stubFor(post(urlPathEqualTo(SCHEDULE)).willReturn(okJson("77")));
        garmin.stubFor(get(urlPathEqualTo(SCHEDULE)).willReturn(okJson("[]")));

        listener.onPlanoAprovado(new PlanoAprovadoEvent(plano.getId(), atleta.getId(), tenantId));

        esperar(() -> sincronizado(t1) && sincronizado(t2));
        TreinoPlanejado a = treinoPlanejadoRepository.findById(t1.getId()).orElseThrow();
        TreinoPlanejado b = treinoPlanejadoRepository.findById(t2.getId()).orElseThrow();
        assertThat(java.util.List.of(a.getExternalId(), b.getExternalId()))
                .containsExactlyInAnyOrder("1000042", "1000043");
        assertThat(a.getExportadoPara()).isEqualTo("[\"GARMIN\"]");
        garmin.verify(2, postRequestedFor(urlPathEqualTo(SCHEDULE)));

        // Re-aprovação sem o treino que ficou com 1000043: o schedule/workout dele vira órfão.
        TreinoPlanejado removido = "1000043".equals(a.getExternalId()) ? a : b;
        TreinoPlanejado mantido = removido == a ? b : a;
        treinoPlanejadoRepository.deleteById(removido.getId());

        garmin.resetAll();
        // O PUT só casa com ownerId e workoutId no corpo (Training API §3.1); sem eles o WireMock
        // responde 404 e o adapter recriaria o workout — o verify abaixo pega.
        garmin.stubFor(get(urlEqualTo(WORKOUT + "1000042")).willReturn(okJson(
                "{\"workoutId\":1000042,\"ownerId\":12345,\"workoutProvider\":\"Menthoros\"}")));
        garmin.stubFor(put(urlEqualTo(WORKOUT + "1000042"))
                .withRequestBody(matchingJsonPath("$.ownerId", equalTo("12345")))
                .withRequestBody(matchingJsonPath("$.workoutId", equalTo("1000042")))
                .willReturn(aResponse().withStatus(200)));
        garmin.stubFor(get(urlPathEqualTo(SCHEDULE)).willReturn(okJson(
                "[{\"scheduleId\":77,\"workoutId\":1000042,\"date\":\"" + LocalDate.now() + "\"},"
                        + "{\"scheduleId\":78,\"workoutId\":1000043,\"date\":\"" + LocalDate.now() + "\"}]")));
        garmin.stubFor(get(urlEqualTo(WORKOUT + "1000043")).willReturn(okJson("{\"workoutProvider\":\"Menthoros\"}")));
        garmin.stubFor(delete(urlEqualTo(SCHEDULE + "/78")).willReturn(aResponse().withStatus(204)));
        garmin.stubFor(delete(urlEqualTo(WORKOUT + "1000043")).willReturn(aResponse().withStatus(204)));

        listener.onPlanoAprovado(new PlanoAprovadoEvent(plano.getId(), atleta.getId(), tenantId));

        esperar(() -> !garmin.findAll(deleteRequestedFor(urlEqualTo(WORKOUT + "1000043"))).isEmpty());
        garmin.verify(1, deleteRequestedFor(urlEqualTo(SCHEDULE + "/78")));
        garmin.verify(0, deleteRequestedFor(urlEqualTo(WORKOUT + "1000042")));
        garmin.verify(1, putRequestedFor(urlEqualTo(WORKOUT + "1000042"))
                .withRequestBody(matchingJsonPath("$.ownerId", equalTo("12345"))));
        garmin.verify(0, postRequestedFor(urlPathEqualTo(CRIAR_WORKOUT)));
        garmin.verify(0, deleteRequestedFor(urlEqualTo(SCHEDULE + "/77")));
        assertThat(treinoPlanejadoRepository.findById(mantido.getId()).orElseThrow().getExternalId())
                .isEqualTo("1000042");
    }

    /**
     * garmin-reenvio-plano (CA1, CA2, CA6): o service real publica o evento dentro da transação e o
     * listener AFTER_COMMIT o recebe — o caminho que o incidente de 2026-11-01 resolveu com UPDATE
     * no banco. O treino em ERRO_PERMANENTE volta a SINCRONIZADO; o já sincronizado é atualizado no
     * mesmo workout, sem POST de criação.
     */
    @Test
    @DisplayName("reenvio: treino em ERRO_PERMANENTE volta a SINCRONIZADO e o sincronizado mantém o workout")
    void reenvioRecuperaTreinoEmErro() {
        Atleta atleta = seedAtleta();
        UUID tenantId = atleta.getAssessoria().getId();
        PlanoSemanal plano = seedPlano(atleta);
        TreinoPlanejado emErro = seedTreino(atleta, plano);
        emErro.setStatusSincronizacao(StatusSincronizacao.ERRO_PERMANENTE);
        emErro.setTentativasSincronizacao(5);
        emErro.setExternalId("1000042");
        emErro.setErroSincronizacao("Tentativas esgotadas");
        treinoPlanejadoRepository.save(emErro);
        TreinoPlanejado sincronizado = seedTreino(atleta, plano);
        sincronizado.setStatusSincronizacao(StatusSincronizacao.SINCRONIZADO);
        sincronizado.setExternalId("1000043");
        treinoPlanejadoRepository.save(sincronizado);
        seedConexaoGarmin(atleta, tenantId);

        for (String id : java.util.List.of("1000042", "1000043")) {
            garmin.stubFor(get(urlEqualTo(WORKOUT + id)).willReturn(okJson(
                    "{\"workoutId\":" + id + ",\"ownerId\":12345,\"workoutProvider\":\"Menthoros\"}")));
            garmin.stubFor(put(urlEqualTo(WORKOUT + id)).willReturn(aResponse().withStatus(200)));
        }
        garmin.stubFor(get(urlPathEqualTo(SCHEDULE)).willReturn(okJson(
                "[{\"scheduleId\":77,\"workoutId\":1000042,\"date\":\"" + LocalDate.now() + "\"},"
                        + "{\"scheduleId\":78,\"workoutId\":1000043,\"date\":\"" + LocalDate.now() + "\"}]")));
        garmin.stubFor(post(urlPathEqualTo(SCHEDULE)).willReturn(okJson("79")));

        int reenviados = planoReviewService.reenviarSincronizacao(plano.getId(), tenantId);

        assertThat(reenviados).isEqualTo(1);
        esperar(() -> sincronizado(emErro));
        TreinoPlanejado recuperado = treinoPlanejadoRepository.findById(emErro.getId()).orElseThrow();
        assertThat(recuperado.getExternalId()).isEqualTo("1000042");
        assertThat(recuperado.getTentativasSincronizacao()).isEqualTo(1);
        assertThat(recuperado.getErroSincronizacao()).isNull();
        esperar(() -> !garmin.findAll(putRequestedFor(urlEqualTo(WORKOUT + "1000043"))).isEmpty());
        assertThat(treinoPlanejadoRepository.findById(sincronizado.getId()).orElseThrow().getExternalId())
                .isEqualTo("1000043");
        garmin.verify(0, postRequestedFor(urlPathEqualTo(CRIAR_WORKOUT)));
        garmin.verify(0, deleteRequestedFor(urlEqualTo(WORKOUT + "1000042")));
        garmin.verify(0, deleteRequestedFor(urlEqualTo(WORKOUT + "1000043")));
    }

    private boolean sincronizado(TreinoPlanejado t) {
        return treinoPlanejadoRepository.findById(t.getId())
                .map(f -> f.getStatusSincronizacao() == StatusSincronizacao.SINCRONIZADO).orElse(false);
    }

    private static void esperar(BooleanSupplier condicao) {
        long limite = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < limite) {
            if (condicao.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("condição não atendida em 15s (listener assíncrono)");
    }

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Garmin Push IT");
        assessoria.setDominio("garmin-push-it-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Garmin Push");
        atleta.setEmail("garmin-push-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private PlanoSemanal seedPlano(Atleta atleta) {
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(atleta.getAssessoria());
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(LocalDate.now().minusDays(6));
        plano.setSemanaFim(LocalDate.now());
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setObjetivoSemanal("Semana de teste");
        return planoSemanalRepository.save(plano);
    }

    private TreinoPlanejado seedTreino(Atleta atleta, PlanoSemanal plano) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setPlanoSemanal(plano);
        treino.setAtleta(atleta);
        treino.setTenantId(atleta.getAssessoria().getId());
        treino.setDataTreino(LocalDate.now());
        treino.setDiaSemana(DiaSemana.SABADO);
        treino.setTipoTreino(TipoTreino.REGENERATIVO);
        treino.setDuracaoMin(Duration.ofMinutes(30));
        treino.setStatusSincronizacao(StatusSincronizacao.PENDENTE);
        treino.setTentativasSincronizacao(0);
        return treinoPlanejadoRepository.save(treino);
    }

    private void seedConexaoGarmin(Atleta atleta, UUID tenantId) {
        IntegracaoExterna conexao = new IntegracaoExterna();
        conexao.setAtleta(atleta);
        conexao.setTenantId(tenantId);
        conexao.setPlataforma(FonteDados.GARMIN);
        conexao.setExternalAthleteId("garmin-uid-" + UUID.randomUUID());
        conexao.setAccessToken("token-it");
        conexao.setRefreshToken("refresh-it");
        conexao.setTokenExpiraEm(Instant.now().plus(Duration.ofHours(1)));
        conexao.setAtivo(true);
        integracaoRepository.save(conexao);
    }
}
