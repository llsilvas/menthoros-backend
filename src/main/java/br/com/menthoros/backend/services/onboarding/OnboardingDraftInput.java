package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Entrada do rascunho de onboarding (retrofit 10.3, athlete-onboarding-baseline). Cobre os
 * campos que hoje tem lugar em {@code PerfilOnboardingAtleta} — os 5 genuinamente novos e os
 * que sao espelhados de {@code Atleta} (migrados so na conclusao, ver
 * {@code OnboardingService.concluirOnboarding}). {@code canalIntegracao}/{@code dispositivoMarca}
 * ficam para o retrofit 10.6, fora deste record.
 */
public record OnboardingDraftInput(
        String objetivo,
        NivelExperiencia nivelExperiencia,
        List<DiaSemana> diasDisponiveis,
        Integer volumeSemanalMax,
        Boolean temLesao,
        String descricaoLesao,
        LocalDate dataUltimaLesao,
        String historicoLesoes,
        BigDecimal maiorTreinoRecenteKm,
        Integer duracaoDisponivelMin,
        String restricoes,
        String modalidade,
        String percepcaoCondicionamento,
        boolean preenchidoPorCoach
) {
}
