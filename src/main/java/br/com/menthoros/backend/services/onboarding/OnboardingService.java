package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.InjuryRiskLevel;
import br.com.menthoros.backend.domain.planner.OnboardingContext;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.TipoProva;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquestra o fluxo completo do onboarding (design.md, athlete-onboarding-baseline):
 * {@link ActivityNormalizer} -&gt; {@link ActivityDedupService} -&gt;
 * {@link BaselineCalculator} -&gt; {@link ConfidenceScorer} -&gt;
 * {@link PlanningPolicyResolver} -&gt; {@code OnboardingContext} (tipo reservado por
 * {@code deterministic-planner-engine}). Persiste o {@code AthleteBaselineState}
 * calculado.
 */
public interface OnboardingService {

    /**
     * Idempotente: NAO — persiste/atualiza {@code AthleteBaselineState} a cada chamada
     * (upsert por atleta+tenant; chamadas repetidas recalculam e sobrescrevem, sem duplicar).
     * Efeitos colaterais: persiste {@code AthleteBaselineState}; le
     * {@code TreinoRealizado}/{@code PerfilOnboardingAtleta}/{@code Atleta}.
     * Tenant-aware: SIM — {@code tenantId} explicito.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta nao existir no tenant
     */
    OnboardingContext montarContexto(UUID atletaId, UUID tenantId);

    /**
     * Indica se o atleta ja possui um {@code AthleteBaselineState} persistido — usado pelo
     * kill-switch {@code onboarding.migrate-existing.enabled} (tasks.md 5.7, design.md Decisao 6)
     * para decidir se um atleta legado (pre-onboarding) deve ter o baseline calculado na
     * proxima geracao de plano.
     *
     * Idempotente: SIM — leitura pura.
     * Efeitos colaterais: NENHUM.
     * Tenant-aware: SIM — {@code tenantId} explicito.
     */
    boolean possuiBaseline(UUID atletaId, UUID tenantId);

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

    /**
     * Salva (cria ou atualiza) o rascunho de onboarding — retrofit 10.3, substitui a Decisao 10
     * original que escrevia os campos espelhados direto em {@code Atleta}. NUNCA toca
     * {@code Atleta}; todo o formulario, enquanto em {@code RASCUNHO}, vive apenas em
     * {@code PerfilOnboardingAtleta} (CA8 — retomar onboarding interrompido).
     *
     * Idempotente: SIM — upsert por atleta+tenant, chamadas repetidas sobrescrevem o mesmo
     * rascunho.
     * Efeitos colaterais: persiste/atualiza {@code PerfilOnboardingAtleta} com status
     * {@code RASCUNHO} (nao altera o status se ja estiver {@code COMPLETO}).
     * Tenant-aware: SIM — {@code tenantId} explicito.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta nao existir no tenant
     */
    PerfilOnboardingAtleta salvarRascunho(UUID atletaId, UUID tenantId, OnboardingDraftInput input);

    /**
     * Conclui o onboarding: migra os campos espelhados do rascunho para {@code Atleta} e marca
     * o perfil como {@code COMPLETO}, numa unica transacao (retrofit 10.3, ADR-0002).
     *
     * <p>Checagem de conflito: se {@code Atleta.updatedAt} for posterior ao {@code criadoEm} do
     * rascunho, alguem editou o atleta diretamente (ex.: coach via CRUD generico) depois que o
     * rascunho comecou — lanca {@code DomainConflictException} em vez de sobrescrever a edicao
     * concorrente.
     *
     * Idempotente: NAO — muda o status do perfil e os campos do atleta a cada chamada
     * (chamar de novo apos concluido re-executa a migracao, sobrescrevendo os campos com o
     * mesmo rascunho).
     * Efeitos colaterais: persiste {@code Atleta} (7 campos migrados) + {@code PerfilOnboardingAtleta}
     * (status -&gt; COMPLETO).
     * Tenant-aware: SIM — {@code tenantId} explicito.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta ou o rascunho nao existirem no tenant
     * @throws br.com.menthoros.backend.exception.DomainConflictException se {@code Atleta} foi editado depois do inicio do rascunho
     */
    PerfilOnboardingAtleta concluirOnboarding(UUID atletaId, UUID tenantId);

    /**
     * Avalia a semana de calibracao do atleta, se ele estiver em
     * {@code TrainingPhase.CALIBRATION} (retrofit 10.4, sessao de grilling 2026-07-21) — entrada
     * na fase e disparada em {@code montarContexto} na 1a vez que {@code confidenceTier != A}
     * (persistido em {@code AthleteBaselineState.calibracaoIniciadaEm}); este metodo so avalia
     * quando esse campo esta preenchido, e limpa-o quando
     * {@code CalibrationEvaluation.elegivelParaSairDaCalibracao()} for {@code true}. Chamado a
     * cada geracao de plano ({@code PlanoServiceImpl.persistirPlanoCompleto}), mesmo ponto onde
     * o shadow do {@code PlannerEngine} roda — "uma semana de calibracao" = um ciclo de
     * {@code gerarPlanoTreino}, sem scheduler novo.
     *
     * Idempotente: NAO — pode limpar {@code calibracaoIniciadaEm} e publicar
     * {@code AtletaPresoEmCalibracaoEvent} (via {@link CalibrationService}).
     * Efeitos colaterais: atualiza {@code AthleteBaselineState} quando sai da calibracao; le
     * {@code TreinoRealizado}/{@code PerfilOnboardingAtleta}/{@code Atleta}.
     * Tenant-aware: SIM — {@code tenantId} explicito.
     *
     * @return vazio quando o atleta nao esta em calibracao (campo nao setado)
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta nao existir no tenant
     */
    Optional<CalibrationEvaluation> avaliarCalibracaoSeAplicavel(
            UUID atletaId, UUID tenantId, LocalDate dataReferenciaSemana, InjuryRiskLevel injuryRiskLevel);
}
