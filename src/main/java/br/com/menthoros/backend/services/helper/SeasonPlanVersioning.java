package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.planner.SeasonWeekLock;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.PlanoTreinoRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.SeasonWeekRepository;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Versões da temporada (add-season-plan-adjust-v2, Decisão 4 do épico): o rascunho de uma nova versão
 * é um {@link PlanoTreino} PROPOSED com {@code version + 1} e {@code baseSeasonPlanId}; aprovar arquiva
 * a ativa e ativa o rascunho, com o passado sempre recopiado da ativa.
 *
 * <p>Não abre transação: roda dentro da do serviço que chama, depois de {@link #travar}.
 */
@Slf4j
@Component
public class SeasonPlanVersioning {

    private final PlanoTreinoRepository planoTreinoRepository;
    private final ProvaRepository provaRepository;
    private final SeasonWeekRepository seasonWeekRepository;
    private final Clock clock;

    public SeasonPlanVersioning(PlanoTreinoRepository planoTreinoRepository, ProvaRepository provaRepository,
                                SeasonWeekRepository seasonWeekRepository, Clock clock) {
        this.planoTreinoRepository = planoTreinoRepository;
        this.provaRepository = provaRepository;
        this.seasonWeekRepository = seasonWeekRepository;
        this.clock = clock;
    }

    /**
     * Trava a prova da temporada ({@code FOR UPDATE}) e só então lê a temporada, como a aprovação do
     * ticket 5 (A5): ajuste, edição, descarte e aprovação do mesmo atleta se serializam, e quem chega
     * depois revalida o status já com o lock.
     *
     * Idempotent: YES. Side Effects: lock pessimista na prova. Tenant-aware: YES.
     *
     * @throws DomainNotFoundException a temporada não é do atleta no tenant
     */
    public PlanoTreino travar(UUID atletaId, UUID seasonPlanId, UUID tenantId) {
        UUID provaId = planoTreinoRepository.findRaceIdBySeasonPlan(seasonPlanId, atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Temporada não encontrada: " + seasonPlanId));
        provaRepository.findByIdAndTenantIdForUpdate(provaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Prova da temporada não encontrada: " + provaId));
        return planoTreinoRepository.findByIdAndAssessoriaId(seasonPlanId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Temporada não encontrada: " + seasonPlanId));
    }

    /**
     * Cria o rascunho da próxima versão a partir da ACTIVE, com cópia de todas as semanas.
     *
     * Idempotent: NO — cria um PlanoTreino e suas semanas. Side Effects: Database insert. Tenant-aware: YES.
     */
    public PlanoTreino criarRascunho(PlanoTreino ativa, UUID tenantId) {
        PlanoTreino r = new PlanoTreino();
        r.setAtleta(ativa.getAtleta());
        r.setProva(ativa.getProva());
        r.setAssessoria(ativa.getAssessoria());
        r.setProvaAlvo(ativa.getProvaAlvo());
        r.setNome(ativa.getNome());
        r.setDescricao(ativa.getDescricao());
        r.setObjetivo(ativa.getObjetivo());
        r.setDataInicio(ativa.getDataInicio());
        r.setStatus(ativa.getStatus());
        r.setSeasonStatus(SeasonPlanStatus.PROPOSED);
        r.setVersion(versao(ativa) + 1);
        r.setTotalWeeks(ativa.getTotalWeeks());
        r.setShortPreparation(ativa.getShortPreparation());
        r.setInitialCtl(ativa.getInitialCtl());
        r.setInitialVolumeKm(ativa.getInitialVolumeKm());
        r.setPeakVolumeKm(ativa.getPeakVolumeKm());
        r.setMaxLongRunKm(ativa.getMaxLongRunKm());
        r.setBaseSeasonPlanId(ativa.getId());
        // flush: o índice de uma PROPOSED por atleta (V118) recusa aqui, não no commit.
        PlanoTreino salvo = planoTreinoRepository.saveAndFlush(r);
        List<SeasonWeek> copias = seasonWeekRepository
                .findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(ativa.getId(), tenantId).stream()
                .map(w -> copia(w, salvo))
                .toList();
        seasonWeekRepository.saveAll(copias);
        log.info("Rascunho de temporada criado: id={}, base={}, version={}", salvo.getId(), ativa.getId(), salvo.getVersion());
        return salvo;
    }

    /**
     * Ativa o rascunho: confere que a base ainda é a ACTIVE e a versão seguinte, recopia da ativa as
     * semanas travadas hoje, recalcula o pico e troca as duas — arquiva a ativa primeiro, por causa do
     * índice de uma ACTIVE por atleta (V116).
     *
     * Idempotent: NO. Side Effects: Database update (duas temporadas e as semanas do rascunho). Tenant-aware: YES.
     *
     * @throws DomainConflictException a base deixou de ser a ACTIVE ou outra versão foi aprovada antes
     */
    public void ativarRascunho(PlanoTreino rascunho, String motivo, UUID aprovadoPor, UUID tenantId) {
        UUID atletaId = rascunho.getAtleta().getId();
        PlanoTreino ativa = planoTreinoRepository.findByIdAndAssessoriaId(rascunho.getBaseSeasonPlanId(), tenantId)
                .filter(b -> b.getSeasonStatus() == SeasonPlanStatus.ACTIVE)
                .filter(b -> b.getAtleta() != null && atletaId.equals(b.getAtleta().getId()))
                .filter(b -> versao(b) + 1 == versao(rascunho))
                .orElseThrow(() -> new DomainConflictException(
                        "A temporada ativa mudou depois de o ajuste começar: descarte o ajuste e comece de novo"));

        LocalDate hoje = LocalDate.now(clock.withZone(SeasonWeekLock.FUSO));
        @Nullable LocalDate dataProva = rascunho.getProva() == null ? null : rascunho.getProva().getDataProva();
        Map<Integer, SeasonWeek> daAtiva = seasonWeekRepository
                .findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(ativa.getId(), tenantId).stream()
                .collect(Collectors.toMap(SeasonWeek::getWeekIndex, Function.identity()));
        List<SeasonWeek> semanas = seasonWeekRepository
                .findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(rascunho.getId(), tenantId);
        // O passado nunca vem do rascunho: a semana pode ter virado entre o ajuste e a aprovação.
        semanas.stream()
                .filter(w -> SeasonWeekLock.of(w.getWeekStart(), hoje, dataProva).isPresent())
                .filter(w -> daAtiva.containsKey(w.getWeekIndex()))
                .forEach(w -> copiarValores(daAtiva.get(w.getWeekIndex()), w));
        recalcularPicos(rascunho, semanas);
        seasonWeekRepository.saveAll(semanas);

        ativa.setSeasonStatus(SeasonPlanStatus.ARCHIVED);
        planoTreinoRepository.saveAndFlush(ativa);
        Instant agora = clock.instant().truncatedTo(ChronoUnit.MICROS);
        rascunho.setSeasonStatus(SeasonPlanStatus.ACTIVE);
        rascunho.setVersionReason(motivo);
        rascunho.setApprovedBy(aprovadoPor);
        rascunho.setApprovedAt(agora);
        planoTreinoRepository.saveAndFlush(rascunho);
        // Métrica do ticket 6: versões por ciclo e intervalo desde a aprovação anterior, por tenant.
        log.info("season-plan.version-approved tenantId={} seasonPlanId={} version={} daysSincePreviousApproval={}",
                tenantId, rascunho.getId(), rascunho.getVersion(),
                ativa.getApprovedAt() == null ? null : ChronoUnit.DAYS.between(ativa.getApprovedAt(), agora));
    }

    /** Maior volume e maior longão entre as semanas; sem nenhum valor, mantém o que havia. */
    public static void recalcularPicos(PlanoTreino p, List<SeasonWeek> semanas) {
        BigDecimal pico = maximo(semanas.stream().map(SeasonWeek::getTargetVolumeKm).toList());
        BigDecimal longao = maximo(semanas.stream().map(SeasonWeek::getLongRunKm).toList());
        if (pico != null) {
            p.setPeakVolumeKm(pico);
        }
        if (longao != null) {
            p.setMaxLongRunKm(longao);
        }
    }

    /** Ajuste (PROPOSED com base) aberto a partir de {@code temporada}, se ela é a ACTIVE. */
    public static @Nullable UUID rascunhoAberto(PlanoTreino temporada, List<PlanoTreino> candidatas) {
        if (temporada.getSeasonStatus() != SeasonPlanStatus.ACTIVE) {
            return null;
        }
        return candidatas.stream()
                .filter(p -> p.getSeasonStatus() == SeasonPlanStatus.PROPOSED && temporada.getId().equals(p.getBaseSeasonPlanId()))
                .map(PlanoTreino::getId)
                .findFirst()
                .orElse(null);
    }

    private static @Nullable BigDecimal maximo(List<BigDecimal> valores) {
        return valores.stream().filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }

    private static int versao(PlanoTreino p) {
        return p.getVersion() == null ? 1 : p.getVersion();
    }

    private static SeasonWeek copia(SeasonWeek origem, PlanoTreino temporada) {
        SeasonWeek s = new SeasonWeek();
        s.setTenantId(origem.getTenantId());
        s.setSeasonPlan(temporada);
        s.setWeekIndex(origem.getWeekIndex());
        s.setWeekStart(origem.getWeekStart());
        copiarValores(origem, s);
        return s;
    }

    private static void copiarValores(SeasonWeek de, SeasonWeek para) {
        para.setPhase(de.getPhase());
        para.setTargetVolumeKm(de.getTargetVolumeKm());
        para.setTargetTss(de.getTargetTss());
        para.setLongRunKm(de.getLongRunKm());
        para.setDeload(de.isDeload());
        para.setKeySessions(de.getKeySessions());
        para.setValueSource(de.getValueSource());
        para.setMilestoneRaceId(de.getMilestoneRaceId());
    }
}
