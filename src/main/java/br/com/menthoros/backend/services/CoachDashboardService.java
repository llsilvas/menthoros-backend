package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.CoachAtletaResumoDto;
import br.com.menthoros.backend.dto.output.CoachCalendarioDto;
import br.com.menthoros.backend.dto.output.CoachInsightsDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Visões agregadas por tenant para o shell do coach (roster, calendário, insights).
 * Read-only e tenant-aware (resolve o tenant via TenantContext).
 */
public interface CoachDashboardService {

    /**
     * Roster enriquecido dos atletas do tenant.
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     */
    List<CoachAtletaResumoDto> getRoster();

    /**
     * Calendário de treinos planejados do tenant na semana de {@code from} (default: semana atual).
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     */
    CoachCalendarioDto getCalendarioSemanal(LocalDate from);

    /**
     * Insights agregados do tenant no intervalo (default: últimas 12 semanas).
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     */
    CoachInsightsDto getInsights(LocalDate from, LocalDate to);
}
