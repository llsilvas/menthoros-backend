package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
 * <p>{@code @Transactional} na classe (rollback automático do framework de teste ao fim de cada
 * método) mantém a sessão Hibernate aberta durante toda a chamada — sem isso, o mapeamento de
 * saída ({@code TreinoMapper.toOutputDto}, que acessa a coleção lazy {@code etapasRealizadas})
 * lançaria {@code LazyInitializationException}: em produção o filtro Open-Session-In-View cobre
 * essa janela, mas aqui não há requisição HTTP real.
 */
@Transactional
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
            when(intervalsIcuClient.buscarAtividade(any(), any())).thenReturn(activityDto("i166338796", "999888"));

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
            when(intervalsIcuClient.buscarAtividade(any(), any())).thenReturn(activityDto("i166338796", "999888"));

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
                1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null);
    }
}
