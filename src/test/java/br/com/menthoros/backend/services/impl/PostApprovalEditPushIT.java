package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.input.TreinoPlanejadoPatchDto;
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
import br.com.menthoros.backend.enums.WorkoutEditReason;
import br.com.menthoros.backend.events.PlanoAprovadoEvent;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.PlannedWorkoutPostApprovalEditRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.TreinoPlanejadoService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Edição de treino em plano aprovado de ponta a ponta (coach-editar-treino-aprovado, CA6, CA8b,
 * CA11b): service real → evento AFTER_COMMIT → {@link WorkoutEditedPushListener} → processor →
 * Garmin simulado por WireMock, com Postgres e Flyway reais.
 *
 * <p>O service é chamado direto (a transação dele é a que dispara o listener) com tenant e JWT no
 * contexto, como o controller faria. O listener é {@code @Async}: o teste espera o efeito por
 * polling.
 */
@DisplayName("Edição de treino em plano aprovado — ponta a ponta (Garmin)")
class PostApprovalEditPushIT extends AbstractIntegrationTest {

    private static final String CRIAR_WORKOUT = "/workoutportal/workout/v2";
    private static final String SCHEDULE = "/training-api/schedule";
    private static final String WORKOUT = "/training-api/workout/v2/";
    private static final String DESCRICAO_EDITADA = "Reduzido apos dor no joelho";

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

    @Autowired private TreinoPlanejadoService treinoPlanejadoService;
    @Autowired private IntervalsIcuPushListener planoAprovadoListener;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;
    @Autowired private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Autowired private IntegracaoExternaRepository integracaoRepository;
    @Autowired private PlannedWorkoutPostApprovalEditRepository editRepository;

    @BeforeEach
    void limparStubs() {
        garmin.resetAll();
    }

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("4 treinos sincronizados, edita 1: só ele é atualizado no mesmo workout e a edição é registrada (CA6, CA11b)")
    void editaUmDeQuatroSincronizados() {
        Atleta atleta = seedAtleta();
        UUID tenantId = atleta.getAssessoria().getId();
        PlanoSemanal plano = seedPlano(atleta);
        List<TreinoPlanejado> treinos = List.of(
                seedSincronizado(atleta, plano, "2000001"), seedSincronizado(atleta, plano, "2000002"),
                seedSincronizado(atleta, plano, "2000003"), seedSincronizado(atleta, plano, "2000004"));
        seedConexaoGarmin(atleta, tenantId);
        TreinoPlanejado editado = treinos.get(1);

        garmin.stubFor(get(urlEqualTo(WORKOUT + "2000002")).willReturn(okJson(
                "{\"workoutId\":2000002,\"ownerId\":12345,\"workoutProvider\":\"Menthoros\"}")));
        garmin.stubFor(put(urlEqualTo(WORKOUT + "2000002")).willReturn(aResponse().withStatus(200)));
        garmin.stubFor(post(urlPathEqualTo(SCHEDULE)).willReturn(okJson("81")));
        garmin.stubFor(get(urlPathEqualTo(SCHEDULE)).willReturn(okJson(
                "[{\"scheduleId\":81,\"workoutId\":2000002,\"date\":\"" + editado.getDataTreino() + "\"}]")));

        editarComo(tenantId, plano, editado, WorkoutEditReason.PAIN_INJURY);

        esperar(() -> status(editado) == StatusSincronizacao.SINCRONIZADO);
        TreinoPlanejado depois = treinoPlanejadoRepository.findById(editado.getId()).orElseThrow();
        assertThat(depois.getExternalId()).isEqualTo("2000002");
        assertThat(depois.getEditedAfterApprovalAt()).isNotNull();
        garmin.verify(1, putRequestedFor(urlEqualTo(WORKOUT + "2000002"))
                .withRequestBody(containing(DESCRICAO_EDITADA)));
        for (String outro : List.of("2000001", "2000003", "2000004")) {
            garmin.verify(0, putRequestedFor(urlEqualTo(WORKOUT + outro)));
        }
        garmin.verify(0, postRequestedFor(urlPathEqualTo(CRIAR_WORKOUT)));
        assertThat(editRepository.findAll())
                .filteredOn(e -> e.getPlannedWorkoutId().equals(editado.getId()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getTenantId()).isEqualTo(tenantId);
                    assertThat(e.getReason()).isEqualTo(WorkoutEditReason.PAIN_INJURY);
                    assertThat(e.getEditedBy()).isEqualTo("coach-it");
                    assertThat(e.getSnapshotAfter()).contains(DESCRICAO_EDITADA);
                    assertThat(e.getSnapshotBefore()).doesNotContain(DESCRICAO_EDITADA);
                });
    }

    @Test
    @DisplayName("treino sem externalId: edição e push da aprovação concorrendo criam um único workout, com o conteúdo editado (CA8b)")
    void edicaoEAprovacaoConcorrendoNaoDuplicam() {
        Atleta atleta = seedAtleta();
        UUID tenantId = atleta.getAssessoria().getId();
        PlanoSemanal plano = seedPlano(atleta);
        TreinoPlanejado treino = seedTreino(atleta, plano);
        seedConexaoGarmin(atleta, tenantId);

        // Criação lenta: o segundo claim chega enquanto o primeiro ainda está no ar.
        garmin.stubFor(post(urlPathEqualTo(CRIAR_WORKOUT))
                .willReturn(okJson("{\"workoutId\":3000001}").withFixedDelay(1500)));
        garmin.stubFor(post(urlPathEqualTo(SCHEDULE)).willReturn(okJson("91")));
        garmin.stubFor(get(urlPathEqualTo(SCHEDULE)).willReturn(okJson("[]")));
        // Um push atrasado acharia o workout e atualizaria no lugar; sem estes stubs o adapter
        // recriaria por 404 e o teste confundiria isso com duplicata da corrida.
        garmin.stubFor(get(urlEqualTo(WORKOUT + "3000001")).willReturn(okJson(
                "{\"workoutId\":3000001,\"ownerId\":12345,\"workoutProvider\":\"Menthoros\"}")));
        garmin.stubFor(put(urlEqualTo(WORKOUT + "3000001")).willReturn(aResponse().withStatus(200)));

        editarComo(tenantId, plano, treino, null);
        planoAprovadoListener.onPlanoAprovado(new PlanoAprovadoEvent(plano.getId(), atleta.getId(), tenantId));

        esperar(() -> status(treino) == StatusSincronizacao.SINCRONIZADO);
        // Folga para um segundo push atrasado aparecer, se existisse.
        dormir(Duration.ofSeconds(2));
        garmin.verify(1, postRequestedFor(urlPathEqualTo(CRIAR_WORKOUT)));
        garmin.verify(1, postRequestedFor(urlPathEqualTo(CRIAR_WORKOUT))
                .withRequestBody(containing(DESCRICAO_EDITADA)));
        assertThat(treinoPlanejadoRepository.findById(treino.getId()).orElseThrow().getExternalId())
                .isEqualTo("3000001");
    }

    @Test
    @DisplayName("push da aprovação no ar: o PATCH responde 409 e nada é registrado (CA3c, CA11b)")
    void pushNoArRecusaAEdicao() {
        Atleta atleta = seedAtleta();
        UUID tenantId = atleta.getAssessoria().getId();
        PlanoSemanal plano = seedPlano(atleta);
        TreinoPlanejado treino = seedTreino(atleta, plano);
        seedConexaoGarmin(atleta, tenantId);

        garmin.stubFor(post(urlPathEqualTo(CRIAR_WORKOUT))
                .willReturn(okJson("{\"workoutId\":3000002}").withFixedDelay(3000)));
        garmin.stubFor(post(urlPathEqualTo(SCHEDULE)).willReturn(okJson("92")));
        garmin.stubFor(get(urlPathEqualTo(SCHEDULE)).willReturn(okJson("[]")));

        planoAprovadoListener.onPlanoAprovado(new PlanoAprovadoEvent(plano.getId(), atleta.getId(), tenantId));
        esperar(() -> status(treino) == StatusSincronizacao.SINCRONIZANDO);

        assertThatThrownBy(() -> editarComo(tenantId, plano, treino, WorkoutEditReason.LOAD))
                .isInstanceOf(DomainConflictException.class);

        esperar(() -> status(treino) == StatusSincronizacao.SINCRONIZADO);
        TreinoPlanejado depois = treinoPlanejadoRepository.findById(treino.getId()).orElseThrow();
        assertThat(depois.getDescricao()).isNotEqualTo(DESCRICAO_EDITADA);
        assertThat(depois.getEditedAfterApprovalAt()).isNull();
        assertThat(editRepository.findAll()).noneMatch(e -> e.getPlannedWorkoutId().equals(treino.getId()));
    }

    private void editarComo(UUID tenantId, PlanoSemanal plano, TreinoPlanejado treino, WorkoutEditReason reason) {
        TenantContext.setTenantId(tenantId);
        Jwt jwt = Jwt.withTokenValue("token-it").header("alg", "none").subject("coach-it")
                .claim("tenant_id", tenantId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            treinoPlanejadoService.editarTreino(plano.getId(), treino.getId(), new TreinoPlanejadoPatchDto(
                    null, DESCRICAO_EDITADA, null, null, null, null, null, null, null, reason));
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private StatusSincronizacao status(TreinoPlanejado treino) {
        return treinoPlanejadoRepository.findById(treino.getId()).orElseThrow().getStatusSincronizacao();
    }

    private static void esperar(BooleanSupplier condicao) {
        long limite = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < limite) {
            if (condicao.getAsBoolean()) {
                return;
            }
            dormir(Duration.ofMillis(100));
        }
        throw new AssertionError("condição não atendida em 15s (listener assíncrono)");
    }

    private static void dormir(Duration duracao) {
        try {
            Thread.sleep(duracao.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Edicao Pos Aprovacao IT");
        assessoria.setDominio("edicao-pos-aprovacao-it-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Edicao");
        atleta.setEmail("edicao-" + UUID.randomUUID() + "@test.com");
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
        plano.setSemanaInicio(LocalDate.now());
        plano.setSemanaFim(LocalDate.now().plusDays(6));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setObjetivoSemanal("Semana de teste");
        return planoSemanalRepository.save(plano);
    }

    /** Dois dias à frente: longe da virada de dia entre a JVM e o fuso do atleta. */
    private TreinoPlanejado seedTreino(Atleta atleta, PlanoSemanal plano) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setPlanoSemanal(plano);
        treino.setAtleta(atleta);
        treino.setTenantId(atleta.getAssessoria().getId());
        treino.setDataTreino(LocalDate.now().plusDays(2));
        treino.setDiaSemana(DiaSemana.SABADO);
        treino.setTipoTreino(TipoTreino.REGENERATIVO);
        treino.setDescricao("Rodagem leve");
        treino.setDuracaoMin(Duration.ofMinutes(30));
        treino.setStatusSincronizacao(StatusSincronizacao.PENDENTE);
        treino.setTentativasSincronizacao(0);
        return treinoPlanejadoRepository.save(treino);
    }

    private TreinoPlanejado seedSincronizado(Atleta atleta, PlanoSemanal plano, String workoutId) {
        TreinoPlanejado treino = seedTreino(atleta, plano);
        treino.setStatusSincronizacao(StatusSincronizacao.SINCRONIZADO);
        treino.setExternalId(workoutId);
        treino.setExportadoPara("[\"GARMIN\"]");
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
