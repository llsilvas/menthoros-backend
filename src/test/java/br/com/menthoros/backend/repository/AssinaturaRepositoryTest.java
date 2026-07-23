package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Assinatura;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.StatusAssinatura;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integração real (Testcontainers) das queries de {@link AssinaturaRepository} contra o schema V68
 * — prova que o lookup 1:1 por assessoria (CA14), o lookup do webhook (Decisão 4) e a query do job
 * de carência (CA5) resolvem de fato o JPQL/schema executado.
 */
@Transactional
class AssinaturaRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AssinaturaRepository assinaturaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("findByAssessoriaId")
    class FindByAssessoriaId {

        @Test
        @DisplayName("retorna a assinatura da assessoria quando existe")
        void retornaQuandoExiste() {
            Assessoria assessoria = seedAssessoria();
            Assinatura assinatura = seedAssinatura(assessoria, StatusAssinatura.ATIVA, null, "sub_1");
            flushClear();

            Optional<Assinatura> resultado = assinaturaRepository.findByAssessoriaId(assessoria.getId());

            assertThat(resultado).isPresent();
            assertThat(resultado.get().getId()).isEqualTo(assinatura.getId());
        }

        @Test
        @DisplayName("vazio quando a assessoria não tem assinatura")
        void vazioQuandoNaoExiste() {
            Optional<Assinatura> resultado = assinaturaRepository.findByAssessoriaId(UUID.randomUUID());

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByAsaasSubscriptionId")
    class FindByAsaasSubscriptionId {

        @Test
        @DisplayName("resolve pelo id da subscription do Asaas")
        void resolvePorSubscriptionId() {
            Assinatura assinatura = seedAssinatura(seedAssessoria(), StatusAssinatura.ATIVA, null, "sub_abc");
            flushClear();

            Optional<Assinatura> resultado = assinaturaRepository.findByAsaasSubscriptionId("sub_abc");

            assertThat(resultado).isPresent();
            assertThat(resultado.get().getId()).isEqualTo(assinatura.getId());
        }

        @Test
        @DisplayName("PENDENTE sem asaasSubscriptionId (null) não colide e não é encontrada")
        void pendenteNulaNaoColide() {
            seedAssinatura(seedAssessoria(), StatusAssinatura.PENDENTE, null, null);
            seedAssinatura(seedAssessoria(), StatusAssinatura.PENDENTE, null, null);
            flushClear();

            Optional<Assinatura> resultado = assinaturaRepository.findByAsaasSubscriptionId("sub_inexistente");

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByStatusAndOverdueDesdeBefore")
    class FindByStatusAndOverdueDesdeBefore {

        @Test
        @DisplayName("retorna INADIMPLENTE com overdueDesde antes do corte; exclui recente e outros status")
        void filtraInadimplenteAntesDoCorte() {
            Instant agora = Instant.now();
            Instant corte = agora.minus(5, ChronoUnit.DAYS);

            Assinatura vencidaAntiga = seedAssinatura(seedAssessoria(), StatusAssinatura.INADIMPLENTE,
                    agora.minus(6, ChronoUnit.DAYS), "sub_venc_antiga");
            seedAssinatura(seedAssessoria(), StatusAssinatura.INADIMPLENTE,
                    agora.minus(4, ChronoUnit.DAYS), "sub_venc_recente");
            seedAssinatura(seedAssessoria(), StatusAssinatura.ATIVA,
                    agora.minus(10, ChronoUnit.DAYS), "sub_ativa");
            flushClear();

            List<Assinatura> resultado = assinaturaRepository
                    .findByStatusAndOverdueDesdeBefore(StatusAssinatura.INADIMPLENTE, corte);

            assertThat(resultado).extracting(Assinatura::getId).containsExactly(vencidaAntiga.getId());
        }
    }

    private void flushClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Assessoria seedAssessoria() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Assinatura Test");
        assessoria.setDominio("assinatura-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(assessoria);
    }

    private Assinatura seedAssinatura(Assessoria assessoria, StatusAssinatura status,
                                      Instant overdueDesde, String asaasSubscriptionId) {
        Assinatura assinatura = new Assinatura();
        assinatura.setAssessoriaId(assessoria.getId());
        assinatura.setStatus(status);
        assinatura.setOverdueDesde(overdueDesde);
        assinatura.setAsaasSubscriptionId(asaasSubscriptionId);
        assinatura.setValor(new BigDecimal("149.90"));
        return assinaturaRepository.save(assinatura);
    }
}
