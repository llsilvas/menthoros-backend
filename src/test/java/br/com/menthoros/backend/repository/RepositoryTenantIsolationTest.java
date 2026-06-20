package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.*;
import br.com.menthoros.backend.enums.*;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Section 3 — Repository tenant-aware methods (TDD: RED → GREEN)
 *
 * Tests 3.1–3.5 verify that each repository can isolate data by tenantId,
 * preventing cross-tenant data leakage through findByIdAndTenantId queries.
 */
@Transactional
class RepositoryTenantIsolationTest extends AbstractIntegrationTest {

    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;
    @Autowired private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Autowired private TreinoRealizadoRepository treinoRealizadoRepository;
    @Autowired private ProvaRepository provaRepository;

    private Assessoria tenant1;
    private Assessoria tenant2;
    private Atleta atleta1;  // belongs to tenant1
    private Atleta atleta2;  // belongs to tenant2

    @BeforeEach
    void setup() {
        tenant1 = new Assessoria();
        tenant1.setNome("Tenant 1");
        tenant1.setDominio("tenant1-repo-" + UUID.randomUUID());
        tenant1.setPlano(PlanoAssessoria.BASIC);
        tenant1 = assessoriaRepository.save(tenant1);

        tenant2 = new Assessoria();
        tenant2.setNome("Tenant 2");
        tenant2.setDominio("tenant2-repo-" + UUID.randomUUID());
        tenant2.setPlano(PlanoAssessoria.BASIC);
        tenant2 = assessoriaRepository.save(tenant2);

        atleta1 = new Atleta();
        atleta1.setNome("Atleta Tenant 1");
        atleta1.setObjetivo("Correr 5km");
        atleta1.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta1.setAtivo(AtletaStatus.ATIVO);
        atleta1.setAssessoria(tenant1);
        atleta1 = atletaRepository.save(atleta1);

        atleta2 = new Atleta();
        atleta2.setNome("Atleta Tenant 2");
        atleta2.setObjetivo("Correr 10km");
        atleta2.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta2.setAtivo(AtletaStatus.ATIVO);
        atleta2.setAssessoria(tenant2);
        atleta2 = atletaRepository.save(atleta2);
    }

    // =========================================================================
    // 3.1 — PlanoSemanalRepository.findByIdAndTenantId
    // =========================================================================

    @Test
    @DisplayName("3.1: PlanoSemanal — findByIdAndTenantId retorna empty quando tenant não coincide (previne cross-tenant leak)")
    void planoSemanal_findByIdAndTenantId_retornaVazioQuandoTenantNaoCoincide() {
        // ARRANGE — cria metadados e plano para atleta do tenant1
        PlanoMetaDados meta = buildMetaDados(atleta1);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = buildPlanoSemanal(atleta1, tenant1, meta);
        plano = planoSemanalRepository.save(plano);

        UUID planoId = plano.getId();

        // ACT — busca com tenant2 (não deve encontrar)
        Optional<PlanoSemanal> resultadoTenant2 = planoSemanalRepository
                .findByIdAndTenantId(planoId, tenant2.getId());

        // ASSERT — deve ser vazio (sem vazamento entre tenants)
        assertThat(resultadoTenant2).isEmpty();
    }

    @Test
    @DisplayName("3.1: PlanoSemanal — findByIdAndTenantId retorna plano quando tenant coincide")
    void planoSemanal_findByIdAndTenantId_retornaPlanoQuandoTenantCoincide() {
        PlanoMetaDados meta = buildMetaDados(atleta1);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = buildPlanoSemanal(atleta1, tenant1, meta);
        plano = planoSemanalRepository.save(plano);

        Optional<PlanoSemanal> resultado = planoSemanalRepository
                .findByIdAndTenantId(plano.getId(), tenant1.getId());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(plano.getId());
    }

    // =========================================================================
    // 3.2 — TreinoPlanejadoRepository.findByIdAndTenantId (já existe — verifica isolamento)
    // =========================================================================

    @Test
    @DisplayName("3.2: TreinoPlanejado — findByIdAndTenantId retorna empty quando tenant não coincide")
    void treinoPlanejado_findByIdAndTenantId_retornaVazioQuandoTenantNaoCoincide() {
        PlanoMetaDados meta = buildMetaDados(atleta1);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = buildPlanoSemanal(atleta1, tenant1, meta);
        plano = planoSemanalRepository.save(plano);

        TreinoPlanejado treino = buildTreinoPlanejado(atleta1, plano, tenant1.getId());
        treino = treinoPlanejadoRepository.save(treino);

        Optional<TreinoPlanejado> resultado = treinoPlanejadoRepository
                .findByIdAndTenantId(treino.getId(), tenant2.getId());

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("3.2: TreinoPlanejado — findByIdAndTenantId retorna treino quando tenant coincide")
    void treinoPlanejado_findByIdAndTenantId_retornaQuandoTenantCoincide() {
        PlanoMetaDados meta = buildMetaDados(atleta1);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = buildPlanoSemanal(atleta1, tenant1, meta);
        plano = planoSemanalRepository.save(plano);

        TreinoPlanejado treino = buildTreinoPlanejado(atleta1, plano, tenant1.getId());
        treino = treinoPlanejadoRepository.save(treino);

        Optional<TreinoPlanejado> resultado = treinoPlanejadoRepository
                .findByIdAndTenantId(treino.getId(), tenant1.getId());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(treino.getId());
    }

    // =========================================================================
    // 3.3 — TreinoRealizadoRepository.findByIdAndTenantId (já existe — verifica isolamento)
    // =========================================================================

    @Test
    @DisplayName("3.3: TreinoRealizado — findByIdAndTenantId retorna empty quando tenant não coincide")
    void treinoRealizado_findByIdAndTenantId_retornaVazioQuandoTenantNaoCoincide() {
        TreinoRealizado treino = buildTreinoRealizado(atleta1, tenant1.getId());
        treino = treinoRealizadoRepository.save(treino);

        Optional<TreinoRealizado> resultado = treinoRealizadoRepository
                .findByIdAndTenantId(treino.getId(), tenant2.getId());

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("3.3: TreinoRealizado — findByIdAndTenantId retorna treino quando tenant coincide")
    void treinoRealizado_findByIdAndTenantId_retornaQuandoTenantCoincide() {
        TreinoRealizado treino = buildTreinoRealizado(atleta1, tenant1.getId());
        treino = treinoRealizadoRepository.save(treino);

        Optional<TreinoRealizado> resultado = treinoRealizadoRepository
                .findByIdAndTenantId(treino.getId(), tenant1.getId());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(treino.getId());
    }

    // =========================================================================
    // 3.4 — PlanoMetadadosRepository.findByIdAndTenantId
    // =========================================================================

    @Test
    @DisplayName("3.4: PlanoMetaDados — findByIdAndTenantId retorna empty quando tenant não coincide")
    void planoMetaDados_findByIdAndTenantId_retornaVazioQuandoTenantNaoCoincide() {
        PlanoMetaDados meta = buildMetaDados(atleta1);
        meta = planoMetadadosRepository.save(meta);

        Optional<PlanoMetaDados> resultado = planoMetadadosRepository
                .findByIdAndTenantId(meta.getId(), tenant2.getId());

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("3.4: PlanoMetaDados — findByIdAndTenantId retorna metadados quando tenant coincide")
    void planoMetaDados_findByIdAndTenantId_retornaQuandoTenantCoincide() {
        PlanoMetaDados meta = buildMetaDados(atleta1);
        meta = planoMetadadosRepository.save(meta);

        Optional<PlanoMetaDados> resultado = planoMetadadosRepository
                .findByIdAndTenantId(meta.getId(), tenant1.getId());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(meta.getId());
    }

    // =========================================================================
    // 3.5 — ProvaRepository.findByIdAndTenantId
    // =========================================================================

    @Test
    @DisplayName("3.5: Prova — findByIdAndTenantId retorna empty quando tenant não coincide")
    void prova_findByIdAndTenantId_retornaVazioQuandoTenantNaoCoincide() {
        Prova prova = buildProva(atleta1, tenant1);
        prova = provaRepository.save(prova);

        Optional<Prova> resultado = provaRepository
                .findByIdAndTenantId(prova.getId(), tenant2.getId());

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("3.5: Prova — findByIdAndTenantId retorna prova quando tenant coincide")
    void prova_findByIdAndTenantId_retornaQuandoTenantCoincide() {
        Prova prova = buildProva(atleta1, tenant1);
        prova = provaRepository.save(prova);

        Optional<Prova> resultado = provaRepository
                .findByIdAndTenantId(prova.getId(), tenant1.getId());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(prova.getId());
    }

    // =========================================================================
    // Helper builders
    // =========================================================================

    private PlanoMetaDados buildMetaDados(Atleta atleta) {
        PlanoMetaDados m = new PlanoMetaDados();
        m.setAtleta(atleta);
        m.setAssessoria(atleta.getAssessoria());
        m.setDiaPreferidoLongo(DiaSemana.SABADO);
        return m;
    }

    private PlanoSemanal buildPlanoSemanal(Atleta atleta, Assessoria assessoria, PlanoMetaDados meta) {
        PlanoSemanal ps = new PlanoSemanal();
        ps.setAtleta(atleta);
        ps.setAssessoria(assessoria);
        ps.setPlanoMetaDados(meta);
        ps.setSemanaInicio(LocalDate.now());
        ps.setSemanaFim(LocalDate.now().plusDays(6));
        ps.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        ps.setStatus(PlanoStatus.PLANEJADO);
        ps.setReviewStatus(PlanoReviewStatus.AGUARDANDO_REVISAO);
        ps.setObjetivoSemanal("Semana base");
        return ps;
    }

    private TreinoPlanejado buildTreinoPlanejado(Atleta atleta, PlanoSemanal plano, UUID tenantId) {
        TreinoPlanejado tp = new TreinoPlanejado();
        tp.setAtleta(atleta);
        tp.setPlanoSemanal(plano);
        tp.setTenantId(tenantId);
        tp.setDataTreino(LocalDate.now().plusDays(1));
        tp.setTipoTreino(TipoTreino.FACIL);
        tp.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
        tp.setFonteDados(FonteDados.IA_GERADO);
        tp.setDuracaoMin(Duration.ofMinutes(60));
        tp.setDistanciaKm(BigDecimal.valueOf(10));
        tp.setDiaSemana(DiaSemana.SEGUNDA);
        return tp;
    }

    private TreinoRealizado buildTreinoRealizado(Atleta atleta, UUID tenantId) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setAtleta(atleta);
        tr.setTenantId(tenantId);
        tr.setDataTreino(LocalDate.now());
        tr.setTipoTreino(TipoTreino.FACIL);
        tr.setDuracaoMin(Duration.ofMinutes(45));
        tr.setDistanciaKm(BigDecimal.valueOf(8));
        tr.setDiaSemana(DiaSemana.SEGUNDA);
        tr.setFonteDados(FonteDados.MANUAL);
        tr.setStatus(TreinoExecucaoStatus.REALIZADO);
        tr.setReconciliationStatus(ReconciliationStatus.PENDENTE);
        tr.setStatusSincronizacao(StatusSincronizacao.NAO_SINCRONIZADO);
        // required NOT NULL columns
        tr.setFcMedia(150);
        tr.setFcMax(175);
        tr.setPaceMedia(Duration.ofMinutes(5).plusSeconds(30));
        return tr;
    }

    private Prova buildProva(Atleta atleta, Assessoria assessoria) {
        Prova p = new Prova();
        p.setAtleta(atleta);
        p.setAssessoria(assessoria);
        p.setNomeProva("Maratona Test " + UUID.randomUUID());
        p.setDataProva(LocalDate.now().plusDays(90));
        p.setDistancia(DistanciaProva.KM_42);
        p.setTipoProva(TipoProva.CORRIDA_RUA);
        p.setStatusProva(ProvaStatus.PLANEJADA);
        return p;
    }
}
