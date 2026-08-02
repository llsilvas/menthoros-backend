package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Task 6.8 — integração REAL (Testcontainers) do fluxo de import de activity do intervals.icu via
 * {@link IntervalsIcuActivityController} + {@code IntervalsIcuActivityIngestionServiceImpl} +
 * {@code IntervalsIcuActivityPersister} reais (design.md D3 passo 1, D5.2). Único ponto mockado é
 * o {@link IntervalsIcuClient} (a chamada HTTP externa) — persistência, dedup, TSS/TSB e
 * reconciliação passam pelo banco real.
 *
 * <p>Autenticação/tenant são estabelecidos diretamente ({@link TestingAuthenticationToken} +
 * {@link TenantContext#setTenantId}), sem passar pela cadeia HTTP/JWT — já coberta com o service
 * mockado em {@link IntervalsIcuActivityControllerAuthTest} (Bloco 5). Chamar o bean gerenciado
 * pelo Spring (não `new Controller(...)`) preserva os aspectos reais de {@code @PreAuthorize} e
 * {@code @RequireTenant}.
 *
 * <p><b>Achado do smoke real do Bloco 7 (2026-07-16):</b> esta suíte originalmente rodava com
 * {@code @Transactional} de classe, o que mascarava um bug real — mantinha a sessão Hibernate
 * aberta artificialmente durante a chamada, escondendo a mesma
 * {@code LazyInitializationException} que ocorreu contra dado real (perfil {@code dev},
 * {@code open-in-view: false} — NÃO há OSIV cobrindo a janela, ao contrário do que o comentário
 * anterior assumia). O bug real: o passo 0 (dedup) de
 * {@code IntervalsIcuActivityIngestionServiceImpl} busca o {@code TreinoRealizado} já existente
 * fora de transação e mapeia direto para DTO, tocando a coleção lazy {@code etapasRealizadas}.
 * Corrigido com {@code @EntityGraph} em
 * {@code TreinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId} (fetch eager na
 * mesma query) — por isso esta classe NÃO usa mais {@code @Transactional}: o teste precisa
 * reproduzir a ausência de sessão ambiente da produção para continuar detectando essa classe de
 * regressão.
 */
class IntervalsIcuActivityImportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntervalsIcuActivityController controller;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private IntegracaoExternaRepository integracaoExternaRepository;

    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;

    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;

    @MockitoBean
    private IntervalsIcuClient intervalsIcuClient;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Nested
    @DisplayName("importarAtividade — precondição Strava (design.md D3 passo 1 / D5.2)")
    class PrecondicaoStrava {

        @Test
        @DisplayName("bloqueado: Strava ativo e não pausado -> 409, nada persistido, client nunca chamado")
        void bloqueiaComStravaAtivoNaoPausado() {
            Atleta atleta = seedAtleta();
            UUID tenantId = atleta.getAssessoria().getId();
            seedIntervalsIcuConexao(atleta, tenantId, "999888");
            seedStravaConexao(atleta, tenantId, false);
            autenticarComoTecnico(tenantId);

            assertThatThrownBy(() -> controller.importarAtividade(atleta.getId(), "i166338796"))
                    .isInstanceOf(DomainConflictException.class);

            verifyNoInteractions(intervalsIcuClient);
            assertThat(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(
                    tenantId, FonteDados.INTERVALS_ICU, "i166338796")).isEmpty();
        }

        @Test
        @DisplayName("liberado: sem conexão Strava -> import prossegue (200)")
        void liberaSemConexaoStrava() {
            Atleta atleta = seedAtleta();
            UUID tenantId = atleta.getAssessoria().getId();
            seedIntervalsIcuConexao(atleta, tenantId, "999888");
            autenticarComoTecnico(tenantId);
            when(intervalsIcuClient.buscarAtividade(any(), any(), anyBoolean())).thenReturn(activityDto("i166338796", "999888"));

            var resposta = controller.importarAtividade(atleta.getId(), "i166338796");

            assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(
                    tenantId, FonteDados.INTERVALS_ICU, "i166338796")).isPresent();
        }

        @Test
        @DisplayName("liberado: Strava ativo mas autoSyncPausado -> import prossegue (200)")
        void liberaComStravaPausado() {
            Atleta atleta = seedAtleta();
            UUID tenantId = atleta.getAssessoria().getId();
            seedIntervalsIcuConexao(atleta, tenantId, "999888");
            seedStravaConexao(atleta, tenantId, true);
            autenticarComoTecnico(tenantId);
            when(intervalsIcuClient.buscarAtividade(any(), any(), anyBoolean())).thenReturn(activityDto("i166338796", "999888"));

            var resposta = controller.importarAtividade(atleta.getId(), "i166338796");

            assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(
                    tenantId, FonteDados.INTERVALS_ICU, "i166338796")).isPresent();
        }

        @Test
        @DisplayName("re-import: activity já importada + Strava ativo não pausado -> 200 com treino "
                + "existente, SEM 409 (dedup do passo 0 tem prioridade sobre a precondição, CA2)")
        void reimportComPrecondicaoQueBloquearIaUmImportNovo() {
            Atleta atleta = seedAtleta();
            UUID tenantId = atleta.getAssessoria().getId();
            seedIntervalsIcuConexao(atleta, tenantId, "999888");
            seedStravaConexao(atleta, tenantId, false);
            TreinoRealizado existente = seedTreinoRealizadoExistente(atleta, tenantId, "i166338796");
            autenticarComoTecnico(tenantId);

            TreinoRealizadoOutputDto resposta = controller.importarAtividade(atleta.getId(), "i166338796").getBody();

            assertThat(resposta).isNotNull();
            assertThat(resposta.id()).isEqualTo(existente.getId());
            verifyNoInteractions(intervalsIcuClient);
        }
    }

    // =========================================================================
    // Helpers (espelha IntervalsIcuPushTxTest)
    // =========================================================================

    private void autenticarComoTecnico(UUID tenantId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("tecnico-teste", null, List.of(new SimpleGrantedAuthority("ROLE_TECNICO"))));
        TenantContext.setTenantId(tenantId);
    }

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Import IT Test");
        assessoria.setDominio("import-it-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Import IT");
        atleta.setEmail("import-it-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        planoMetadadosRepository.save(meta);

        return atleta;
    }

    private IntegracaoExterna seedIntervalsIcuConexao(Atleta atleta, UUID tenantId, String externalAthleteId) {
        IntegracaoExterna conexao = new IntegracaoExterna();
        conexao.setAtleta(atleta);
        conexao.setTenantId(tenantId);
        conexao.setPlataforma(FonteDados.INTERVALS_ICU);
        conexao.setExternalAthleteId(externalAthleteId);
        conexao.setAccessToken("fake-api-key");
        conexao.setAtivo(true);
        return integracaoExternaRepository.save(conexao);
    }

    private IntegracaoExterna seedStravaConexao(Atleta atleta, UUID tenantId, boolean autoSyncPausado) {
        IntegracaoExterna conexao = new IntegracaoExterna();
        conexao.setAtleta(atleta);
        conexao.setTenantId(tenantId);
        conexao.setPlataforma(FonteDados.STRAVA);
        conexao.setExternalAthleteId("strava-athlete-1");
        conexao.setAccessToken("fake-strava-token");
        conexao.setAtivo(true);
        conexao.setAutoSyncPausado(autoSyncPausado);
        return integracaoExternaRepository.save(conexao);
    }

    private TreinoRealizado seedTreinoRealizadoExistente(Atleta atleta, UUID tenantId, String externalId) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setTenantId(tenantId);
        treino.setDataTreino(LocalDate.now());
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(TipoTreino.FACIL);
        treino.setDuracaoMin(Duration.ofMinutes(30));
        treino.setExternalId(externalId);
        treino.setFonteDados(FonteDados.INTERVALS_ICU);
        treino.setStatus(TreinoExecucaoStatus.REALIZADO);
        treino.setStatusSincronizacao(StatusSincronizacao.PENDENTE);
        return treinoRealizadoRepository.save(treino);
    }

    private IcuActivityDto activityDto(String activityId, String athleteId) {
        return new IcuActivityDto(activityId, athleteId, "Run", "Corrida de teste", "2026-07-16T08:00:00",
                1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null, null, null);
    }
    @Nested
    @DisplayName("etapas do intervals.icu (change intervals-icu-activity-laps)")
    class Etapas {

        @Test
        @DisplayName("CA1: import grava as etapas em tb_etapa_realizada com ordem sequencial e FK")
        void importGravaEtapas() {
            Atleta atleta = seedAtleta();
            UUID tenantId = atleta.getAssessoria().getId();
            seedIntervalsIcuConexao(atleta, tenantId, "999888");
            autenticarComoTecnico(tenantId);
            when(intervalsIcuClient.buscarAtividade(any(), any(), anyBoolean()))
                    .thenReturn(activityComIntervalos("i171415754", "999888"));

            controller.importarAtividade(atleta.getId(), "i171415754");

            TreinoRealizado salvo = treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(
                    tenantId, FonteDados.INTERVALS_ICU, "i171415754").orElseThrow();
            assertThat(salvo.getEtapasRealizadas()).hasSize(2);
            assertThat(salvo.getEtapasRealizadas()).extracting(EtapaRealizada::getOrdem)
                    .containsExactly(1, 2);
            assertThat(salvo.getEtapasRealizadas()).allSatisfy(e ->
                    assertThat(e.getTreinoRealizado().getId()).isEqualTo(salvo.getId()));
        }

        @Test
        @DisplayName("CA9: zona, intensidade e inclinacao chegam ao banco — inclinacao em percentual")
        void gravaZonaIntensidadeInclinacao() {
            Atleta atleta = seedAtleta();
            UUID tenantId = atleta.getAssessoria().getId();
            seedIntervalsIcuConexao(atleta, tenantId, "999888");
            autenticarComoTecnico(tenantId);
            when(intervalsIcuClient.buscarAtividade(any(), any(), anyBoolean()))
                    .thenReturn(activityComIntervalos("i171415754", "999888"));

            controller.importarAtividade(atleta.getId(), "i171415754");

            EtapaRealizada etapa = treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(
                    tenantId, FonteDados.INTERVALS_ICU, "i171415754").orElseThrow()
                    .getEtapasRealizadas().get(0);
            assertThat(etapa.getZona()).isEqualTo(1);
            assertThat(etapa.getIntensidadePct()).isEqualByComparingTo(new java.math.BigDecimal("75.00"));
            // 0.0011977126 na fonte -> 0,1% no banco
            assertThat(etapa.getInclinacaoMediaPct()).isEqualByComparingTo(new java.math.BigDecimal("0.1"));
            // 113.24149 mm na fonte -> 11,3 cm no banco (NUMERIC(4,1) nao comportaria o valor cru)
            assertThat(etapa.getOscilacaoVerticalCm()).isEqualByComparingTo(new java.math.BigDecimal("11.3"));
        }

        @Test
        @DisplayName("re-import serializa o treino COM etapas sem LazyInitializationException")
        void reimportNaoLancaLazyInitialization() {
            Atleta atleta = seedAtleta();
            UUID tenantId = atleta.getAssessoria().getId();
            seedIntervalsIcuConexao(atleta, tenantId, "999888");
            autenticarComoTecnico(tenantId);
            when(intervalsIcuClient.buscarAtividade(any(), any(), anyBoolean()))
                    .thenReturn(activityComIntervalos("i171415754", "999888"));
            controller.importarAtividade(atleta.getId(), "i171415754");

            // Passo 0 (dedup): roda FORA de transacao e mapeia direto para DTO. A colecao de etapas
            // e LAZY — sem o @EntityGraph do repositorio isto estoura em open-in-view=false.
            var resposta = controller.importarAtividade(atleta.getId(), "i171415754");

            assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(resposta.getBody()).isNotNull();
            assertThat(resposta.getBody().etapasRealizadas()).hasSize(2);
        }

        @Test
        @DisplayName("backfill completa um treino importado sem etapas, sem tocar no summary")
        void backfillCompletaTreinoAntigo() {
            Atleta atleta = seedAtleta();
            UUID tenantId = atleta.getAssessoria().getId();
            seedIntervalsIcuConexao(atleta, tenantId, "999888");
            autenticarComoTecnico(tenantId);
            // Treino "legado": importado antes da ingestao de etapas existir.
            when(intervalsIcuClient.buscarAtividade(any(), any(), anyBoolean()))
                    .thenReturn(activityDto("i171415754", "999888"));
            controller.importarAtividade(atleta.getId(), "i171415754");
            TreinoRealizado antes = treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(
                    tenantId, FonteDados.INTERVALS_ICU, "i171415754").orElseThrow();
            assertThat(antes.getEtapasRealizadas()).isEmpty();
            var distanciaAntes = antes.getDistanciaKm();

            when(intervalsIcuClient.buscarAtividade(any(), any(), anyBoolean()))
                    .thenReturn(activityComIntervalos("i171415754", "999888"));
            var resultado = controller.backfillEtapas(atleta.getId());

            assertThat(resultado.getBody()).isNotNull();
            assertThat(resultado.getBody().candidatos()).isEqualTo(1);
            assertThat(resultado.getBody().atualizados()).isEqualTo(1);

            TreinoRealizado depois = treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(
                    tenantId, FonteDados.INTERVALS_ICU, "i171415754").orElseThrow();
            assertThat(depois.getEtapasRealizadas()).hasSize(2);
            // O summary nao foi remapeado.
            assertThat(depois.getDistanciaKm()).isEqualByComparingTo(distanciaAntes);
        }

        @Test
        @DisplayName("backfill e idempotente: segunda execucao nao acha candidatos")
        void backfillIdempotente() {
            Atleta atleta = seedAtleta();
            UUID tenantId = atleta.getAssessoria().getId();
            seedIntervalsIcuConexao(atleta, tenantId, "999888");
            autenticarComoTecnico(tenantId);
            when(intervalsIcuClient.buscarAtividade(any(), any(), anyBoolean()))
                    .thenReturn(activityComIntervalos("i171415754", "999888"));
            controller.importarAtividade(atleta.getId(), "i171415754");

            var resultado = controller.backfillEtapas(atleta.getId());

            assertThat(resultado.getBody()).isNotNull();
            assertThat(resultado.getBody().candidatos()).isZero();
        }
    }

    private IcuActivityDto activityComIntervalos(String activityId, String athleteId) {
        var intervalo1 = new br.com.menthoros.backend.dto.intervalsicu.IcuActivityIntervalDto(
                7130765L, "WORK", null, 0, 1001.92, 388, 388, 2.582268, 127.0, 145.0, 81.3866,
                null, 2.4000244, 0.95185256, 249.62077, 51.06683, 113.24149, 11.984201, 24.425259,
                1, 75.0, 0.0011977126);
        var intervalo2 = new br.com.menthoros.backend.dto.intervalsicu.IcuActivityIntervalDto(
                1483778L, "RECOVERY", null, 389, 500.67, 195, 195, 2.5675383, 139.0, 145.0, 81.34359,
                null, 0.0, 0.94692343, 253.0, 50.8, 103.7, 11.06, 19.0,
                1, 82.0, -0.003195669);
        return new IcuActivityDto(activityId, athleteId, "Run", "Corrida de teste", "2026-07-16T08:00:00",
                1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null,
                2, java.util.List.of(intervalo1, intervalo2));
    }
}
