package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.billing.AthleteBilling;
import br.com.menthoros.backend.dto.input.AthleteContractInputDto;
import br.com.menthoros.backend.entity.AthleteContract;
import br.com.menthoros.backend.entity.AthleteInvoice;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.AthleteBillingStatus;
import br.com.menthoros.backend.enums.ContractPeriodicity;
import br.com.menthoros.backend.enums.InvoiceStatus;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AthleteContractRepository;
import br.com.menthoros.backend.repository.AthleteInvoiceRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.projection.AthleteOpenInvoiceView;
import br.com.menthoros.backend.repository.projection.ContractLastDueDateView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AthleteContractServiceImplTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 21);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-21T12:00:00Z"), ZoneId.of("UTC"));

    @Mock private AthleteContractRepository contractRepository;
    @Mock private AthleteInvoiceRepository invoiceRepository;
    @Mock private AtletaRepository atletaRepository;
    @Mock private CacheManager cacheManager;
    @Mock private Cache cacheAtletas;
    @Mock private Cache cacheAtletasList;

    private AthleteContractServiceImpl service;

    private UUID tenantId;
    private UUID athleteId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        athleteId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        service = new AthleteContractServiceImpl(contractRepository, invoiceRepository, atletaRepository, cacheManager, CLOCK);
        // caches presentes por padrão; os testes de mutação verificam a invalidação
        lenient().when(cacheManager.getCache("atletas")).thenReturn(cacheAtletas);
        lenient().when(cacheManager.getCache("atletas-list")).thenReturn(cacheAtletasList);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("createOrUpdate")
    class CreateOrUpdate {

        @Test
        @DisplayName("cria o contrato e a primeira mensalidade no primeiro dia 10 ≥ início (CA2)")
        void criaComPrimeiraMensalidade() {
            when(atletaRepository.findByIdAndTenantId(athleteId, tenantId)).thenReturn(Optional.of(new Atleta()));
            when(contractRepository.findActiveByAthleteIdAndTenantId(athleteId, tenantId)).thenReturn(Optional.empty());
            AthleteContract persisted = contrato(UUID.randomUUID(), HOJE);
            when(contractRepository.saveAndFlush(any())).thenReturn(persisted);
            when(contractRepository.findByIdAndTenantIdForUpdate(persisted.getId(), tenantId)).thenReturn(Optional.of(persisted));
            when(invoiceRepository.findTopByContractIdAndTenantIdOrderByDueDateDesc(persisted.getId(), tenantId))
                    .thenReturn(Optional.empty());

            AthleteContract result = service.createOrUpdate(athleteId, input(new BigDecimal("200.00"), 10, HOJE));

            assertThat(result).isSameAs(persisted);
            ArgumentCaptor<AthleteContract> contratoCaptor = ArgumentCaptor.forClass(AthleteContract.class);
            verify(contractRepository).saveAndFlush(contratoCaptor.capture());
            assertThat(contratoCaptor.getValue().getTenantId()).isEqualTo(tenantId);
            assertThat(contratoCaptor.getValue().getAthleteId()).isEqualTo(athleteId);
            assertThat(contratoCaptor.getValue().isAthleteNoticeEnabled()).isTrue();

            ArgumentCaptor<AthleteInvoice> invoiceCaptor = ArgumentCaptor.forClass(AthleteInvoice.class);
            verify(invoiceRepository).save(invoiceCaptor.capture());
            AthleteInvoice invoice = invoiceCaptor.getValue();
            assertThat(invoice.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 10));
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
            assertThat(invoice.getAmount()).isEqualByComparingTo("200.00");
            assertThat(invoice.getTenantId()).isEqualTo(tenantId);
            assertThat(invoice.getContractId()).isEqualTo(persisted.getId());
        }

        @Test
        @DisplayName("edita o contrato existente sob lock e não toca mensalidades (CA9)")
        void editaSemTocarMensalidades() {
            when(atletaRepository.findByIdAndTenantId(athleteId, tenantId)).thenReturn(Optional.of(new Atleta()));
            AthleteContract existing = contrato(UUID.randomUUID(), HOJE.minusMonths(2));
            when(contractRepository.findActiveByAthleteIdAndTenantId(athleteId, tenantId)).thenReturn(Optional.of(existing));
            when(contractRepository.findByIdAndTenantIdForUpdate(existing.getId(), tenantId)).thenReturn(Optional.of(existing));
            when(contractRepository.save(existing)).thenReturn(existing);

            AthleteContract result = service.createOrUpdate(athleteId, input(new BigDecimal("250.00"), 15, existing.getStartDate()));

            assertThat(result.getAmount()).isEqualByComparingTo("250.00");
            assertThat(result.getDueDay()).isEqualTo(15);
            verify(contractRepository, never()).saveAndFlush(any());
            verifyNoInteractions(invoiceRepository);
            // o badge do GET de atleta vive em cache: toda mutação invalida as duas chaves
            verify(cacheAtletas).evict(athleteId + "_" + tenantId);
            verify(cacheAtletasList).evict(tenantId);
        }

        @Test
        @DisplayName("encerramento venceu a corrida entre a leitura e o lock → não edita o morto, cria um novo")
        void encerradoEntreLeituraELock() {
            when(atletaRepository.findByIdAndTenantId(athleteId, tenantId)).thenReturn(Optional.of(new Atleta()));
            AthleteContract lido = contrato(UUID.randomUUID(), HOJE);
            AthleteContract travado = contrato(lido.getId(), HOJE);
            travado.setEndedAt(OffsetDateTime.now());
            when(contractRepository.findActiveByAthleteIdAndTenantId(athleteId, tenantId)).thenReturn(Optional.of(lido));
            when(contractRepository.findByIdAndTenantIdForUpdate(lido.getId(), tenantId)).thenReturn(Optional.of(travado));
            AthleteContract novo = contrato(UUID.randomUUID(), HOJE);
            when(contractRepository.saveAndFlush(any())).thenReturn(novo);
            when(contractRepository.findByIdAndTenantIdForUpdate(novo.getId(), tenantId)).thenReturn(Optional.of(novo));
            when(invoiceRepository.findTopByContractIdAndTenantIdOrderByDueDateDesc(novo.getId(), tenantId))
                    .thenReturn(Optional.empty());

            AthleteContract result = service.createOrUpdate(athleteId, input(BigDecimal.TEN, 10, HOJE));

            assertThat(result).isSameAs(novo);
            verify(contractRepository, never()).save(travado);
            assertThat(travado.getAmount()).isEqualByComparingTo("200.00"); // intocado
        }

        @Test
        @DisplayName("athleteNoticeEnabled=false é persistido; nulo vira true")
        void avisoAoAtleta() {
            when(atletaRepository.findByIdAndTenantId(athleteId, tenantId)).thenReturn(Optional.of(new Atleta()));
            AthleteContract existing = contrato(UUID.randomUUID(), HOJE);
            when(contractRepository.findActiveByAthleteIdAndTenantId(athleteId, tenantId)).thenReturn(Optional.of(existing));
            when(contractRepository.findByIdAndTenantIdForUpdate(existing.getId(), tenantId)).thenReturn(Optional.of(existing));
            when(contractRepository.save(existing)).thenReturn(existing);

            service.createOrUpdate(athleteId, new AthleteContractInputDto(
                    ContractPeriodicity.MONTHLY, BigDecimal.TEN, 10, HOJE, false));
            assertThat(existing.isAthleteNoticeEnabled()).isFalse();

            service.createOrUpdate(athleteId, new AthleteContractInputDto(
                    ContractPeriodicity.MONTHLY, BigDecimal.TEN, 10, HOJE, null));
            assertThat(existing.isAthleteNoticeEnabled()).isTrue();
        }

        @Test
        @DisplayName("atleta inexistente ou de outro tenant → DomainNotFoundException, nada persistido")
        void atletaNaoEncontrado() {
            when(atletaRepository.findByIdAndTenantId(athleteId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createOrUpdate(athleteId, input(BigDecimal.TEN, 10, HOJE)))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(contractRepository, invoiceRepository);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("br.com.menthoros.backend.services.impl.AthleteContractServiceImplTest#inputsInvalidos")
        @DisplayName("defesa em profundidade: input inválido → IllegalArgumentException antes de qualquer acesso")
        void inputInvalido(String caso, AthleteContractInputDto input) {
            assertThatThrownBy(() -> service.createOrUpdate(athleteId, input))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(atletaRepository, contractRepository, invoiceRepository);
        }
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> inputsInvalidos() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("nulo", null),
                org.junit.jupiter.params.provider.Arguments.of("sem periodicidade",
                        new AthleteContractInputDto(null, BigDecimal.TEN, 10, HOJE, true)),
                org.junit.jupiter.params.provider.Arguments.of("dia 0",
                        new AthleteContractInputDto(ContractPeriodicity.MONTHLY, BigDecimal.TEN, 0, HOJE, true)),
                org.junit.jupiter.params.provider.Arguments.of("dia 32",
                        new AthleteContractInputDto(ContractPeriodicity.MONTHLY, BigDecimal.TEN, 32, HOJE, true)),
                org.junit.jupiter.params.provider.Arguments.of("dia nulo",
                        new AthleteContractInputDto(ContractPeriodicity.MONTHLY, BigDecimal.TEN, null, HOJE, true)),
                org.junit.jupiter.params.provider.Arguments.of("sem início",
                        new AthleteContractInputDto(ContractPeriodicity.MONTHLY, BigDecimal.TEN, 10, null, true)),
                org.junit.jupiter.params.provider.Arguments.of("valor negativo",
                        new AthleteContractInputDto(ContractPeriodicity.MONTHLY, new BigDecimal("-1"), 10, HOJE, true))
        );
    }

    @Nested
    @DisplayName("end")
    class End {

        @Test
        @DisplayName("seta endedAt sob lock e não toca mensalidades (CA10)")
        void encerra() {
            AthleteContract active = contrato(UUID.randomUUID(), HOJE);
            when(contractRepository.findActiveByAthleteIdAndTenantId(athleteId, tenantId)).thenReturn(Optional.of(active));
            when(contractRepository.findByIdAndTenantIdForUpdate(active.getId(), tenantId)).thenReturn(Optional.of(active));
            when(contractRepository.save(active)).thenReturn(active);

            AthleteContract result = service.end(athleteId);

            assertThat(result.getEndedAt()).isEqualTo(OffsetDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
            assertThat(result.isActive()).isFalse();
            verifyNoInteractions(invoiceRepository);
        }

        @Test
        @DisplayName("sem contrato ativo → DomainNotFoundException")
        void semContratoAtivo() {
            when(contractRepository.findActiveByAthleteIdAndTenantId(athleteId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.end(athleteId)).isInstanceOf(DomainNotFoundException.class);

            verify(contractRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("ensureNextInvoice")
    class EnsureNextInvoice {

        private AthleteContract contract;

        @BeforeEach
        void lockContrato() {
            contract = contrato(UUID.randomUUID(), LocalDate.of(2026, 1, 10));
        }

        private void stubLock() {
            when(contractRepository.findByIdAndTenantIdForUpdate(contract.getId(), tenantId)).thenReturn(Optional.of(contract));
        }

        private void stubLast(LocalDate last) {
            when(invoiceRepository.findTopByContractIdAndTenantIdOrderByDueDateDesc(contract.getId(), tenantId))
                    .thenReturn(Optional.ofNullable(last).map(d -> AthleteInvoice.builder().dueDate(d).build()));
        }

        private List<LocalDate> vencimentosSalvos() {
            ArgumentCaptor<AthleteInvoice> captor = ArgumentCaptor.forClass(AthleteInvoice.class);
            verify(invoiceRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
            return captor.getAllValues().stream().map(AthleteInvoice::getDueDate).toList();
        }

        @Test
        @DisplayName("última venceu ontem → gera exatamente uma, um período à frente (CA4)")
        void ultimaVenceuOntem() {
            stubLock();
            stubLast(HOJE.minusDays(1));

            int generated = service.ensureNextInvoice(contract.getId(), tenantId, HOJE);

            assertThat(generated).isEqualTo(1);
            assertThat(vencimentosSalvos()).containsExactly(LocalDate.of(2026, 10, 10));
        }

        @Test
        @DisplayName("última ainda não venceu → não gera nada (idempotente)")
        void ultimaNoFuturo() {
            stubLock();
            stubLast(HOJE.plusDays(5));

            assertThat(service.ensureNextInvoice(contract.getId(), tenantId, HOJE)).isZero();
            verify(invoiceRepository, never()).save(any());
        }

        @Test
        @DisplayName("última vence hoje → não gera (vencimento ≥ hoje satisfaz o invariante)")
        void ultimaVenceHoje() {
            stubLock();
            stubLast(HOJE);

            assertThat(service.ensureNextInvoice(contract.getId(), tenantId, HOJE)).isZero();
        }

        @Test
        @DisplayName("scheduler parado: gera as faltantes em sequência até uma com vencimento ≥ hoje (CA5)")
        void recuperaMesesPerdidos() {
            stubLock();
            stubLast(LocalDate.of(2026, 7, 10));

            int generated = service.ensureNextInvoice(contract.getId(), tenantId, HOJE);

            // as duas que venceram sem o job (08 e 09) mais a que satisfaz o invariante (10/10)
            assertThat(generated).isEqualTo(3);
            assertThat(vencimentosSalvos()).containsExactly(
                    LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 10));
        }

        @Test
        @DisplayName("sem mensalidade e início no futuro → primeira na data legada (CA13, data futura)")
        void semMensalidadeInicioFuturo() {
            contract.setStartDate(LocalDate.of(2026, 10, 5));
            contract.setDueDay(5);
            stubLock();
            stubLast(null);

            assertThat(service.ensureNextInvoice(contract.getId(), tenantId, HOJE)).isEqualTo(1);
            assertThat(vencimentosSalvos()).containsExactly(LocalDate.of(2026, 10, 5));
        }

        @Test
        @DisplayName("sem mensalidade e início no passado → primeira no próximo dia a partir de hoje, nunca dívida passada (CA13)")
        void semMensalidadeInicioPassado() {
            contract.setStartDate(LocalDate.of(2026, 3, 5));
            contract.setDueDay(5);
            stubLock();
            stubLast(null);

            assertThat(service.ensureNextInvoice(contract.getId(), tenantId, HOJE)).isEqualTo(1);
            assertThat(vencimentosSalvos()).containsExactly(LocalDate.of(2026, 10, 5));
        }

        @Test
        @DisplayName("contrato encerrado não gera, mesmo com a última vencida (CA10)")
        void encerradoNaoGera() {
            contract.setEndedAt(OffsetDateTime.now());
            stubLock();

            assertThat(service.ensureNextInvoice(contract.getId(), tenantId, HOJE)).isZero();
            verifyNoInteractions(invoiceRepository);
        }

        @Test
        @DisplayName("contrato de outro tenant → DomainNotFoundException")
        void outroTenant() {
            when(contractRepository.findByIdAndTenantIdForUpdate(contract.getId(), tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.ensureNextInvoice(contract.getId(), tenantId, HOJE))
                    .isInstanceOf(DomainNotFoundException.class);
            verifyNoInteractions(invoiceRepository);
        }

        @Test
        @DisplayName("atraso maior que o teto: gera 24, para com WARN; a execução seguinte completa")
        void tetoDeVinteQuatro() {
            stubLock();
            stubLast(HOJE.minusMonths(30)); // 30 períodos atrasados

            int primeira = service.ensureNextInvoice(contract.getId(), tenantId, HOJE);
            assertThat(primeira).isEqualTo(AthleteContractServiceImpl.MAX_INVOICES_PER_RUN);
            List<LocalDate> geradas = vencimentosSalvos();
            assertThat(geradas).hasSize(24);
            assertThat(geradas.get(23)).isBefore(HOJE);

            // segunda execução continua de onde parou: as 6 vencidas restantes + a que vence à frente
            stubLast(geradas.get(23));
            int segunda = service.ensureNextInvoice(contract.getId(), tenantId, HOJE);
            assertThat(segunda).isEqualTo(7);
            List<LocalDate> todas = vencimentosSalvos();
            assertThat(todas).hasSize(31);
            assertThat(todas.get(30)).isAfterOrEqualTo(HOJE);
        }

        @Test
        @DisplayName("hoje nulo é erro de programação")
        void hojeNulo() {
            assertThatThrownBy(() -> service.ensureNextInvoice(contract.getId(), tenantId, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("markPaid")
    class MarkPaid {

        @Test
        @DisplayName("sem valor nem data informados: PAID com valor da mensalidade e hoje (CA7)")
        void defaults() {
            AthleteInvoice invoice = mensalidade(InvoiceStatus.OPEN);
            when(invoiceRepository.findByIdAndTenantId(invoice.getId(), tenantId)).thenReturn(Optional.of(invoice));
            when(invoiceRepository.save(invoice)).thenReturn(invoice);

            AthleteInvoice result = service.markPaid(invoice.getId(), null, null);

            assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(result.getPaidAt()).isEqualTo(HOJE);
            assertThat(result.getPaidAmount()).isEqualByComparingTo("200.00");
        }

        @Test
        @DisplayName("baixa invalida o cache do atleta dono do contrato (badge não fica 30 min desatualizado)")
        void baixaInvalidaCache() {
            AthleteInvoice invoice = mensalidade(InvoiceStatus.OPEN);
            AthleteContract contract = contrato(invoice.getContractId(), HOJE);
            when(invoiceRepository.findByIdAndTenantId(invoice.getId(), tenantId)).thenReturn(Optional.of(invoice));
            when(invoiceRepository.save(invoice)).thenReturn(invoice);
            when(contractRepository.findById(invoice.getContractId())).thenReturn(Optional.of(contract));

            service.markPaid(invoice.getId(), null, null);

            verify(cacheAtletas).evict(athleteId + "_" + tenantId);
            verify(cacheAtletasList).evict(tenantId);
        }

        @Test
        @DisplayName("cache ausente (nome não configurado) não quebra a baixa")
        void cacheAusente() {
            AthleteInvoice invoice = mensalidade(InvoiceStatus.OPEN);
            when(invoiceRepository.findByIdAndTenantId(invoice.getId(), tenantId)).thenReturn(Optional.of(invoice));
            when(invoiceRepository.save(invoice)).thenReturn(invoice);
            when(contractRepository.findById(invoice.getContractId())).thenReturn(Optional.of(contrato(invoice.getContractId(), HOJE)));
            when(cacheManager.getCache("atletas")).thenReturn(null);
            when(cacheManager.getCache("atletas-list")).thenReturn(null);

            assertThat(service.markPaid(invoice.getId(), null, null).getStatus()).isEqualTo(InvoiceStatus.PAID);
        }

        @Test
        @DisplayName("valor e data informados prevalecem")
        void informados() {
            AthleteInvoice invoice = mensalidade(InvoiceStatus.OPEN);
            when(invoiceRepository.findByIdAndTenantId(invoice.getId(), tenantId)).thenReturn(Optional.of(invoice));
            when(invoiceRepository.save(invoice)).thenReturn(invoice);

            AthleteInvoice result = service.markPaid(invoice.getId(), HOJE.minusDays(3), new BigDecimal("180.00"));

            assertThat(result.getPaidAt()).isEqualTo(HOJE.minusDays(3));
            assertThat(result.getPaidAmount()).isEqualByComparingTo("180.00");
        }

        @Test
        @DisplayName("data de pagamento futura → IllegalArgumentException antes de buscar")
        void dataFutura() {
            assertThatThrownBy(() -> service.markPaid(UUID.randomUUID(), HOJE.plusDays(1), null))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(invoiceRepository);
        }

        @Test
        @DisplayName("valor pago negativo → IllegalArgumentException antes de buscar")
        void valorNegativo() {
            assertThatThrownBy(() -> service.markPaid(UUID.randomUUID(), null, new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(invoiceRepository);
        }

        @Test
        @DisplayName("mensalidade de outro tenant → DomainNotFoundException")
        void outroTenant() {
            UUID id = UUID.randomUUID();
            when(invoiceRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markPaid(id, null, null)).isInstanceOf(DomainNotFoundException.class);
            verify(invoiceRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("undoPayment")
    class UndoPayment {

        @Test
        @DisplayName("PAID volta a OPEN com data e valor nulos (CA7)")
        void desfaz() {
            AthleteInvoice invoice = mensalidade(InvoiceStatus.PAID);
            invoice.setPaidAt(HOJE);
            invoice.setPaidAmount(new BigDecimal("200.00"));
            when(invoiceRepository.findByIdAndTenantId(invoice.getId(), tenantId)).thenReturn(Optional.of(invoice));
            when(invoiceRepository.save(invoice)).thenReturn(invoice);

            AthleteInvoice result = service.undoPayment(invoice.getId());

            assertThat(result.getStatus()).isEqualTo(InvoiceStatus.OPEN);
            assertThat(result.getPaidAt()).isNull();
            assertThat(result.getPaidAmount()).isNull();
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel {

        @Test
        @DisplayName("OPEN vira CANCELLED")
        void cancela() {
            AthleteInvoice invoice = mensalidade(InvoiceStatus.OPEN);
            when(invoiceRepository.findByIdAndTenantId(invoice.getId(), tenantId)).thenReturn(Optional.of(invoice));
            when(invoiceRepository.save(invoice)).thenReturn(invoice);

            assertThat(service.cancel(invoice.getId()).getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        }

        @Test
        @DisplayName("mensalidade PAGA não cancela: 409 e nada muda (CA8)")
        void pagaNaoCancela() {
            AthleteInvoice invoice = mensalidade(InvoiceStatus.PAID);
            when(invoiceRepository.findByIdAndTenantId(invoice.getId(), tenantId)).thenReturn(Optional.of(invoice));

            assertThatThrownBy(() -> service.cancel(invoice.getId())).isInstanceOf(DomainConflictException.class);

            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
            verify(invoiceRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("tabela de transições (design D6)")
    class Transicoes {

        @ParameterizedTest(name = "{0} a partir de {1} → {2}")
        @CsvSource({
                "markPaid,    OPEN,      ok",
                "markPaid,    PAID,      409",
                "markPaid,    CANCELLED, 409",
                "undoPayment, OPEN,      409",
                "undoPayment, PAID,      ok",
                "undoPayment, CANCELLED, 409",
                "cancel,      OPEN,      ok",
                "cancel,      PAID,      409",
                "cancel,      CANCELLED, 409",
        })
        void transicao(String acao, InvoiceStatus de, String esperado) {
            AthleteInvoice invoice = mensalidade(de);
            when(invoiceRepository.findByIdAndTenantId(invoice.getId(), tenantId)).thenReturn(Optional.of(invoice));
            if ("ok".equals(esperado)) {
                when(invoiceRepository.save(invoice)).thenReturn(invoice);
            }

            Runnable act = switch (acao) {
                case "markPaid" -> () -> service.markPaid(invoice.getId(), null, null);
                case "undoPayment" -> () -> service.undoPayment(invoice.getId());
                default -> () -> service.cancel(invoice.getId());
            };

            if ("ok".equals(esperado)) {
                act.run();
                verify(invoiceRepository).save(invoice);
            } else {
                assertThatThrownBy(act::run).isInstanceOf(DomainConflictException.class);
                assertThat(invoice.getStatus()).isEqualTo(de);
                verify(invoiceRepository, never()).save(any());
            }
        }

        @ParameterizedTest
        @EnumSource(InvoiceStatus.class)
        @DisplayName("todo status tem exatamente as transições da tabela — adicionar um força decisão")
        void enumCoberto(InvoiceStatus status) {
            assertThat(InvoiceStatus.values()).contains(status);
        }
    }

    @Nested
    @DisplayName("resolveBilling")
    class ResolveBilling {

        @Test
        @DisplayName("lote vazio → mapa vazio sem tocar o banco")
        void loteVazio() {
            assertThat(service.resolveBilling(List.of(), HOJE)).isEmpty();
            verifyNoInteractions(invoiceRepository, contractRepository);
        }

        @Test
        @DisplayName("com mensalidade em aberto vencida: OVERDUE e próximo = a mais antiga em aberto (CA6)")
        void comAbertaVencida() {
            when(invoiceRepository.findOpenByTenantIdAndAthleteIdIn(eq(tenantId), anyCollection())).thenReturn(List.of(
                    aberta(athleteId, HOJE.minusDays(2)), aberta(athleteId, HOJE.plusDays(28))));

            Optional<AthleteBilling> billing = service.resolveBilling(athleteId, HOJE);

            assertThat(billing).contains(new AthleteBilling(AthleteBillingStatus.OVERDUE, HOJE.minusDays(2)));
            verifyNoInteractions(contractRepository);
        }

        @Test
        @DisplayName("sem contrato ativo nem mensalidade em aberto → ausente (CA1)")
        void semNada() {
            when(invoiceRepository.findOpenByTenantIdAndAthleteIdIn(eq(tenantId), anyCollection())).thenReturn(List.of());
            when(contractRepository.findActiveByTenantIdAndAthleteIdIn(eq(tenantId), anyCollection())).thenReturn(List.of());

            assertThat(service.resolveBilling(athleteId, HOJE)).isEmpty();
            verify(invoiceRepository, never()).findLastDueDateByContract(any(), anyCollection());
        }

        @Test
        @DisplayName("baixa antecipada: contrato ativo sem em aberto → UP_TO_DATE e próximo calculado da última (spec)")
        void baixaAntecipada() {
            AthleteContract contract = contrato(UUID.randomUUID(), HOJE.minusMonths(1));
            when(invoiceRepository.findOpenByTenantIdAndAthleteIdIn(eq(tenantId), anyCollection())).thenReturn(List.of());
            when(contractRepository.findActiveByTenantIdAndAthleteIdIn(eq(tenantId), anyCollection())).thenReturn(List.of(contract));
            when(invoiceRepository.findLastDueDateByContract(eq(tenantId), anyCollection()))
                    .thenReturn(List.of(ultima(contract.getId(), LocalDate.of(2026, 10, 10))));

            Optional<AthleteBilling> billing = service.resolveBilling(athleteId, LocalDate.of(2026, 9, 26));

            assertThat(billing).contains(new AthleteBilling(AthleteBillingStatus.UP_TO_DATE, LocalDate.of(2026, 11, 10)));
        }

        @Test
        @DisplayName("contrato migrado sem nenhuma mensalidade → UP_TO_DATE e próximo a partir de hoje")
        void contratoSemMensalidade() {
            AthleteContract contract = contrato(UUID.randomUUID(), LocalDate.of(2026, 3, 5));
            contract.setDueDay(5);
            when(invoiceRepository.findOpenByTenantIdAndAthleteIdIn(eq(tenantId), anyCollection())).thenReturn(List.of());
            when(contractRepository.findActiveByTenantIdAndAthleteIdIn(eq(tenantId), anyCollection())).thenReturn(List.of(contract));
            when(invoiceRepository.findLastDueDateByContract(eq(tenantId), anyCollection())).thenReturn(List.of());

            assertThat(service.resolveBilling(athleteId, HOJE))
                    .contains(new AthleteBilling(AthleteBillingStatus.UP_TO_DATE, LocalDate.of(2026, 10, 5)));
        }

        @Test
        @DisplayName("lote misto usa no máximo três queries e devolve só quem tem cobrança")
        void loteMisto() {
            UUID comAberta = UUID.randomUUID();
            UUID semAbertaComContrato = UUID.randomUUID();
            UUID semNada = UUID.randomUUID();
            AthleteContract contract = contrato(UUID.randomUUID(), HOJE.minusMonths(1));
            contract.setAthleteId(semAbertaComContrato);
            when(invoiceRepository.findOpenByTenantIdAndAthleteIdIn(eq(tenantId), anyCollection()))
                    .thenReturn(List.of(aberta(comAberta, HOJE.plusDays(3))));
            when(contractRepository.findActiveByTenantIdAndAthleteIdIn(eq(tenantId), anyCollection()))
                    .thenReturn(List.of(contract));
            when(invoiceRepository.findLastDueDateByContract(eq(tenantId), anyCollection()))
                    .thenReturn(List.of(ultima(contract.getId(), HOJE.minusDays(1))));

            Map<UUID, AthleteBilling> result = service.resolveBilling(List.of(comAberta, semAbertaComContrato, semNada), HOJE);

            assertThat(result).hasSize(2);
            assertThat(result.get(comAberta).status()).isEqualTo(AthleteBillingStatus.DUE_SOON);
            assertThat(result.get(semAbertaComContrato))
                    .isEqualTo(new AthleteBilling(AthleteBillingStatus.UP_TO_DATE, LocalDate.of(2026, 10, 10)));
            assertThat(result).doesNotContainKey(semNada);
            verify(invoiceRepository, times(1)).findOpenByTenantIdAndAthleteIdIn(any(), anyCollection());
            verify(contractRepository, times(1)).findActiveByTenantIdAndAthleteIdIn(any(), anyCollection());
            verify(invoiceRepository, times(1)).findLastDueDateByContract(any(), anyCollection());
            verifyNoMoreInteractions(invoiceRepository, contractRepository);
        }
    }

    // ---- helpers ----

    private AthleteContract contrato(UUID id, LocalDate start) {
        return AthleteContract.builder()
                .id(id)
                .tenantId(tenantId)
                .athleteId(athleteId)
                .periodicity(ContractPeriodicity.MONTHLY)
                .amount(new BigDecimal("200.00"))
                .dueDay(10)
                .startDate(start)
                .build();
    }

    private AthleteInvoice mensalidade(InvoiceStatus status) {
        return AthleteInvoice.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .contractId(UUID.randomUUID())
                .dueDate(HOJE.plusDays(10))
                .amount(new BigDecimal("200.00"))
                .status(status)
                .build();
    }

    private static AthleteContractInputDto input(BigDecimal amount, int dueDay, LocalDate start) {
        return new AthleteContractInputDto(ContractPeriodicity.MONTHLY, amount, dueDay, start, null);
    }

    // projeções são interfaces de leitura: instâncias reais, não mocks (stub aninhado quebra o Mockito)
    private record OpenView(UUID athleteId, LocalDate dueDate) implements AthleteOpenInvoiceView {
        @Override public UUID getAthleteId() { return athleteId; }
        @Override public LocalDate getDueDate() { return dueDate; }
    }

    private record LastView(UUID contractId, LocalDate lastDueDate) implements ContractLastDueDateView {
        @Override public UUID getContractId() { return contractId; }
        @Override public LocalDate getLastDueDate() { return lastDueDate; }
    }

    private static AthleteOpenInvoiceView aberta(UUID athleteId, LocalDate dueDate) {
        return new OpenView(athleteId, dueDate);
    }

    private static ContractLastDueDateView ultima(UUID contractId, LocalDate last) {
        return new LastView(contractId, last);
    }
}
