package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.enums.CanalIntegracao;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DispositivoMarca;
import br.com.menthoros.backend.enums.NivelExperiencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Entrada do rascunho de onboarding (retrofit 10.3, athlete-onboarding-baseline). Cobre os
 * campos que hoje tem lugar em {@code PerfilOnboardingAtleta} — os 5 genuinamente novos, os
 * que sao espelhados de {@code Atleta} (migrados so na conclusao, ver
 * {@code OnboardingService.concluirOnboarding}), e {@code canalIntegracao}/
 * {@code dispositivoMarca}/{@code dispositivoModelo} (retrofit 10.6 — ficam SOMENTE em
 * {@code PerfilOnboardingAtleta}, nao existem em {@code Atleta} para migrar).
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
        boolean preenchidoPorCoach,
        CanalIntegracao canalIntegracao,
        DispositivoMarca dispositivoMarca,
        String dispositivoModelo
) {
}
