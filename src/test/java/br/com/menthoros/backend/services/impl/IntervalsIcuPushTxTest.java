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
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.PushResult;
import br.com.menthoros.backend.services.WorkoutChannel;
import br.com.menthoros.backend.services.helper.IntervalsIcuWorkoutConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integração REAL (Testcontainers) do CA1 (change {@code intervals-icu-push-hardening}): TX por
 * treino no push ({@link IntervalsIcuPushProcessor}) garante que um claim perdido
 * ({@link OptimisticLockingFailureException} REAL, provocado por um UPDATE concorrente via JDBC,
 * fora de qualquer {@code EntityManager} do processor) em UM treino do lote nunca arrasta por
 * rollback a marcação já persistida do OUTRO treino do mesmo lote.
 *
 * <p><b>Como o conflito real é plantado:</b> {@code claimNaTx} faz, na mesma TX-A:
 * {@code find} (lê a {@code versao} atual) → {@code converter.converter(treino)} → mutações
 * in-memory → {@code saveAndFlush} (UPDATE condicionado à {@code versao} lida). O único ponto de
 * injeção possível para um UPDATE concorrente cair ENTRE o find e o flush do MESMO treino é o meio
 * do método — por isso o teste usa um {@link MockitoSpyBean} do {@link IntervalsIcuWorkoutConverter}
 * real: no {@code converter()} do treino-alvo (identificado pelo id, não pela ordem de iteração do
 * lote — a query {@code findAllByPlanoSemanalIdAndTenantId} não garante ordem), faz o UPDATE de
 * {@code versao} via {@link JdbcTemplate} (conexão independente, já commitada) e só então delega
 * ao método real. O {@code saveAndFlush} subsequente do processor, com a {@code versao} antiga
 * ainda em memória, colide de verdade contra a linha já incrementada → OptimisticLockingFailureException.
 *
 * <p>Sem {@code @Transactional} na classe: as asserções finais usam consultas NOVAS
 * ({@code treinoPlanejadoRepository.findById}, cada chamada de repository JPA abre a própria
 * transação), garantindo que o que se lê é o que ficou de fato persistido — não um snapshot dentro
 * da transação do próprio teste.
 *
 * <p>{@code onPlanoAprovado} é {@code @Async} contra um executor real (o bean autowired é o proxy
 * do Spring) — chamar o método diretamente não o torna síncrono. O teste aguarda o término do lote
 * via um {@link CountDownLatch} disparado pelo mock de {@code removerOrfaos}, a ÚLTIMA chamada do
 * corpo do listener (garante que os dois treinos já foram processados antes das asserções).
 */
class IntervalsIcuPushTxTest extends AbstractIntegrationTest {

    @MockitoBean
    private WorkoutChannel workoutChannel;

    @MockitoBean
    private IntervalsIcuConnectionService connectionService;

    @MockitoSpyBean
    private IntervalsIcuWorkoutConverter converter;

    @Autowired
    private IntervalsIcuPushListener listener;

    @Autowired
    private TreinoPlanejadoRepository treinoPlanejadoRepository;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;

    @Autowired
    private PlanoSemanalRepository planoSemanalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("lote de 2 treinos: claim perdido REAL (OptimisticLockingFailureException) no 1o "
            + "nao impede o 2o de terminar SINCRONIZADO persistido, e o 1o permanece intacto (CA1)")
    void claimPerdidoDeUmTreinoNaoArrastaMarcacaoJaPersistidaDoOutro() throws InterruptedException {
        Atleta atleta = seedAtleta();
        UUID tenantId = atleta.getAssessoria().getId();
        PlanoSemanal plano = seedPlano(atleta);

        TreinoPlanejado treino1 = seedTreino(atleta, plano); // vai perder o claim
        TreinoPlanejado treino2 = seedTreino(atleta, plano); // vai sincronizar normalmente
        UUID id1 = treino1.getId();
        UUID id2 = treino2.getId();

        IntegracaoExterna conexao = conexaoFake(atleta, tenantId);
        when(connectionService.conexaoAtiva(atleta.getId(), tenantId)).thenReturn(Optional.of(conexao));

        // Injeção do conflito real: EXATAMENTE quando o claim do treino1 chama o converter (entre
        // o find e o saveAndFlush da mesma TX-A), um UPDATE concorrente via JdbcTemplate (conexão
        // própria, já commitada) bumpa a versao do treino1 no banco. O saveAndFlush que vem a
        // seguir, com a versao antiga ainda em memória, falha com OptimisticLockingFailureException
        // real. O converter do treino2 (sem stub) segue para o método real normalmente.
        doAnswer(invocation -> {
            jdbcTemplate.update("UPDATE tb_treino_planejado SET versao = versao + 1 WHERE id = ?", id1);
            return invocation.callRealMethod();
        }).when(converter).converter(argThat(t -> t != null && id1.equals(t.getId())));

        when(workoutChannel.push(eq(conexao), any(), any())).thenReturn(PushResult.okCriado(222L));

        // removerOrfaos é a ÚLTIMA chamada do corpo do listener — dispara o latch quando o lote
        // (os dois treinos) já foi totalmente processado na thread async.
        CountDownLatch loteProcessado = new CountDownLatch(1);
        doAnswer(invocation -> {
            loteProcessado.countDown();
            return null;
        }).when(workoutChannel).removerOrfaos(any(), any(), any(), any());

        listener.onPlanoAprovado(new PlanoAprovadoEvent(plano.getId(), atleta.getId(), tenantId));

        assertThat(loteProcessado.await(10, TimeUnit.SECONDS))
                .as("lote não terminou de processar (async) dentro do timeout")
                .isTrue();

        // O treino1 nunca chega à rede (o claim falha ANTES do push) — só o treino2 chama push.
        verify(workoutChannel, times(1)).push(eq(conexao), any(), any());
        verify(workoutChannel, never()).push(eq(conexao), argThat(w -> w != null
                && w.externalId().endsWith(id1.toString())), any());

        // Consultas NOVAS — nenhuma delas está dentro da TX do listener (que já retornou).
        TreinoPlanejado fresh1 = treinoPlanejadoRepository.findById(id1).orElseThrow();
        TreinoPlanejado fresh2 = treinoPlanejadoRepository.findById(id2).orElseThrow();

        // Treino 1: claim perdido — permanece no estado anterior, elegível a retry. A TX-A dele
        // sofreu rollback completo (o OptimisticLockingFailureException não deixou nada persistir).
        assertThat(fresh1.getStatusSincronizacao())
                .as("claim perdido não muda o status do treino1 — nenhuma marcação descartada por "
                        + "rollback alheio nem própria")
                .isEqualTo(StatusSincronizacao.PENDENTE);
        assertThat(fresh1.getTentativasSincronizacao())
                .as("TX-A do treino1 sofreu rollback: a tentativa registrada em memória nunca foi persistida")
                .isZero();
        assertThat(fresh1.getExternalId()).isNull();
        assertThat(fresh1.precisaSincronizar())
                .as("treino1 continua elegível a retry pelo scheduler")
                .isTrue();

        // Treino 2: termina SINCRONIZADO, persistido e visível em consulta nova — a marcação dele
        // não foi arrastada pelo rollback do treino1.
        assertThat(fresh2.getStatusSincronizacao())
                .as("treino2 sincronizado com sucesso, persistido de forma independente do treino1")
                .isEqualTo(StatusSincronizacao.SINCRONIZADO);
        assertThat(fresh2.getExternalId()).isEqualTo("222");
        assertThat(fresh2.getTentativasSincronizacao()).isEqualTo(1);
        assertThat(fresh2.getSincronizadoEm()).isNotNull();
    }

    // =========================================================================
    // Helpers (espelha TreinoPlanejadoRepositoryTest / DeduplicationConstraintTest)
    // =========================================================================

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Push TX Test");
        assessoria.setDominio("push-tx-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Push TX");
        atleta.setEmail("push-tx-" + UUID.randomUUID() + "@test.com");
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
        // duracaoMin > 0 no próprio treino já basta para o IntervalsIcuWorkoutConverter
        // considerá-lo exportável (caminho stepUnico), sem precisar de etapas prescritivas.
        treino.setDuracaoMin(Duration.ofMinutes(30));
        treino.setStatusSincronizacao(StatusSincronizacao.PENDENTE);
        treino.setTentativasSincronizacao(0);
        return treinoPlanejadoRepository.save(treino);
    }

    /** Conexão intervals.icu não-persistida, devolvida pelo {@link IntervalsIcuConnectionService}
     * mockado. O treino2 sincroniza com sucesso, então o listener REALMENTE chama
     * {@code integracaoExternaRepository.save(conexao)} (real, não mockado) ao fim do lote — por
     * isso a entidade precisa de um {@code atleta} válido (FK NOT NULL) mesmo sem ter sido
     * persistida antes. NÃO atribuir id manualmente: com {@code @GeneratedValue(UUID)} e id nulo,
     * {@code save()} decide corretamente por {@code persist()} (INSERT); um id manual faria
     * {@code save()} tentar {@code merge()} de uma linha inexistente, disparando um
     * {@code StaleObjectStateException} espúrio (sem relação com o {@code @Version} de
     * {@code TreinoPlanejado} que este teste realmente exercita). */
    private IntegracaoExterna conexaoFake(Atleta atleta, UUID tenantId) {
        IntegracaoExterna conexao = new IntegracaoExterna();
        conexao.setAtleta(atleta);
        conexao.setTenantId(tenantId);
        conexao.setPlataforma(FonteDados.INTERVALS_ICU);
        conexao.setExternalAthleteId("999888");
        conexao.setAccessToken("fake-access-token");
        conexao.setAtivo(true);
        return conexao;
    }
}
