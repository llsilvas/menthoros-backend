package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.planner.SeasonWeekLock;
import br.com.menthoros.backend.domain.planner.SeasonWeekSnapshot;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.repository.PlanoTreinoRepository;
import br.com.menthoros.backend.repository.SeasonWeekRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolve a semana da temporada {@code ACTIVE} que coincide com a semana do plano em geração
 * (add-season-plan-weekly-context). Separado do {@link PlanGenerationContextLoader} para o gate
 * (flag, status, semana) ser testável sem os outros nove colaboradores do loader.
 */
@Slf4j
@Component
public class SeasonWeekContextResolver {

    private final PlanoTreinoRepository planoTreinoRepository;
    private final SeasonWeekRepository seasonWeekRepository;
    private final boolean enabled;

    public SeasonWeekContextResolver(PlanoTreinoRepository planoTreinoRepository,
                                     SeasonWeekRepository seasonWeekRepository,
                                     @Value("${season-plan.enabled:false}") boolean enabled) {
        this.planoTreinoRepository = planoTreinoRepository;
        this.seasonWeekRepository = seasonWeekRepository;
        this.enabled = enabled;
    }

    /**
     * Semana da temporada ativa cujo {@code week_start} é {@code semanaInicio}. Vazio com a flag
     * desligada, sem temporada {@code ACTIVE} ou com a semana fora da temporada — nesses casos a
     * geração segue o fluxo legado.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: YES — temporada e semanas consultadas por {@code tenantId}.
     *
     * @param atleta atleta com {@code provas} já inicializadas (fonte do nome/data do marco)
     */
    public Optional<SeasonWeekSnapshot> resolve(Atleta atleta, UUID tenantId, LocalDate semanaInicio) {
        if (!enabled) {
            return Optional.empty();
        }
        Optional<UUID> temporadaId = planoTreinoRepository
                .findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(atleta.getId(), tenantId, List.of(SeasonPlanStatus.ACTIVE))
                .stream()
                .findFirst() // no máximo uma: índice único parcial uk_plano_treino_active_season (V116)
                .map(PlanoTreino::getId);
        if (temporadaId.isEmpty()) {
            return Optional.empty();
        }
        List<SeasonWeek> semanas = seasonWeekRepository
                .findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(temporadaId.get(), tenantId);
        Optional<SeasonWeekSnapshot> snapshot = semanas.stream()
                .filter(w -> semanaInicio.equals(w.getWeekStart()))
                .findFirst()
                .map(w -> toSnapshot(w, semanas, atleta));
        log.debug("[season-plan] semana {} do atleta {}: {}", semanaInicio, atleta.getId(),
                snapshot.map(s -> "semana " + s.weekIndex() + " de " + s.totalWeeks()).orElse("fora da temporada"));
        return snapshot;
    }

    private static SeasonWeekSnapshot toSnapshot(SeasonWeek semana, List<SeasonWeek> semanas, Atleta atleta) {
        return new SeasonWeekSnapshot(
                semana.getId(),
                semana.getWeekIndex(),
                semanas.size(),
                semana.getPhase(),
                semana.getTargetTss(),
                semana.getTargetVolumeKm(),
                semana.getLongRunKm(),
                semana.isDeload(),
                proximoMarco(semana, semanas, atleta));
    }

    /**
     * Mesmo filtro dos marcos exibidos ao coach ({@code SeasonPlanResponseMapper.marcos}): prova
     * cancelada ou remarcada para fora da semana gravada não conta.
     */
    private static Optional<SeasonWeekSnapshot.Milestone> proximoMarco(SeasonWeek atual, List<SeasonWeek> semanas,
                                                                        Atleta atleta) {
        Map<UUID, Prova> provas = atleta.getProvas() == null ? Map.of() : atleta.getProvas().stream()
                .filter(p -> p.getId() != null && p.getStatusProva() != ProvaStatus.CANCELADA)
                .collect(Collectors.toMap(Prova::getId, Function.identity(), (a, b) -> a));
        return semanas.stream()
                .filter(w -> w.getWeekIndex() >= atual.getWeekIndex())
                .filter(w -> w.getMilestoneRaceId() != null && marcoValido(w, provas.get(w.getMilestoneRaceId())))
                .findFirst()
                .map(w -> provas.get(w.getMilestoneRaceId()))
                .map(p -> new SeasonWeekSnapshot.Milestone(p.getNomeProva(), p.getDataProva()));
    }

    private static boolean marcoValido(SeasonWeek semana, Prova prova) {
        return prova != null && SeasonWeekLock.contains(semana.getWeekStart(), prova.getDataProva());
    }
}
