package br.com.menthoros.backend.services.impl;

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
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AthleteContractRepository;
import br.com.menthoros.backend.repository.AthleteInvoiceRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.services.AthleteContractService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corrida real no banco (CA4 sob concorrência, CA16): N threads chamam a geração ao mesmo tempo
 * sobre o mesmo contrato com a última mensalidade vencida. O lock pessimista serializa: a
 * primeira gera, as demais releem e não geram. Sem o lock, várias gerariam e o {@code UNIQUE}
 * derrubaria as perdedoras com exceção — aqui nenhuma thread pode falhar.
 *
 * <p>Sem {@code @Transactional} de propósito: cada thread precisa da sua transação.</p>
 */
class AthleteContractConcurrencyIT extends AbstractIntegrationTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 21);
    private static final int THREADS = 6;

    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private AthleteContractRepository contractRepository;
    @Autowired private AthleteInvoiceRepository invoiceRepository;
    @Autowired private AthleteContractService service;

    @Test
    @DisplayName("geração concorrente sobre o mesmo contrato produz exatamente uma mensalidade nova, sem falhas")
    void geracaoConcorrenteSerializada() throws Exception {
        UUID tenant = seedTenant();
        AthleteContract contract = contractRepository.save(AthleteContract.builder()
                .tenantId(tenant).athleteId(seedAtleta(tenant)).periodicity(ContractPeriodicity.MONTHLY)
                .amount(new BigDecimal("200.00")).dueDay(10).startDate(LocalDate.of(2026, 1, 10)).build());
        invoiceRepository.save(AthleteInvoice.builder()
                .tenantId(tenant).contractId(contract.getId()).dueDate(LocalDate.of(2026, 9, 10))
                .amount(contract.getAmount()).status(InvoiceStatus.PAID).build());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                results.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        TenantContext.setTenantId(tenant);
                        return service.ensureNextInvoice(contract.getId(), tenant, HOJE);
                    } finally {
                        TenantContext.clear();
                    }
                }));
            }
            start.countDown();
            int total = 0;
            for (Future<Integer> f : results) {
                total += f.get(30, TimeUnit.SECONDS); // uma exceção aqui é falha do teste
            }
            assertThat(total).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        List<AthleteInvoice> invoices = invoiceRepository.findByContractIdAndTenantIdOrderByDueDateDesc(contract.getId(), tenant);
        assertThat(invoices).hasSize(2);
        assertThat(invoices.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(invoices.get(0).getStatus()).isEqualTo(InvoiceStatus.OPEN);
    }

    @Test
    @DisplayName("encerrar durante a geração: a mensalidade nasce antes do encerramento ou não nasce (CA16)")
    void encerrarDuranteGeracao() throws Exception {
        UUID tenant = seedTenant();
        UUID athleteId = seedAtleta(tenant);
        AthleteContract contract = contractRepository.save(AthleteContract.builder()
                .tenantId(tenant).athleteId(athleteId).periodicity(ContractPeriodicity.MONTHLY)
                .amount(new BigDecimal("200.00")).dueDay(10).startDate(LocalDate.of(2026, 1, 10)).build());
        invoiceRepository.save(AthleteInvoice.builder()
                .tenantId(tenant).contractId(contract.getId()).dueDate(LocalDate.of(2026, 9, 10))
                .amount(contract.getAmount()).status(InvoiceStatus.PAID).build());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> gerar = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try {
                    TenantContext.setTenantId(tenant);
                    return service.ensureNextInvoice(contract.getId(), tenant, HOJE);
                } finally {
                    TenantContext.clear();
                }
            });
            Future<AthleteContract> encerrar = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try {
                    TenantContext.setTenantId(tenant);
                    return service.end(athleteId);
                } finally {
                    TenantContext.clear();
                }
            });
            start.countDown();
            int geradas = gerar.get(30, TimeUnit.SECONDS);
            AthleteContract encerrado = encerrar.get(30, TimeUnit.SECONDS);

            assertThat(encerrado.isActive()).isFalse();
            List<AthleteInvoice> invoices = invoiceRepository.findByContractIdAndTenantIdOrderByDueDateDesc(contract.getId(), tenant);
            // ou gerou uma (venceu o lock) ou zero (releu já encerrado) — nunca outro estado
            assertThat(geradas).isBetween(0, 1);
            assertThat(invoices).hasSize(1 + geradas);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("baixa e cancelamento simultâneos da mesma mensalidade: exatamente um vence, o outro recebe conflito (D6)")
    void baixaECancelarConcorrentes() throws Exception {
        UUID tenant = seedTenant();
        AthleteContract contract = contractRepository.save(AthleteContract.builder()
                .tenantId(tenant).athleteId(seedAtleta(tenant)).periodicity(ContractPeriodicity.MONTHLY)
                .amount(new BigDecimal("200.00")).dueDay(10).startDate(LocalDate.of(2026, 1, 10)).build());
        AthleteInvoice invoice = invoiceRepository.save(AthleteInvoice.builder()
                .tenantId(tenant).contractId(contract.getId()).dueDate(LocalDate.of(2026, 10, 10))
                .amount(contract.getAmount()).status(InvoiceStatus.OPEN).build());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> baixa = pool.submit(() -> executar(start, tenant, () -> service.markPaid(invoice.getId(), null, null)));
            Future<Boolean> cancelar = pool.submit(() -> executar(start, tenant, () -> service.cancel(invoice.getId())));
            start.countDown();
            boolean baixaOk = baixa.get(30, TimeUnit.SECONDS);
            boolean cancelarOk = cancelar.get(30, TimeUnit.SECONDS);

            assertThat(baixaOk ^ cancelarOk).as("exatamente uma transição vence").isTrue();
            AthleteInvoice fim = invoiceRepository.findByIdAndTenantId(invoice.getId(), tenant).orElseThrow();
            assertThat(fim.getStatus()).isEqualTo(baixaOk ? InvoiceStatus.PAID : InvoiceStatus.CANCELLED);
        } finally {
            pool.shutdownNow();
        }
    }

    /** true se a transição foi gravada; false se perdeu a corrida (409 por conflito de domínio ou de versão). */
    private static boolean executar(CountDownLatch start, UUID tenant, Runnable acao) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        try {
            TenantContext.setTenantId(tenant);
            acao.run();
            return true;
        } catch (br.com.menthoros.backend.exception.DomainConflictException
                 | org.springframework.dao.OptimisticLockingFailureException e) {
            return false;
        } finally {
            TenantContext.clear();
        }
    }

    private UUID seedTenant() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Concorrencia");
        assessoria.setDominio("concorrencia-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(assessoria).getId();
    }

    private UUID seedAtleta(UUID tenantId) {
        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Concorrencia");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoriaRepository.getReferenceById(tenantId));
        return atletaRepository.save(atleta).getId();
    }
}
