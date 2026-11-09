package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.config.external.GarminProperties;
import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoReconciliacao;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoReconciliacaoRepository;
import br.com.menthoros.backend.services.WorkoutAnalysisPromptDataBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.GarminPing;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.GarminPingStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.GarminPingRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.scheduler.GarminPingProcessorScheduler;
import br.com.menthoros.backend.services.FitParseService;
import br.com.menthoros.backend.services.GarminOAuthService;
import br.com.menthoros.backend.services.helper.FitTreinoPersister;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ingestão de atividades do Garmin de ponta a ponta (garmin-activity-ingestion, CA1, CA3, CA4):
 * webhook {@code activityFiles} real → fila → scheduler real baixando o FIT do WireMock → treino
 * persistido, com Postgres e Flyway reais.
 *
 * <p>O job fica desligado no profile integration (contextos cacheados compartilham o banco); o
 * teste monta o scheduler com o cliente real apontado para o WireMock, já que a origem aceita no
 * {@code callbackURL} em produção é fixa ({@code https://apis.garmin.com}).
 */
@AutoConfigureMockMvc
@DisplayName("Ingestão de atividades Garmin — ponta a ponta")
class GarminActivityIngestionIT extends AbstractIntegrationTest {

    private static final String SEGREDO_WEBHOOK = "segredo-do-it-ingestao-com-32-caracteres-ou-mais";
    private static final String PATH_ARQUIVO = "/wellness-api/rest/activityFile";
    private static final String FIT = "/fit/corrida-15km-16laps.fit";
    private static final String DISPOSITIVO = "Forerunner 965";

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
        registry.add("app.garmin.webhook-secret", () -> SEGREDO_WEBHOOK);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private IntegracaoExternaRepository integracaoRepository;
    @Autowired private GarminPingRepository pingRepository;
    @Autowired private TreinoRealizadoRepository treinoRepository;
    @Autowired @Qualifier("garminWebClient") private WebClient garminWebClient;
    @Autowired private GarminProperties garminProperties;
    @Autowired private GarminOAuthService garminOAuthService;
    @Autowired private FitParseService fitParseService;
    @Autowired private FitTreinoPersister fitTreinoPersister;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;
    @Autowired private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Autowired private WorkoutAnalysisPromptDataBuilder promptDataBuilder;
    // Spy (chama o real por padrão): o CA5 faz a auditoria falhar depois das mutações da conciliação.
    @MockitoSpyBean private TreinoReconciliacaoRepository treinoReconciliacaoRepository;

    private GarminPingProcessorScheduler processador;
    private Assessoria assessoria;
    private Atleta atleta;
    private UUID subjectAtleta;
    private String garminUserId;

    @BeforeEach
    void seed() {
        garmin.resetAll();
        reset(treinoReconciliacaoRepository);
        garminUserId = "garmin-uid-" + UUID.randomUUID();
        processador = new GarminPingProcessorScheduler(pingRepository, integracaoRepository,
                new GarminClientImpl(garminWebClient, garminProperties, URI.create(garmin.baseUrl())),
                garminOAuthService, fitParseService, fitTreinoPersister, transactionManager, clock);

        assessoria = new Assessoria();
        assessoria.setNome("Assessoria Ingestao Garmin IT");
        assessoria.setDominio("garmin-ingestao-it-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        atleta = new Atleta();
        atleta.setNome("Atleta Ingestao Garmin IT");
        atleta.setEmail("garmin-ingestao-it-" + UUID.randomUUID() + "@menthoros.test");
        atleta.setObjetivo("Correr 15km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        // O upload manual resolve o atleta pelo Usuario vinculado ao subject do token.
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

        IntegracaoExterna conexao = new IntegracaoExterna();
        conexao.setAtleta(atleta);
        conexao.setPlataforma(FonteDados.GARMIN);
        conexao.setTenantId(assessoria.getId());
        conexao.setExternalAthleteId(garminUserId);
        conexao.setAccessToken("g-access");
        conexao.setTokenExpiraEm(Instant.now().plus(Duration.ofDays(1)));
        conexao.setAtivo(true);
        integracaoRepository.save(conexao);
    }

    @Test
    @DisplayName("ping → fila → download → treino GARMIN; ping repetido e upload manual não duplicam")
    void pingViraTreinoSemDuplicar() throws Exception {
        String summaryId = "summary-" + UUID.randomUUID();
        garmin.stubFor(get(urlPathEqualTo(PATH_ARQUIVO))
                .withHeader("Authorization", equalTo("Bearer g-access"))
                .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .withBody(arquivoFit())));
        String corpo = ping(summaryId);

        webhook(corpo);
        GarminPing pendente = pingDo(summaryId);
        assertThat(pendente.getStatus()).isEqualTo(GarminPingStatus.PENDING);

        processador.processar();

        // CA1 — treino GARMIN com o modelo do relógio, as 16 voltas e TSS calculado.
        GarminPing processado = pingDo(summaryId);
        assertThat(processado.getStatus()).isEqualTo(GarminPingStatus.PROCESSED);
        assertThat(processado.getCompletedWorkoutId()).isNotNull();
        TreinoRealizado treino = new TransactionTemplate(transactionManager).execute(s -> {
            TreinoRealizado t = treinoRepository.findByIdWithEtapas(processado.getCompletedWorkoutId()).orElseThrow();
            t.getEtapasRealizadas().size();
            return t;
        });
        assertThat(treino.getFonteDados()).isEqualTo(FonteDados.GARMIN);
        assertThat(treino.getDeviceName()).isEqualTo(DISPOSITIVO);
        assertThat(treino.getEtapasRealizadas()).hasSize(16);
        assertThat(treino.getTssCalculado()).isNotNull().isPositive();
        assertThat(integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(
                atleta.getId(), FonteDados.GARMIN, assessoria.getId()).orElseThrow().getUltimaSincronizacao())
                .isNotNull();

        // CA3 — o Garmin reenvia o mesmo ping: nenhum ping novo, nenhum download novo.
        webhook(corpo);
        processador.processar();
        assertThat(pingsDo(summaryId)).hasSize(1);
        garmin.verify(1, getRequestedFor(urlPathEqualTo(PATH_ARQUIVO)));

        // CA4 — o atleta sobe o mesmo FIT à mão: 200 (já importado), sem segundo treino.
        mockMvc.perform(multipart("/api/v1/atletas/me/treinos/importar-fit")
                        .file(new MockMultipartFile("arquivo", "corrida.fit",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, arquivoFit()))
                        .with(jwtAtleta()))
                .andExpect(status().isOk());
        assertThat(treinoRepository.findByAtletaIdOrderByDataTreinoAsc(atleta.getId())).hasSize(1);
    }

    @Test
    @DisplayName("FIT com planejado inequívoco no dia vincula automaticamente e a análise enxerga o planejado (CA1, CA8)")
    void fitComPlanejadoVinculaAutomaticamente() throws Exception {
        TreinoPlanejado planejado = planejadoIgualAoFit();

        UUID treinoId = importarViaPing();

        TreinoRealizado realizado = carregar(treinoId);
        assertThat(realizado.getReconciliationStatus()).isEqualTo(ReconciliationStatus.VINCULADO_AUTOMATICO);
        assertThat(realizado.getTreinoPlanejado().getId()).isEqualTo(planejado.getId());
        assertThat(treinoPlanejadoRepository.findById(planejado.getId()).orElseThrow().getStatusTreino())
                .isEqualTo(TreinoExecucaoStatus.REALIZADO);
        assertThat(auditoriaDe(treinoId)).hasSize(1);

        String prompt = new TransactionTemplate(transactionManager).execute(s ->
                promptDataBuilder.build(treinoRepository.findById(treinoId).orElseThrow()));
        assertThat(prompt).contains("\"planned\"").contains("\"type\":\"CONTINUO\"").contains("\"duration_min\"");
    }

    @Test
    @DisplayName("FIT sem planejado na janela ±1 dia fica NAO_PLANEJADO e entra na fila do treinador (CA2)")
    void fitSemPlanejadoVaiParaFila() throws Exception {
        UUID treinoId = importarViaPing();

        assertThat(carregar(treinoId).getReconciliationStatus()).isEqualTo(ReconciliationStatus.NAO_PLANEJADO);
        assertThat(treinoRepository.findPendentesParaRevisao(assessoria.getId(), atleta.getId(),
                        List.of(ReconciliationStatus.AMBIGUO, ReconciliationStatus.NAO_PLANEJADO), null, null,
                        PageRequest.of(0, 10)).getContent())
                .extracting(TreinoRealizado::getId).containsExactly(treinoId);
    }

    @Test
    @DisplayName("falha da conciliação depois das mutações não reverte a ingestão nem deixa vínculo parcial (CA5)")
    void falhaDaConciliacaoNaoReverteIngestao() throws Exception {
        TreinoPlanejado planejado = planejadoIgualAoFit();
        doThrow(new IllegalStateException("falha simulada na auditoria"))
                .when(treinoReconciliacaoRepository).save(any(TreinoReconciliacao.class));

        UUID treinoId = importarViaPing();

        TreinoRealizado realizado = carregar(treinoId);
        assertThat(realizado.getReconciliationStatus()).isNull();
        assertThat(realizado.getTreinoPlanejado()).isNull();
        assertThat(treinoPlanejadoRepository.findById(planejado.getId()).orElseThrow().getStatusTreino())
                .isEqualTo(TreinoExecucaoStatus.PENDENTE);
        assertThat(auditoriaDe(treinoId)).isEmpty();
    }

    /** Ping → fila → download → treino; devolve o id do realizado e confirma o ping PROCESSED. */
    private UUID importarViaPing() throws Exception {
        String summaryId = "summary-" + UUID.randomUUID();
        garmin.stubFor(get(urlPathEqualTo(PATH_ARQUIVO))
                .withHeader("Authorization", equalTo("Bearer g-access"))
                .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .withBody(arquivoFit())));
        webhook(ping(summaryId));

        processador.processar();

        GarminPing processado = pingDo(summaryId);
        assertThat(processado.getStatus()).isEqualTo(GarminPingStatus.PROCESSED);
        return processado.getCompletedWorkoutId();
    }

    /** Planejado CONTINUO no mesmo dia, com a duração e a distância do próprio FIT: match inequívoco. */
    private TreinoPlanejado planejadoIgualAoFit() throws IOException {
        FitSessionData sessao = fitParseService.parse(new ByteArrayInputStream(arquivoFit()));

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(assessoria);
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(sessao.dataTreino().minusDays(3));
        plano.setSemanaFim(sessao.dataTreino().plusDays(3));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(30));
        plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setObjetivoSemanal("Semana do IT de conciliação");
        plano = planoSemanalRepository.save(plano);

        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setPlanoSemanal(plano);
        planejado.setAtleta(atleta);
        planejado.setTenantId(assessoria.getId());
        planejado.setDataTreino(sessao.dataTreino());
        planejado.setDiaSemana(br.com.menthoros.backend.util.Utils.converterDayOfWeekParaDiaSemana(
                sessao.dataTreino().getDayOfWeek()));
        planejado.setTipoTreino(TipoTreino.CONTINUO);
        planejado.setDuracaoMin(sessao.duracao());
        planejado.setDistanciaKm(BigDecimal.valueOf(sessao.distanciaKm()).setScale(2, RoundingMode.HALF_UP));
        planejado.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
        planejado.setStatusSincronizacao(StatusSincronizacao.NAO_SINCRONIZADO);
        planejado.setTentativasSincronizacao(0);
        return treinoPlanejadoRepository.save(planejado);
    }

    private TreinoRealizado carregar(UUID treinoId) {
        return new TransactionTemplate(transactionManager).execute(s -> {
            TreinoRealizado t = treinoRepository.findById(treinoId).orElseThrow();
            if (t.getTreinoPlanejado() != null) {
                t.getTreinoPlanejado().getId();
            }
            return t;
        });
    }

    private List<TreinoReconciliacao> auditoriaDe(UUID treinoId) {
        return treinoReconciliacaoRepository.findAll().stream()
                .filter(a -> a.getTreinoRealizado() != null && treinoId.equals(a.getTreinoRealizado().getId()))
                .toList();
    }

    private void webhook(String corpo) throws Exception {
        mockMvc.perform(post("/api/v1/garmin/webhook/{segredo}", SEGREDO_WEBHOOK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());
    }

    private String ping(String summaryId) {
        return """
                {"activityFiles":[{"userId":"%s","summaryId":"%s","activityId":"%s",
                  "callbackURL":"%s%s?id=%s&token=token-do-it","activityType":"RUNNING","fileType":"FIT",
                  "deviceName":"%s","startTimeInSeconds":%d,"manual":false}]}
                """.formatted(garminUserId, summaryId, summaryId, garmin.baseUrl(), PATH_ARQUIVO, summaryId,
                DISPOSITIVO, Instant.now().minusSeconds(3600).getEpochSecond());
    }

    private GarminPing pingDo(String summaryId) {
        List<GarminPing> pings = pingsDo(summaryId);
        assertThat(pings).hasSize(1);
        return pings.get(0);
    }

    private List<GarminPing> pingsDo(String summaryId) {
        return pingRepository.findAll().stream().filter(p -> summaryId.equals(p.getSummaryId())).toList();
    }

    private RequestPostProcessor jwtAtleta() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ATLETA"))
                .jwt(j -> j.subject(subjectAtleta.toString())
                        .claim("tenant_id", assessoria.getId().toString())
                        .claim("email", subjectAtleta + "@menthoros.test")
                        .claim("roles", List.of("ATLETA")));
    }

    private static byte[] arquivoFit() throws IOException {
        try (InputStream in = GarminActivityIngestionIT.class.getResourceAsStream(FIT)) {
            assertThat(in).as("fixture %s", FIT).isNotNull();
            return in.readAllBytes();
        }
    }
}
