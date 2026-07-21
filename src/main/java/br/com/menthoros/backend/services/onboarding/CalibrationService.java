package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.InjuryRiskLevel;
import br.com.menthoros.backend.enums.NivelExperiencia;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Gerencia a fase de calibracao (design.md Decisao 5, athlete-onboarding-baseline):
 * determina o estagio interno ({@link CalibrationStage}), re-calcula baseline e
 * score a cada semana, e decide a saida da calibracao. Emite
 * {@code AtletaPresoEmCalibracaoEvent} se o atleta permanecer alem da semana 4.
 */
public interface CalibrationService {

    /** Estagio por numero de semana desde o inicio da calibracao — 1 a 8 (design.md Decisao 5). */
    CalibrationStage determinarEstagio(int semanaDesdeInicioCalibracao);

    /**
     * Idempotente: NAO — publica {@code AtletaPresoEmCalibracaoEvent} quando aplicavel (nao ha
     * dedup de evento entre chamadas repetidas).
     * Efeitos colaterais: publica evento de aplicacao quando o atleta esta preso alem da semana 4.
     * Tenant-aware: SIM — {@code tenantId} explicito, usado no evento.
     */
    CalibrationEvaluation avaliarSemana(
            UUID atletaId,
            UUID tenantId,
            int semanaDesdeInicioCalibracao,
            LocalDate dataReferenciaSemana,
            NivelExperiencia nivelExperiencia,
            List<NormalizedActivity> historicoDeduplicado,
            ConfidenceScorerInput confidenceInput,
            InjuryRiskLevel injuryRiskLevel
    );
}
