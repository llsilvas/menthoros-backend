package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.AthleteContract;
import br.com.menthoros.backend.entity.AthleteInvoice;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.ContractPeriodicity;
import br.com.menthoros.backend.enums.InvoiceStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.repository.projection.AthleteOpenInvoiceView;
import br.com.menthoros.backend.repository.projection.ContractLastDueDateView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolamento por tenant de {@link AthleteContractRepository} e {@link AthleteInvoiceRepository}
 * contra o schema real (task 1.3): o filtro de tenant é manual, então cada método é testado
 * com o tenant certo e com outro tenant.
 */
@Transactional
class AthleteContractRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private AthleteContractRepository contractRepository;
    @Autowired
    private AthleteInvoiceRepository invoiceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("AthleteContractRepository")
    class Contratos {

        @Test
        @DisplayName("findActiveByAthleteIdAndTenantId ignora encerrado e outro tenant")
        void ativoPorAtletaETenant() {
            Atleta atleta = seedAtleta();
            UUID tenant = atleta.getAssessoria().getId();
            AthleteContract encerrado = salvarContrato(atleta, tenant);
            encerrado.setEndedAt(OffsetDateTime.now());
            contractRepository.saveAndFlush(encerrado); // o flush ordena INSERT antes de UPDATE: encerrar precisa chegar ao banco antes do novo ativo
            AthleteContract ativo = salvarContrato(atleta, tenant);
            flushClear();

            assertThat(contractRepository.findActiveByAthleteIdAndTenantId(atleta.getId(), tenant))
                    .map(AthleteContract::getId).contains(ativo.getId());
            assertThat(contractRepository.findActiveByAthleteIdAndTenantId(atleta.getId(), UUID.randomUUID()))
                    .isEmpty();
        }

        @Test
        @DisplayName("findByIdAndTenantIdForUpdate devolve a linha travada no tenant certo e nada em outro")
        void lockPorIdETenant() {
            Atleta atleta = seedAtleta();
            UUID tenant = atleta.getAssessoria().getId();
            AthleteContract contrato = salvarContrato(atleta, tenant);
            flushClear();

            assertThat(contractRepository.findByIdAndTenantIdForUpdate(contrato.getId(), tenant))
                    .map(AthleteContract::getId).contains(contrato.getId());
            assertThat(contractRepository.findByIdAndTenantIdForUpdate(contrato.getId(), UUID.randomUUID()))
                    .isEmpty();
        }

        @Test
        @DisplayName("findTenantIdsWithActiveContract lista cada tenant uma vez e ignora só encerrados")
        void tenantsComContratoAtivo() {
            Atleta a1 = seedAtleta();
            Atleta a2 = seedAtleta();
            Atleta a3 = seedAtleta();
            UUID t1 = a1.getAssessoria().getId();
            UUID t2 = a2.getAssessoria().getId();
            UUID t3 = a3.getAssessoria().getId();
            salvarContrato(a1, t1);
            salvarContrato(a2, t2);
            AthleteContract encerrado = salvarContrato(a3, t3);
            encerrado.setEndedAt(OffsetDateTime.now());
            contractRepository.saveAndFlush(encerrado); // o flush ordena INSERT antes de UPDATE: encerrar precisa chegar ao banco antes do novo ativo
            flushClear();

            List<UUID> tenants = contractRepository.findTenantIdsWithActiveContract();

            assertThat(tenants).contains(t1, t2).doesNotContain(t3);
            assertThat(tenants).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("findActiveByTenantId e findActiveByTenantIdAndAthleteIdIn são escopados ao tenant")
        void ativosPorTenant() {
            Atleta a1 = seedAtleta();
            UUID t1 = a1.getAssessoria().getId();
            Atleta a2 = seedAtleta();
            UUID t2 = a2.getAssessoria().getId();
            AthleteContract c1 = salvarContrato(a1, t1);
            salvarContrato(a2, t2);
            flushClear();

            assertThat(contractRepository.findActiveByTenantId(t1))
                    .extracting(AthleteContract::getId).containsExactly(c1.getId());
            assertThat(contractRepository.findActiveByTenantIdAndAthleteIdIn(t1, List.of(a1.getId(), a2.getId())))
                    .extracting(AthleteContract::getId).containsExactly(c1.getId());
            assertThat(contractRepository.findActiveByTenantIdAndAthleteIdIn(t2, List.of(a1.getId())))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("AthleteInvoiceRepository")
    class Mensalidades {

        @Test
        @DisplayName("findByIdAndTenantId, por contrato e última — invisíveis a outro tenant")
        void lookupsEscopados() {
            Atleta atleta = seedAtleta();
            UUID tenant = atleta.getAssessoria().getId();
            AthleteContract contrato = salvarContrato(atleta, tenant);
            AthleteInvoice out = salvarMensalidade(contrato, LocalDate.of(2026, 10, 10), InvoiceStatus.OPEN);
            AthleteInvoice nov = salvarMensalidade(contrato, LocalDate.of(2026, 11, 10), InvoiceStatus.OPEN);
            flushClear();
            UUID outro = UUID.randomUUID();

            assertThat(invoiceRepository.findByIdAndTenantId(out.getId(), tenant)).isPresent();
            assertThat(invoiceRepository.findByIdAndTenantId(out.getId(), outro)).isEmpty();

            assertThat(invoiceRepository.findByContractIdAndTenantIdOrderByDueDateDesc(contrato.getId(), tenant))
                    .extracting(AthleteInvoice::getId).containsExactly(nov.getId(), out.getId());
            assertThat(invoiceRepository.findByContractIdAndTenantIdOrderByDueDateDesc(contrato.getId(), outro))
                    .isEmpty();

            assertThat(invoiceRepository.findTopByContractIdAndTenantIdOrderByDueDateDesc(contrato.getId(), tenant))
                    .map(AthleteInvoice::getId).contains(nov.getId());
            assertThat(invoiceRepository.findTopByContractIdAndTenantIdOrderByDueDateDesc(contrato.getId(), outro))
                    .isEmpty();
        }

        @Test
        @DisplayName("findOpenByTenantIdAndAthleteIdIn traz só OPEN, inclusive de contrato encerrado, no tenant certo")
        void abertasPorAtleta() {
            Atleta atleta = seedAtleta();
            UUID tenant = atleta.getAssessoria().getId();
            AthleteContract encerrado = salvarContrato(atleta, tenant);
            salvarMensalidade(encerrado, LocalDate.of(2026, 8, 10), InvoiceStatus.OPEN);
            encerrado.setEndedAt(OffsetDateTime.now());
            contractRepository.saveAndFlush(encerrado); // o flush ordena INSERT antes de UPDATE: encerrar precisa chegar ao banco antes do novo ativo
            AthleteContract ativo = salvarContrato(atleta, tenant);
            salvarMensalidade(ativo, LocalDate.of(2026, 10, 10), InvoiceStatus.OPEN);
            salvarMensalidade(ativo, LocalDate.of(2026, 9, 10), InvoiceStatus.PAID);
            salvarMensalidade(ativo, LocalDate.of(2026, 7, 10), InvoiceStatus.CANCELLED);
            flushClear();

            List<AthleteOpenInvoiceView> abertas =
                    invoiceRepository.findOpenByTenantIdAndAthleteIdIn(tenant, List.of(atleta.getId()));

            assertThat(abertas).extracting(AthleteOpenInvoiceView::getDueDate)
                    .containsExactlyInAnyOrder(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 10, 10));
            assertThat(abertas).extracting(AthleteOpenInvoiceView::getAthleteId).containsOnly(atleta.getId());
            assertThat(invoiceRepository.findOpenByTenantIdAndAthleteIdIn(UUID.randomUUID(), List.of(atleta.getId())))
                    .isEmpty();
        }

        @Test
        @DisplayName("findLastDueDateByContract devolve o maior vencimento por contrato, escopado ao tenant")
        void ultimoVencimentoPorContrato() {
            Atleta atleta = seedAtleta();
            UUID tenant = atleta.getAssessoria().getId();
            AthleteContract contrato = salvarContrato(atleta, tenant);
            salvarMensalidade(contrato, LocalDate.of(2026, 10, 10), InvoiceStatus.PAID);
            salvarMensalidade(contrato, LocalDate.of(2026, 11, 10), InvoiceStatus.PAID);
            flushClear();

            List<ContractLastDueDateView> ultimos =
                    invoiceRepository.findLastDueDateByContract(tenant, List.of(contrato.getId()));

            assertThat(ultimos).singleElement().satisfies(v -> {
                assertThat(v.getContractId()).isEqualTo(contrato.getId());
                assertThat(v.getLastDueDate()).isEqualTo(LocalDate.of(2026, 11, 10));
            });
            assertThat(invoiceRepository.findLastDueDateByContract(UUID.randomUUID(), List.of(contrato.getId())))
                    .isEmpty();
        }
    }

    // ---- helpers ----

    private void flushClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Contrato Test");
        assessoria.setDominio("contrato-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Contrato");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private AthleteContract salvarContrato(Atleta atleta, UUID tenant) {
        return contractRepository.save(AthleteContract.builder()
                .tenantId(tenant)
                .athleteId(atleta.getId())
                .periodicity(ContractPeriodicity.MONTHLY)
                .amount(new BigDecimal("200.00"))
                .dueDay(10)
                .startDate(LocalDate.of(2026, 9, 21))
                .build());
    }

    private AthleteInvoice salvarMensalidade(AthleteContract contrato, LocalDate dueDate, InvoiceStatus status) {
        return invoiceRepository.save(AthleteInvoice.builder()
                .tenantId(contrato.getTenantId())
                .contractId(contrato.getId())
                .dueDate(dueDate)
                .amount(contrato.getAmount())
                .status(status)
                .build());
    }
}
