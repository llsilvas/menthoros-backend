package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.SugestaoCoach;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** add-coach-suggestion-edit-delta — contra Postgres real (migration V103 e UPDATEs condicionais). */
class SugestaoCoachEditDeltaIT extends AbstractIntegrationTest {

    @Autowired
    private SugestaoCoachRepository sugestaoCoachRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("V103: sugestão nova nasce nunca editada, versao 0, e as constraints antigas seguem")
    void migrationAplicaDefaults() {
        SugestaoCoach s = sugestaoCoachRepository.saveAndFlush(seedSugestaoPendente());

        SugestaoCoach lida = sugestaoCoachRepository.findById(s.getId()).orElseThrow();
        assertThat(lida.getSummaryOriginal()).isNull();
        assertThat(lida.isEditadoPeloCoach()).isFalse();
        assertThat(lida.getEditedAt()).isNull();
        assertThat(lida.getVersao()).isZero();

        Integer constraints = jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint WHERE conname = 'chk_sugestao_status'
                """, Integer.class);
        Integer indice = jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes WHERE indexname = 'uk_sugestao_pending'
                """, Integer.class);
        assertThat(constraints).isEqualTo(1);
        assertThat(indice).isEqualTo(1);
    }

    @Test
    @DisplayName("editarSePendente: 1ª edição fixa summaryOriginal; 2ª não o sobrescreve; versao sobe (CA1, CA2)")
    void primeiraEdicaoFixaOriginal() {
        SugestaoCoach s = sugestaoCoachRepository.saveAndFlush(seedSugestaoPendente());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThat(inTx(tx, () -> sugestaoCoachRepository.editarSePendente(
                s.getId(), s.getTenantId(), StatusSugestao.PENDING, "texto 1", Instant.now(), 0L))).isEqualTo(1);
        assertThat(inTx(tx, () -> sugestaoCoachRepository.editarSePendente(
                s.getId(), s.getTenantId(), StatusSugestao.PENDING, "texto 2", Instant.now(), 1L))).isEqualTo(1);

        SugestaoCoach lida = sugestaoCoachRepository.findById(s.getId()).orElseThrow();
        assertThat(lida.getSummaryOriginal()).isEqualTo("Reduzir volume em 20%");
        assertThat(lida.getSummary()).isEqualTo("texto 2");
        assertThat(lida.isEditadoPeloCoach()).isTrue();
        assertThat(lida.getEditedAt()).isNotNull();
        assertThat(lida.getVersao()).isEqualTo(2L);
    }

    @Test
    @DisplayName("editarSePendente com versão desatualizada não grava nada (CA8)")
    void versaoDesatualizadaNaoGrava() {
        SugestaoCoach s = sugestaoCoachRepository.saveAndFlush(seedSugestaoPendente());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThat(inTx(tx, () -> sugestaoCoachRepository.editarSePendente(
                s.getId(), s.getTenantId(), StatusSugestao.PENDING, "x", Instant.now(), 5L))).isZero();
        assertThat(sugestaoCoachRepository.findById(s.getId()).orElseThrow().getSummary())
                .isEqualTo("Reduzir volume em 20%");
    }

    @Test
    @DisplayName("editar depois de decidir: decisão incrementa versao e editar não grava em sugestão APPROVED (CA12)")
    void editarAposDecisaoNaoGrava() {
        SugestaoCoach s = sugestaoCoachRepository.saveAndFlush(seedSugestaoPendente());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThat(inTx(tx, () -> sugestaoCoachRepository.decidirSePendente(
                s.getId(), s.getTenantId(), StatusSugestao.PENDING, StatusSugestao.APPROVED,
                Instant.now(), UUID.randomUUID(), null, 0L))).isEqualTo(1);
        assertThat(inTx(tx, () -> sugestaoCoachRepository.editarSePendente(
                s.getId(), s.getTenantId(), StatusSugestao.PENDING, "tarde demais", Instant.now(), 0L))).isZero();

        SugestaoCoach lida = sugestaoCoachRepository.findById(s.getId()).orElseThrow();
        assertThat(lida.getSummary()).isEqualTo("Reduzir volume em 20%");
        assertThat(lida.getVersao()).isEqualTo(1L);
    }

    @Test
    @DisplayName("decidirSePendente com versaoEsperada desatualizada não decide; sem ela decide (CA9)")
    void decisaoComVersaoDesatualizada() {
        SugestaoCoach s = sugestaoCoachRepository.saveAndFlush(seedSugestaoPendente());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(st -> sugestaoCoachRepository.editarSePendente(
                s.getId(), s.getTenantId(), StatusSugestao.PENDING, "editado por B", Instant.now(), 0L));

        assertThat(inTx(tx, () -> sugestaoCoachRepository.decidirSePendente(
                s.getId(), s.getTenantId(), StatusSugestao.PENDING, StatusSugestao.APPROVED,
                Instant.now(), UUID.randomUUID(), null, 0L))).isZero();
        assertThat(inTx(tx, () -> sugestaoCoachRepository.decidirSePendente(
                s.getId(), s.getTenantId(), StatusSugestao.PENDING, StatusSugestao.APPROVED,
                Instant.now(), UUID.randomUUID(), null, null))).isEqualTo(1);
    }

    @Test
    @DisplayName("tenant diferente: editar e decidir não afetam nem gravam nada")
    void outroTenantNaoAfeta() {
        SugestaoCoach s = sugestaoCoachRepository.saveAndFlush(seedSugestaoPendente());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID outroTenant = UUID.randomUUID();

        assertThat(inTx(tx, () -> sugestaoCoachRepository.editarSePendente(
                s.getId(), outroTenant, StatusSugestao.PENDING, "invasor", Instant.now(), 0L))).isZero();
        assertThat(inTx(tx, () -> sugestaoCoachRepository.decidirSePendente(
                s.getId(), outroTenant, StatusSugestao.PENDING, StatusSugestao.APPROVED,
                Instant.now(), UUID.randomUUID(), null, null))).isZero();

        SugestaoCoach lida = sugestaoCoachRepository.findById(s.getId()).orElseThrow();
        assertThat(lida.getSummary()).isEqualTo("Reduzir volume em 20%");
        assertThat(lida.getStatus()).isEqualTo(StatusSugestao.PENDING);
    }

    @Test
    @DisplayName("editar x editar e editar x aprovar em paralelo, mesma versao: exatamente uma vence")
    void corridaEmParaleloSoUmaVence() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        for (boolean contraDecisao : new boolean[] {false, true}) {
            SugestaoCoach s = sugestaoCoachRepository.saveAndFlush(seedSugestaoPendente());
            CountDownLatch prontos = new CountDownLatch(2);
            CountDownLatch largada = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Callable<Integer> editar = () -> {
                    prontos.countDown();
                    largada.await();
                    return inTx(tx, () -> sugestaoCoachRepository.editarSePendente(
                            s.getId(), s.getTenantId(), StatusSugestao.PENDING, "edicao A", Instant.now(), 0L));
                };
                Callable<Integer> concorrente = () -> {
                    prontos.countDown();
                    largada.await();
                    return inTx(tx, () -> contraDecisao
                            ? sugestaoCoachRepository.decidirSePendente(s.getId(), s.getTenantId(),
                                    StatusSugestao.PENDING, StatusSugestao.APPROVED, Instant.now(),
                                    UUID.randomUUID(), null, 0L)
                            : sugestaoCoachRepository.editarSePendente(s.getId(), s.getTenantId(),
                                    StatusSugestao.PENDING, "edicao B", Instant.now(), 0L));
                };
                var a = pool.submit(editar);
                var b = pool.submit(concorrente);
                assertThat(prontos.await(5, TimeUnit.SECONDS)).isTrue();
                largada.countDown();

                assertThat(a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS))
                        .as("exatamente uma das duas escritas afeta linha (contraDecisao=%s)", contraDecisao)
                        .isEqualTo(1);
                assertThat(sugestaoCoachRepository.findById(s.getId()).orElseThrow().getVersao()).isEqualTo(1L);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    /** int primitivo evita a ambiguidade de assertThat(Integer) com lambdas de TransactionTemplate. */
    private static int inTx(TransactionTemplate tx, Supplier<Integer> acao) {
        return tx.execute(st -> acao.get());
    }

    private SugestaoCoach seedSugestaoPendente() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Edit Delta " + UUID.randomUUID());
        assessoria.setDominio("edit-delta-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Edit Delta");
        atleta.setEmail(UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        return SugestaoCoach.builder()
                .tenantId(assessoria.getId())
                .atleta(atleta)
                .tipo(TipoSugestao.RECOVERY)
                .status(StatusSugestao.PENDING)
                .confidence("HIGH")
                .summary("Reduzir volume em 20%")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
