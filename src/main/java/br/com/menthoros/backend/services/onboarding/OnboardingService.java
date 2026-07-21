package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.OnboardingContext;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.TipoProva;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Orquestra o fluxo completo do onboarding (design.md, athlete-onboarding-baseline):
 * {@link ActivityNormalizer} -&gt; {@link ActivityDedupService} -&gt;
 * {@link BaselineCalculator} -&gt; {@link ConfidenceScorer} -&gt;
 * {@link PlanningPolicyResolver} -&gt; {@code OnboardingContext} (tipo reservado por
 * {@code deterministic-planner-engine}). Persiste o {@code AthleteBaselineSnapshot}
 * calculado.
 */
public interface OnboardingService {

    /**
     * Idempotente: NAO — persiste/atualiza {@code AthleteBaselineSnapshot} a cada chamada
     * (upsert por atleta+tenant; chamadas repetidas recalculam e sobrescrevem, sem duplicar).
     * Efeitos colaterais: persiste {@code AthleteBaselineSnapshot}; le
     * {@code TreinoRealizado}/{@code PerfilOnboardingAtleta}/{@code Atleta}.
     * Tenant-aware: SIM — {@code tenantId} explicito.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta nao existir no tenant
     */
    OnboardingContext montarContexto(UUID atletaId, UUID tenantId);

    /**
     * Cria ou atualiza a {@code Prova} alvo do atleta a partir do {@code dataProva} coletado no
     * onboarding (CA13, design.md Decisao 8) — reaproveita o CRUD de {@code Prova} ja existente
     * em vez de manter um campo solto duplicado. Quando ja existe uma {@code Prova} com a mesma
     * {@code dataProva}/{@code distancia} marcada {@code provaAlvo=true}, atualiza-a; caso
     * contrario, cria uma nova. Na MESMA transacao, desmarca {@code provaAlvo=false} de qualquer
     * outra {@code Prova} ativa do atleta — garante no maximo uma prova-alvo por atleta
     * (correcao do pre-mortem rodada 2: sem isso, {@code PeriodizationPlanner.findFirst()} pode
     * escolher uma prova diferente da que o onboarding acabou de criar).
     *
     * Idempotente: SIM para o mesmo {@code dataProva}/{@code distancia} (atualiza a mesma linha,
     * nao duplica); NAO no sentido amplo — muda o conjunto de provas-alvo do atleta a cada chamada.
     * Efeitos colaterais: persiste/atualiza {@code Prova}; pode desmarcar {@code provaAlvo} de
     * outras provas do atleta.
     * Tenant-aware: SIM — {@code tenantId} explicito.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta nao existir no tenant
     */
    Prova criarOuAtualizarProvaAlvo(UUID atletaId, UUID tenantId, LocalDate dataProva,
                                     TipoProva tipoProva, DistanciaProva distancia,
                                     BigDecimal distanciaKm, String nomeProva);
}
