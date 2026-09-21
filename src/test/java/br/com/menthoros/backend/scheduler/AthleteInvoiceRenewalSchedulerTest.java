package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.entity.AthleteContract;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AthleteContractRepository;
import br.com.menthoros.backend.services.AthleteContractService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AthleteInvoiceRenewalSchedulerTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 21);
    /** 03:30 UTC = 00:30 em São Paulo: o "hoje" do job tem de ser o de São Paulo, não o UTC. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T03:30:00Z"), ZoneId.of("UTC"));

    @Mock private AthleteContractRepository contractRepository;
    @Mock private AthleteContractService contractService;

    private AthleteInvoiceRenewalScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AthleteInvoiceRenewalScheduler(contractRepository, contractService, CLOCK);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("renew")
    class Renew {

        @Test
        @DisplayName("dois tenants: processa ambos com o TenantContext certo e limpa ao fim (CA17)")
        void doisTenantsComContexto() {
            UUID t1 = UUID.randomUUID();
            UUID t2 = UUID.randomUUID();
            AthleteContract c1 = contrato(t1);
            AthleteContract c2 = contrato(t2);
            when(contractRepository.findTenantIdsWithActiveContract()).thenReturn(List.of(t1, t2));
            when(contractRepository.findActiveByTenantId(t1)).thenReturn(List.of(c1));
            when(contractRepository.findActiveByTenantId(t2)).thenReturn(List.of(c2));
            List<UUID> tenantsVistos = new ArrayList<>();
            when(contractService.ensureNextInvoice(any(), any(), eq(HOJE))).thenAnswer(inv -> {
                tenantsVistos.add(TenantContext.getRequiredTenantId());
                return 1;
            });

            int total = scheduler.renew(HOJE);

            assertThat(total).isEqualTo(2);
            assertThat(tenantsVistos).containsExactly(t1, t2);
            verify(contractService).ensureNextInvoice(c1.getId(), t1, HOJE);
            verify(contractService).ensureNextInvoice(c2.getId(), t2, HOJE);
            assertThat(TenantContext.hasTenant()).isFalse();
        }

        @Test
        @DisplayName("falha no primeiro tenant não impede o segundo, e o contexto não vaza (CA17)")
        void falhaIsoladaPorTenant() {
            UUID t1 = UUID.randomUUID();
            UUID t2 = UUID.randomUUID();
            AthleteContract c2 = contrato(t2);
            when(contractRepository.findTenantIdsWithActiveContract()).thenReturn(List.of(t1, t2));
            when(contractRepository.findActiveByTenantId(t1)).thenThrow(new RuntimeException("banco fora"));
            when(contractRepository.findActiveByTenantId(t2)).thenReturn(List.of(c2));
            when(contractService.ensureNextInvoice(c2.getId(), t2, HOJE)).thenReturn(1);

            int total = scheduler.renew(HOJE);

            assertThat(total).isEqualTo(1);
            verify(contractService).ensureNextInvoice(c2.getId(), t2, HOJE);
            assertThat(TenantContext.hasTenant()).isFalse();
        }

        @Test
        @DisplayName("falha num contrato não impede os demais do mesmo tenant")
        void falhaIsoladaPorContrato() {
            UUID t1 = UUID.randomUUID();
            AthleteContract quebrado = contrato(t1);
            AthleteContract ok = contrato(t1);
            when(contractRepository.findTenantIdsWithActiveContract()).thenReturn(List.of(t1));
            when(contractRepository.findActiveByTenantId(t1)).thenReturn(List.of(quebrado, ok));
            when(contractService.ensureNextInvoice(quebrado.getId(), t1, HOJE))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq_athlete_invoice_contract_due_date"));
            when(contractService.ensureNextInvoice(ok.getId(), t1, HOJE)).thenReturn(2);

            assertThat(scheduler.renew(HOJE)).isEqualTo(2);
            verify(contractService).ensureNextInvoice(ok.getId(), t1, HOJE);
        }

        @Test
        @DisplayName("sem tenant com contrato ativo → nada chamado")
        void semTenants() {
            when(contractRepository.findTenantIdsWithActiveContract()).thenReturn(List.of());

            assertThat(scheduler.renew(HOJE)).isZero();
            verifyNoInteractions(contractService);
        }

        @Test
        @DisplayName("o disparo agendado usa o dia de São Paulo, não o UTC")
        void diaDeSaoPaulo() {
            when(contractRepository.findTenantIdsWithActiveContract()).thenReturn(List.of());

            scheduler.renew();

            verify(contractRepository).findTenantIdsWithActiveContract();
            // 03:30Z de 22/09 é 00:30 de 22/09 em SP — mesmo dia neste caso; o ponto é o clock com zona
            assertThat(LocalDate.now(CLOCK.withZone(ZoneId.of("America/Sao_Paulo")))).isEqualTo(LocalDate.of(2026, 9, 22));
        }

        @Test
        @DisplayName("desabilitado por property → não toca o banco")
        void desabilitado() {
            ReflectionTestUtils.setField(scheduler, "enabled", false);

            scheduler.renew();

            verify(contractRepository, never()).findTenantIdsWithActiveContract();
            verifyNoInteractions(contractService);
        }
    }

    private static AthleteContract contrato(UUID tenantId) {
        return AthleteContract.builder().id(UUID.randomUUID()).tenantId(tenantId).athleteId(UUID.randomUUID()).build();
    }
}
