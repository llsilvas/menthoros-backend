package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.billing.AthleteBilling;
import br.com.menthoros.backend.dto.input.AthleteContractInputDto;
import br.com.menthoros.backend.entity.AthleteContract;
import br.com.menthoros.backend.entity.AthleteInvoice;
import br.com.menthoros.backend.enums.AthleteBillingStatus;
import br.com.menthoros.backend.enums.InvoiceStatus;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AthleteContractRepository;
import br.com.menthoros.backend.repository.AthleteInvoiceRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.projection.AthleteOpenInvoiceView;
import br.com.menthoros.backend.repository.projection.ContractLastDueDateView;
import br.com.menthoros.backend.services.AthleteContractService;
import br.com.menthoros.backend.services.helper.DueDateCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Regras do contrato do atleta e das mensalidades (design D2–D6, D9).
 *
 * <p>Concorrência: toda mutação do contrato e a geração de mensalidade passam por
 * {@code findByIdAndTenantIdForUpdate} (lock pessimista), então "editar vale só para as
 * futuras" e "encerrado não gera" são garantidos por serialização. O {@code UNIQUE
 * (contract_id, due_date)} é rede de segurança: se disparar, a exceção sobe (409 na request;
 * o scheduler loga por contrato) — não é engolida dentro da transação, porque o commit falharia
 * depois do catch.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AthleteContractServiceImpl implements AthleteContractService {

    /** Teto de gerações por contrato por chamada; a próxima execução continua de onde parou (D3). */
    static final int MAX_INVOICES_PER_RUN = 24;

    /** Fuso da cobrança, compartilhado com o scheduler de renovação. */
    public static final ZoneId BILLING_ZONE = ZoneId.of("America/Sao_Paulo");

    private final AthleteContractRepository contractRepository;
    private final AthleteInvoiceRepository invoiceRepository;
    private final AtletaRepository atletaRepository;
    private final CacheManager cacheManager;
    private final Clock clock;

    /**
     * Idempotent: YES — leitura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AthleteContract> findActiveContract(UUID athleteId) {
        return contractRepository.findActiveByAthleteIdAndTenantId(athleteId, TenantContext.getRequiredTenantId());
    }

    /**
     * Idempotent: YES — leitura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Override
    @Transactional(readOnly = true)
    public List<AthleteInvoice> listInvoices(UUID contractId) {
        return invoiceRepository.findByContractIdAndTenantIdOrderByDueDateDesc(contractId, TenantContext.getRequiredTenantId());
    }

    /**
     * Idempotent: YES para o mesmo input — a segunda chamada edita o que a primeira criou.
     * Side Effects: Database insert/update do contrato; insert da primeira mensalidade na criação.
     * Tenant-aware: YES
     *
     * @throws DomainNotFoundException atleta inexistente ou de outro tenant
     * @throws org.springframework.dao.DataIntegrityViolationException dois PUT criando ao mesmo
     *         tempo: o segundo bate no índice parcial e recebe 409; repetir vira edição (D1)
     */
    @Override
    @Transactional
    public AthleteContract createOrUpdate(UUID athleteId, AthleteContractInputDto input) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        validate(input);
        atletaRepository.findByIdAndTenantId(athleteId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));

        Optional<AthleteContract> existing = contractRepository.findActiveByAthleteIdAndTenantId(athleteId, tenantId);
        if (existing.isPresent()) {
            AthleteContract locked = lockContract(existing.get().getId(), tenantId);
            // releitura sob lock: se um encerramento venceu a corrida, não editar o contrato morto — criar outro
            if (locked.isActive()) {
                apply(locked, input);
                AthleteContract saved = contractRepository.save(locked);
                evictAthleteCaches(athleteId, tenantId);
                log.info("Contrato do atleta editado: contractId={}, athleteId={}, tenantId={}", saved.getId(), athleteId, tenantId);
                return saved;
            }
            log.info("Contrato {} encerrado durante a edição; criando um novo para athleteId={}", locked.getId(), athleteId);
        }

        AthleteContract contract = AthleteContract.builder()
                .tenantId(tenantId)
                .athleteId(athleteId)
                .build();
        apply(contract, input);
        // flush aqui para o índice parcial disparar nesta linha (409) e o lock abaixo encontrar a linha
        AthleteContract created = contractRepository.saveAndFlush(contract);
        int generated = ensureNextInvoice(created.getId(), tenantId, today());
        evictAthleteCaches(athleteId, tenantId);
        log.info("Contrato do atleta criado: contractId={}, athleteId={}, tenantId={}, mensalidades={}",
                created.getId(), athleteId, tenantId, generated);
        return created;
    }

    /**
     * Idempotent: NO — a segunda chamada não encontra contrato ativo (404).
     * Side Effects: Database update (endedAt). Mensalidades em aberto ficam como estão.
     * Tenant-aware: YES
     */
    @Override
    @Transactional
    public AthleteContract end(UUID athleteId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        AthleteContract active = contractRepository.findActiveByAthleteIdAndTenantId(athleteId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta sem contrato ativo"));
        AthleteContract locked = lockContract(active.getId(), tenantId);
        locked.setEndedAt(OffsetDateTime.now(clock));
        AthleteContract saved = contractRepository.save(locked);
        evictAthleteCaches(athleteId, tenantId);
        log.info("Contrato do atleta encerrado: contractId={}, athleteId={}, tenantId={}", saved.getId(), athleteId, tenantId);
        return saved;
    }

    /**
     * Idempotent: YES — rodar de novo não gera nada enquanto a última mensalidade não vencer.
     * Side Effects: Database insert de 0..{@value #MAX_INVOICES_PER_RUN} mensalidades.
     * Tenant-aware: YES — recebe o tenant explícito (o scheduler roda fora de request).
     *
     * <p>Sem mensalidade nenhuma, a primeira nasce em {@code firstDueDate(max(startDate, today))}:
     * nunca no passado, o sistema não inventa dívida que não viu nascer (D3/D7).</p>
     *
     * <p>Chamado de {@link #createOrUpdate} por {@code this} (self-invocation): ali o proxy não
     * intercepta e o método roda na transação da criação — o que é o desejado, contrato e
     * primeira mensalidade nascem ou falham juntos. O {@code @Transactional} vale para o scheduler.</p>
     */
    @Override
    @Transactional
    public int ensureNextInvoice(UUID contractId, UUID tenantId, LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("today cannot be null");
        }
        AthleteContract contract = lockContract(contractId, tenantId);
        if (!contract.isActive()) {
            return 0;
        }
        Optional<LocalDate> last = invoiceRepository
                .findTopByContractIdAndTenantIdOrderByDueDateDesc(contractId, tenantId)
                .map(AthleteInvoice::getDueDate);

        int generated = 0;
        LocalDate cursor;
        if (last.isPresent()) {
            cursor = last.get();
        } else {
            LocalDate start = contract.getStartDate().isAfter(today) ? contract.getStartDate() : today;
            cursor = DueDateCalendar.firstDueDate(start, contract.getDueDay());
            createInvoice(contract, cursor);
            generated++;
        }
        while (cursor.isBefore(today)) {
            if (generated >= MAX_INVOICES_PER_RUN) {
                log.warn("Teto de {} mensalidades atingido: contractId={}, tenantId={}, última gerada={}; a próxima execução continua",
                        MAX_INVOICES_PER_RUN, contractId, tenantId, cursor);
                break;
            }
            cursor = DueDateCalendar.nextDueDate(cursor, contract.getPeriodicity(), contract.getDueDay());
            createInvoice(contract, cursor);
            generated++;
        }
        if (generated > 0) {
            log.info("Mensalidades geradas: contractId={}, tenantId={}, quantidade={}, última={}", contractId, tenantId, generated, cursor);
        }
        return generated;
    }

    /**
     * Idempotent: NO — a segunda chamada encontra PAID e responde 409.
     * Side Effects: Database update (status, paidAt, paidAmount).
     * Tenant-aware: YES
     */
    @Override
    @Transactional
    public AthleteInvoice markPaid(UUID invoiceId, LocalDate paidAt, BigDecimal paidAmount) {
        if (paidAmount != null && paidAmount.signum() < 0) {
            throw new IllegalArgumentException("Valor pago não pode ser negativo");
        }
        if (paidAt != null && paidAt.isAfter(today())) {
            throw new IllegalArgumentException("Data do pagamento não pode ser futura");
        }
        AthleteInvoice invoice = requireInvoice(invoiceId);
        requireStatus(invoice, InvoiceStatus.OPEN, "dar baixa");
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(paidAt != null ? paidAt : today());
        invoice.setPaidAmount(paidAmount != null ? paidAmount : invoice.getAmount());
        AthleteInvoice saved = invoiceRepository.save(invoice);
        evictAthleteCaches(saved);
        log.info("Baixa de mensalidade: invoiceId={}, tenantId={}, paidAt={}", invoiceId, invoice.getTenantId(), saved.getPaidAt());
        return saved;
    }

    /**
     * Idempotent: NO — a segunda chamada encontra OPEN e responde 409.
     * Side Effects: Database update (status, paidAt e paidAmount anulados).
     * Tenant-aware: YES
     */
    @Override
    @Transactional
    public AthleteInvoice undoPayment(UUID invoiceId) {
        AthleteInvoice invoice = requireInvoice(invoiceId);
        requireStatus(invoice, InvoiceStatus.PAID, "desfazer a baixa");
        invoice.setStatus(InvoiceStatus.OPEN);
        invoice.setPaidAt(null);
        invoice.setPaidAmount(null);
        AthleteInvoice saved = invoiceRepository.save(invoice);
        evictAthleteCaches(saved);
        log.info("Baixa desfeita: invoiceId={}, tenantId={}", invoiceId, invoice.getTenantId());
        return saved;
    }

    /**
     * Idempotent: NO — a segunda chamada encontra CANCELLED e responde 409.
     * Side Effects: Database update (status). Não gera outra mensalidade no mesmo período.
     * Tenant-aware: YES
     */
    @Override
    @Transactional
    public AthleteInvoice cancel(UUID invoiceId) {
        AthleteInvoice invoice = requireInvoice(invoiceId);
        requireStatus(invoice, InvoiceStatus.OPEN, "cancelar");
        invoice.setStatus(InvoiceStatus.CANCELLED);
        AthleteInvoice saved = invoiceRepository.save(invoice);
        evictAthleteCaches(saved);
        log.info("Mensalidade cancelada: invoiceId={}, tenantId={}", invoiceId, invoice.getTenantId());
        return saved;
    }

    /**
     * Idempotent: YES — leitura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AthleteBilling> resolveBilling(UUID athleteId, LocalDate today) {
        return Optional.ofNullable(resolveBilling(List.of(athleteId), today).get(athleteId));
    }

    /**
     * Idempotent: YES — leitura, 2–3 queries para o lote inteiro.
     * Side Effects: NONE
     * Tenant-aware: YES
     *
     * <p>Com mensalidade em aberto: status pela lista de vencimentos e próximo = o menor. Sem em
     * aberto mas com contrato ativo: UP_TO_DATE e próximo calculado a partir da última mensalidade
     * (ou do início, se nunca houve) — fluxo normal após baixa antecipada (D4). Sem nenhum dos
     * dois: fora do mapa.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AthleteBilling> resolveBilling(Collection<UUID> athleteIds, LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("today cannot be null");
        }
        if (athleteIds == null || athleteIds.isEmpty()) {
            return Map.of();
        }
        UUID tenantId = TenantContext.getRequiredTenantId();
        Set<UUID> ids = new HashSet<>(athleteIds);

        Map<UUID, List<LocalDate>> openByAthlete = new HashMap<>();
        for (AthleteOpenInvoiceView open : invoiceRepository.findOpenByTenantIdAndAthleteIdIn(tenantId, ids)) {
            openByAthlete.computeIfAbsent(open.getAthleteId(), k -> new ArrayList<>()).add(open.getDueDate());
        }

        Map<UUID, AthleteBilling> result = new HashMap<>();
        openByAthlete.forEach((athleteId, dueDates) -> {
            LocalDate next = dueDates.stream().min(LocalDate::compareTo).orElseThrow();
            result.put(athleteId, new AthleteBilling(AthleteBillingStatus.resolve(dueDates, today), next));
        });

        Set<UUID> withoutOpen = new HashSet<>(ids);
        withoutOpen.removeAll(openByAthlete.keySet());
        if (withoutOpen.isEmpty()) {
            return result;
        }
        List<AthleteContract> activeContracts = contractRepository.findActiveByTenantIdAndAthleteIdIn(tenantId, withoutOpen);
        if (activeContracts.isEmpty()) {
            return result;
        }
        Map<UUID, LocalDate> lastByContract = new HashMap<>();
        List<UUID> contractIds = activeContracts.stream().map(AthleteContract::getId).toList();
        for (ContractLastDueDateView view : invoiceRepository.findLastDueDateByContract(tenantId, contractIds)) {
            lastByContract.put(view.getContractId(), view.getLastDueDate());
        }
        for (AthleteContract contract : activeContracts) {
            LocalDate last = lastByContract.get(contract.getId());
            LocalDate next = last != null
                    ? DueDateCalendar.nextDueDate(last, contract.getPeriodicity(), contract.getDueDay())
                    : DueDateCalendar.firstDueDate(
                            contract.getStartDate().isAfter(today) ? contract.getStartDate() : today,
                            contract.getDueDay());
            result.put(contract.getAthleteId(), new AthleteBilling(AthleteBillingStatus.UP_TO_DATE, next));
        }
        return result;
    }

    // ---- helpers ----

    /**
     * {@code AtletaOutputDto} carrega {@code billingStatus}/{@code nextDueDate} e vive nos caches
     * {@code atletas} (por atleta) e {@code atletas-list} (por tenant) do {@code AtletaServiceImpl},
     * com TTL de 30 min. Sem esta invalidação, uma baixa levaria até meia hora para sumir do
     * badge no GET de atleta. Mesmas chaves das anotações lá; precedente: {@code TsbRecalculoExecutor}.
     */
    private void evictAthleteCaches(UUID athleteId, UUID tenantId) {
        Cache porAtleta = cacheManager.getCache("atletas");
        if (porAtleta != null) {
            porAtleta.evict(athleteId + "_" + tenantId);
        }
        Cache lista = cacheManager.getCache("atletas-list");
        if (lista != null) {
            lista.evict(tenantId);
        }
    }

    private void evictAthleteCaches(AthleteInvoice invoice) {
        contractRepository.findById(invoice.getContractId())
                .filter(c -> invoice.getTenantId().equals(c.getTenantId()))
                .ifPresent(c -> evictAthleteCaches(c.getAthleteId(), c.getTenantId()));
    }

    private AthleteContract lockContract(UUID contractId, UUID tenantId) {
        return contractRepository.findByIdAndTenantIdForUpdate(contractId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Contrato não encontrado"));
    }

    private AthleteInvoice requireInvoice(UUID invoiceId) {
        return invoiceRepository.findByIdAndTenantId(invoiceId, TenantContext.getRequiredTenantId())
                .orElseThrow(() -> new DomainNotFoundException("Mensalidade não encontrada"));
    }

    private static void requireStatus(AthleteInvoice invoice, InvoiceStatus expected, String action) {
        if (invoice.getStatus() != expected) {
            throw new DomainConflictException(
                    "Não é possível " + action + " de mensalidade " + invoice.getStatus() + "; esperado " + expected);
        }
    }

    private void createInvoice(AthleteContract contract, LocalDate dueDate) {
        invoiceRepository.save(AthleteInvoice.builder()
                .tenantId(contract.getTenantId())
                .contractId(contract.getId())
                .dueDate(dueDate)
                .amount(contract.getAmount())
                .status(InvoiceStatus.OPEN)
                .build());
    }

    private static void apply(AthleteContract contract, AthleteContractInputDto input) {
        contract.setPeriodicity(input.periodicity());
        contract.setAmount(input.amount());
        contract.setDueDay(input.dueDay());
        contract.setStartDate(input.startDate());
        contract.setAthleteNoticeEnabled(input.athleteNoticeEnabled() == null || input.athleteNoticeEnabled());
    }

    /** Defesa em profundidade: o controller já valida com Bean Validation, o serviço não confia. */
    private static void validate(AthleteContractInputDto input) {
        if (input == null) {
            throw new IllegalArgumentException("Contrato não informado");
        }
        if (input.periodicity() == null) {
            throw new IllegalArgumentException("Periodicidade é obrigatória");
        }
        if (input.dueDay() == null || input.dueDay() < 1 || input.dueDay() > 31) {
            throw new IllegalArgumentException("Dia de vencimento deve estar entre 1 e 31");
        }
        if (input.startDate() == null) {
            throw new IllegalArgumentException("Data de início é obrigatória");
        }
        if (input.amount() != null && input.amount().signum() < 0) {
            throw new IllegalArgumentException("Valor não pode ser negativo");
        }
    }

    /**
     * "Hoje" da cobrança é o de São Paulo, o mesmo do scheduler — em host UTC, entre 21h e 0h,
     * o fuso padrão já diria "amanhã" e a primeira mensalidade ou a data de baixa divergiriam
     * do que o job considera.
     */
    private LocalDate today() {
        return LocalDate.now(clock.withZone(BILLING_ZONE));
    }
}
