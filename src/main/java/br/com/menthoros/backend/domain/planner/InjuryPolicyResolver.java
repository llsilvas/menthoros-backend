package br.com.menthoros.backend.domain.planner;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Resolve override de fase e necessidade de revisao do coach a partir dos campos de lesao do
 * atleta (design.md Decisao 14). A janela de "lesao recente" e parametro explicito — o default
 * de 30 dias vem de {@code planner-engine.injury.recent-window-days} na camada de service, nao
 * hardcoded no dominio.
 */
@Component
public class InjuryPolicyResolver {

    public InjuryPolicyResult resolve(AthleteSnapshot atleta, LocalDate referenceDate, int recentWindowDias) {
        if (atleta.temLesao()) {
            return new InjuryPolicyResult(Optional.of(TrainingPhase.RECOVERY), true, "INJURY_ACTIVE");
        }

        if (atleta.dataUltimaLesao() != null) {
            LocalDate inicioJanela = referenceDate.minusDays(recentWindowDias);
            if (!atleta.dataUltimaLesao().isBefore(inicioJanela)) {
                return new InjuryPolicyResult(Optional.of(TrainingPhase.RETURN_TO_TRAINING), false, "INJURY_RECENT");
            }
        }

        if (atleta.descricaoLesao() != null && !atleta.descricaoLesao().isBlank()) {
            return new InjuryPolicyResult(Optional.empty(), true, "INJURY_DESCRIPTION_UNSTRUCTURED");
        }

        return InjuryPolicyResult.fluxoNormal();
    }
}
