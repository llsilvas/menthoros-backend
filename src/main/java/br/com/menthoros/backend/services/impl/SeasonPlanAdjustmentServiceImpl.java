package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.planner.SeasonWeekLock;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.dto.input.SeasonWeekUpdateInputDto;
import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.enums.WeekValueSource;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.SeasonPlanDisabledException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.PlanoTreinoRepository;
import br.com.menthoros.backend.repository.SeasonWeekRepository;
import br.com.menthoros.backend.services.SeasonPlanAdjustmentService;
import br.com.menthoros.backend.services.SeasonPlanService.Generation;
import br.com.menthoros.backend.services.helper.SeasonPlanResponseMapper;
import br.com.menthoros.backend.services.helper.SeasonPlanVersioning;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ajuste da temporada aprovada (add-season-plan-adjust-v2). As versões em si (rascunho, ativação,
 * lock) vivem no {@link SeasonPlanVersioning}; aqui ficam as regras de cada rota.
 */
@Slf4j
@Service
public class SeasonPlanAdjustmentServiceImpl implements SeasonPlanAdjustmentService {

    /** RACE_WEEK é só da semana da prova, que é travada; as demais fases do motor semanal nunca entram na temporada. */
    private static final Set<TrainingPhase> FASES_EDITAVEIS =
            EnumSet.of(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER);
    private static final int KEY_SESSIONS_MAX = 500;
    // Tetos de domínio bem acima do real (e dentro de NUMERIC(10,3)): valor absurdo vira 422, nunca 500 do banco.
    private static final BigDecimal VOLUME_MAX_KM = BigDecimal.valueOf(500);
    private static final BigDecimal LONGAO_MAX_KM = BigDecimal.valueOf(200);
    private static final double TSS_MAX = 3000;
    private static final String DESCARTADO = "rascunho descartado";

    private final PlanoTreinoRepository planoTreinoRepository;
    private final SeasonWeekRepository seasonWeekRepository;
    private final SeasonPlanVersioning versioning;
    private final SeasonPlanResponseMapper responseMapper;
    private final Clock clock;
    private final boolean enabled;

    public SeasonPlanAdjustmentServiceImpl(PlanoTreinoRepository planoTreinoRepository,
                                           SeasonWeekRepository seasonWeekRepository,
                                           SeasonPlanVersioning versioning,
                                           SeasonPlanResponseMapper responseMapper,
                                           Clock clock,
                                           @Value("${season-plan.enabled:false}") boolean enabled) {
        this.planoTreinoRepository = planoTreinoRepository;
        this.seasonWeekRepository = seasonWeekRepository;
        this.versioning = versioning;
        this.responseMapper = responseMapper;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public Generation createDraft(UUID atletaId, UUID seasonPlanId) {
        exigirHabilitado();
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("Ajuste de temporada: base={}, atletaId={}, tenantId={}", seasonPlanId, atletaId, tenantId);
        PlanoTreino base = versioning.travar(atletaId, seasonPlanId, tenantId);
        if (base.getSeasonStatus() != SeasonPlanStatus.ACTIVE) {
            throw new DomainConflictException("Só a temporada ativa pode ser ajustada");
        }
        List<PlanoTreino> propostas = planoTreinoRepository.findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(
                atletaId, tenantId, List.of(SeasonPlanStatus.PROPOSED));
        Optional<PlanoTreino> mesmaBase = propostas.stream()
                .filter(p -> base.getId().equals(p.getBaseSeasonPlanId()))
                .findFirst();
        if (mesmaBase.isPresent()) {
            log.info("Ajuste já aberto: id={}", mesmaBase.get().getId());
            return new Generation(responseMapper.toDto(mesmaBase.get(), tenantId, null), false);
        }
        if (!propostas.isEmpty()) {
            throw new DomainConflictException("O atleta já tem outra temporada proposta");
        }
        PlanoTreino rascunho = versioning.criarRascunho(base, tenantId);
        return new Generation(responseMapper.toDto(rascunho, tenantId, null), true);
    }

    @Override
    @Transactional(readOnly = true)
    public SeasonPlanResponseDto findById(UUID atletaId, UUID seasonPlanId) {
        exigirHabilitado();
        UUID tenantId = TenantContext.getRequiredTenantId();
        // O id da prova só serve para provar que a temporada é do atleta no tenant.
        planoTreinoRepository.findRaceIdBySeasonPlan(seasonPlanId, atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Temporada não encontrada: " + seasonPlanId));
        PlanoTreino temporada = planoTreinoRepository.findByIdAndAssessoriaId(seasonPlanId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Temporada não encontrada: " + seasonPlanId));
        @Nullable UUID rascunho = temporada.getSeasonStatus() == SeasonPlanStatus.ACTIVE
                ? SeasonPlanVersioning.rascunhoAberto(temporada, planoTreinoRepository
                        .findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(atletaId, tenantId, List.of(SeasonPlanStatus.PROPOSED)))
                : null;
        return responseMapper.toDto(temporada, tenantId, rascunho);
    }

    @Override
    @Transactional
    public SeasonPlanResponseDto updateWeek(UUID atletaId, UUID seasonPlanId, int weekIndex,
                                            SeasonWeekUpdateInputDto input) {
        exigirHabilitado();
        validarEntrada(input);
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("Editando semana {} da temporada {}: atletaId={}, tenantId={}", weekIndex, seasonPlanId, atletaId, tenantId);
        PlanoTreino temporada = versioning.travar(atletaId, seasonPlanId, tenantId);
        if (temporada.getSeasonStatus() != SeasonPlanStatus.PROPOSED) {
            throw new DomainConflictException("Só uma temporada proposta ou em ajuste pode ser editada");
        }
        List<SeasonWeek> semanas = seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(
                temporada.getId(), tenantId);
        SeasonWeek semana = semanas.stream()
                .filter(w -> w.getWeekIndex() != null && w.getWeekIndex() == weekIndex)
                .findFirst()
                .orElseThrow(() -> new DomainNotFoundException("Semana " + weekIndex + " não encontrada na temporada"));
        LocalDate hoje = LocalDate.now(clock.withZone(SeasonWeekLock.FUSO));
        @Nullable LocalDate dataProva = temporada.getProva() == null ? null : temporada.getProva().getDataProva();
        SeasonWeekLock.of(semana.getWeekStart(), hoje, dataProva).ifPresent(motivo -> {
            throw new DomainRuleViolationException(mensagemTrava(weekIndex, motivo));
        });

        aplicar(semana, input);
        semana.setValueSource(WeekValueSource.COACH);
        SeasonPlanVersioning.recalcularPicos(temporada, semanas);
        seasonWeekRepository.save(semana);
        planoTreinoRepository.save(temporada);
        log.info("Semana {} da temporada {} editada pelo treinador", weekIndex, seasonPlanId);
        return responseMapper.toDto(temporada, semanas, tenantId, null);
    }

    @Override
    @Transactional
    public void discard(UUID atletaId, UUID seasonPlanId) {
        exigirHabilitado();
        UUID tenantId = TenantContext.getRequiredTenantId();
        PlanoTreino temporada = versioning.travar(atletaId, seasonPlanId, tenantId);
        if (temporada.getSeasonStatus() != SeasonPlanStatus.PROPOSED || temporada.getBaseSeasonPlanId() == null) {
            throw new DomainConflictException("Só um ajuste em aberto pode ser descartado");
        }
        temporada.setSeasonStatus(SeasonPlanStatus.ARCHIVED);
        temporada.setVersionReason(DESCARTADO);
        planoTreinoRepository.save(temporada);
        log.info("Ajuste descartado: id={}, base={}, tenantId={}", seasonPlanId, temporada.getBaseSeasonPlanId(), tenantId);
    }

    /** Revalida no serviço o que o Bean Validation já cobre na rota, mais a fase (regra de domínio). */
    private static void validarEntrada(SeasonWeekUpdateInputDto input) {
        if (input == null) {
            throw new DomainRuleViolationException("Informe os valores da semana");
        }
        if (input.phase() != null && !FASES_EDITAVEIS.contains(input.phase())) {
            throw new DomainRuleViolationException("Fase " + input.phase() + " não pode ser escolhida no ajuste");
        }
        if ((input.targetTss() != null && input.targetTss() < 0) || negativo(input.targetVolumeKm())
                || negativo(input.longRunKm())) {
            throw new DomainRuleViolationException("Os alvos da semana não podem ser negativos");
        }
        if ((input.targetTss() != null && (input.targetTss().isNaN() || input.targetTss() > TSS_MAX))
                || acima(input.targetVolumeKm(), VOLUME_MAX_KM) || acima(input.longRunKm(), LONGAO_MAX_KM)) {
            throw new DomainRuleViolationException("Alvo fora do limite: volume até " + VOLUME_MAX_KM + " km, longão até "
                    + LONGAO_MAX_KM + " km e TSS até " + (int) TSS_MAX);
        }
        if (input.keySessions() != null && input.keySessions().length() > KEY_SESSIONS_MAX) {
            throw new DomainRuleViolationException("As sessões-chave têm no máximo " + KEY_SESSIONS_MAX + " caracteres");
        }
    }

    private static boolean acima(@Nullable BigDecimal valor, BigDecimal teto) {
        return valor != null && valor.compareTo(teto) > 0;
    }

    private static boolean negativo(@Nullable BigDecimal valor) {
        return valor != null && valor.signum() < 0;
    }

    private static void aplicar(SeasonWeek semana, SeasonWeekUpdateInputDto input) {
        if (input.phase() != null) {
            semana.setPhase(input.phase());
        }
        if (input.targetTss() != null) {
            semana.setTargetTss(input.targetTss());
        }
        if (input.targetVolumeKm() != null) {
            semana.setTargetVolumeKm(input.targetVolumeKm());
        }
        if (input.longRunKm() != null) {
            semana.setLongRunKm(input.longRunKm());
        }
        if (input.deload() != null) {
            semana.setDeload(input.deload());
        }
        if (input.keySessions() != null) {
            semana.setKeySessions(input.keySessions().isBlank() ? null : input.keySessions().trim());
        }
    }

    private static String mensagemTrava(int weekIndex, SeasonWeekLock.Reason motivo) {
        return switch (motivo) {
            case COMPLETED -> "A semana " + weekIndex + " já foi concluída e não pode ser editada";
            case IN_PROGRESS -> "A semana " + weekIndex + " está em andamento e não pode ser editada";
            case RACE_WEEK -> "A semana " + weekIndex + " é a da prova-alvo e não pode ser editada";
        };
    }

    private void exigirHabilitado() {
        if (!enabled) {
            throw new SeasonPlanDisabledException();
        }
    }
}
