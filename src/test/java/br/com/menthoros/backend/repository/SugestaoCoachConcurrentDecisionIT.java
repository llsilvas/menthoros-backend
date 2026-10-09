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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra conexões/transações reais e concorrentes (não um mock de "retorna 0"), que
 * {@link SugestaoCoachRepository#decidirSePendente} de fato serializa duas decisões simultâneas
 * sobre a mesma sugestão {@code PENDING} — achado do QA gate de
 * {@code add-coach-suggestion-decision-audit} (code-reviewer e security-reviewer, ambos
 * independentemente): o único teste existente para esse caminho
 * (`SugestaoCoachServiceImplTest...decisaoConcorrenteLancaConflito`) mockava
 * {@code repository.decidirSePendente(...)} retornando {@code 0} — provava a reação do serviço ao
 * conflito, não que o `UPDATE ... WHERE status = 'PENDING'` real do Postgres garante que só uma
 * transação ganha.
 *
 * <p>Sem {@code @Transactional} na classe de propósito: os dois threads precisam de transações
 * próprias e independentes (via {@link TransactionTemplate}) para a corrida ser real — uma
 * transação de teste envolvendo tudo eliminaria a concorrência que o teste existe para provar.</p>
 */
class SugestaoCoachConcurrentDecisionIT extends AbstractIntegrationTest {

    @Autowired
    private SugestaoCoachRepository sugestaoCoachRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("duas decisões simultâneas na mesma sugestão PENDING: exatamente uma vence")
    void duasDecisoesConcorrentesSoUmaVence() throws Exception {
        Assessoria assessoria = seedAssessoria();
        Atleta atleta = seedAtleta(assessoria);
        SugestaoCoach sugestao = seedSugestaoPendente(assessoria, atleta);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch prontos = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        UUID reviewerAprova = UUID.randomUUID();
        UUID reviewerRejeita = UUID.randomUUID();

        try {
            Callable<Integer> tentarAprovar = () -> {
                prontos.countDown();
                largada.await();
                return transactionTemplate.execute(status -> sugestaoCoachRepository.decidirSePendente(
                        sugestao.getId(), assessoria.getId(), StatusSugestao.PENDING,
                        StatusSugestao.APPROVED, Instant.now(), reviewerAprova, null));
            };
            Callable<Integer> tentarRejeitar = () -> {
                prontos.countDown();
                largada.await();
                return transactionTemplate.execute(status -> sugestaoCoachRepository.decidirSePendente(
                        sugestao.getId(), assessoria.getId(), StatusSugestao.PENDING,
                        StatusSugestao.REJECTED, Instant.now(), reviewerRejeita, "motivo da corrida"));
            };

            Future<Integer> aprovacao = pool.submit(tentarAprovar);
            Future<Integer> rejeicao = pool.submit(tentarRejeitar);

            // Só libera a largada quando as duas threads já estão bloqueadas no await — garante
            // que as duas transações disputam a mesma linha de verdade, não uma depois da outra.
            assertThat(prontos.await(5, TimeUnit.SECONDS)).isTrue();
            largada.countDown();

            int linhasAprovacao = aprovacao.get(10, TimeUnit.SECONDS);
            int linhasRejeicao = rejeicao.get(10, TimeUnit.SECONDS);

            assertThat(linhasAprovacao + linhasRejeicao)
                    .as("exatamente uma das duas transições afeta linha — a outra perde a corrida (0)")
                    .isEqualTo(1);

            SugestaoCoach persistida = sugestaoCoachRepository.findById(sugestao.getId()).orElseThrow();
            if (linhasAprovacao == 1) {
                assertThat(persistida.getStatus()).isEqualTo(StatusSugestao.APPROVED);
                assertThat(persistida.getReviewedBy()).isEqualTo(reviewerAprova);
            } else {
                assertThat(persistida.getStatus()).isEqualTo(StatusSugestao.REJECTED);
                assertThat(persistida.getReviewedBy()).isEqualTo(reviewerRejeita);
                assertThat(persistida.getRejectionReason()).isEqualTo("motivo da corrida");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private Assessoria seedAssessoria() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Concorrencia Decisao " + UUID.randomUUID());
        assessoria.setDominio("concorrencia-decisao-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(assessoria);
    }

    private Atleta seedAtleta(Assessoria assessoria) {
        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Concorrencia");
        atleta.setEmail(UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private SugestaoCoach seedSugestaoPendente(Assessoria assessoria, Atleta atleta) {
        SugestaoCoach sugestao = SugestaoCoach.builder()
                .tenantId(assessoria.getId())
                .atleta(atleta)
                .tipo(TipoSugestao.RECOVERY)
                .status(StatusSugestao.PENDING)
                .confidence("HIGH")
                .summary("Sugestão de teste de concorrência")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return sugestaoCoachRepository.save(sugestao);
    }
}
