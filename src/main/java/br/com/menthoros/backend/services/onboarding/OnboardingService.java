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
     * <p>{@code chamadorEhCoach} (task 6.0.1/6.0.5): quando {@code false}, valida que
     * {@code atletaId} pertence ao proprio chamador (via {@code AtletaProgressService}) — atleta
     * so pode editar o proprio rascunho. TECNICO/ADMIN (coach-como-proxy, Decisao 3) tem acesso
     * irrestrito dentro do tenant, ja garantido por {@code @RequireTenant} no controller.
     *
     * Idempotente: SIM — upsert por atleta+tenant, chamadas repetidas sobrescrevem o mesmo
     * rascunho.
     * Efeitos colaterais: persiste/atualiza {@code PerfilOnboardingAtleta} com status
     * {@code RASCUNHO} (nao altera o status se ja estiver {@code COMPLETO}).
     * Tenant-aware: SIM — {@code tenantId} explicito.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta nao existir no tenant
     * @throws br.com.menthoros.backend.exception.AccessDeniedException se o chamador (atleta) tentar editar o rascunho de outro atleta
     */
    PerfilOnboardingAtleta salvarRascunho(UUID atletaId, UUID tenantId, OnboardingDraftInput input, boolean chamadorEhCoach);

    /**
     * Recupera o rascunho salvo (task 6.0.2, CA8 — retomar onboarding interrompido). Mesmo
     * controle de acesso de {@link #salvarRascunho}.
     *
     * Idempotente: SIM — leitura pura.
     * Efeitos colaterais: NENHUM.
     * Tenant-aware: SIM — {@code tenantId} explicito.
     *
     * @return vazio quando o atleta ainda nao iniciou o onboarding
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta nao existir no tenant
     * @throws br.com.menthoros.backend.exception.AccessDeniedException se o chamador (atleta) tentar ler o rascunho de outro atleta
     */
    Optional<PerfilOnboardingAtleta> buscarRascunho(UUID atletaId, UUID tenantId, boolean chamadorEhCoach);

    /**
     * Conclui o onboarding (task 6.0.3): migra os campos espelhados do rascunho para
     * {@code Atleta} e marca o perfil como {@code COMPLETO} numa unica transacao (retrofit 10.3,
     * ADR-0002); em seguida cria/atualiza a {@code Prova} alvo a partir de {@code dataProva}
     * (CA13, Decisao 8) e calcula o {@code OnboardingContext} inicial (baseline + score + tier,
     * via {@link #montarContexto}).
     *
     * <p>Checagem de conflito: se {@code Atleta.updatedAt} for posterior ao {@code atualizadoEm} do
     * rascunho, alguem editou o atleta diretamente (ex.: coach via CRUD generico) depois da ultima
     * vez que o rascunho foi salvo — lanca {@code DomainConflictException} em vez de sobrescrever a
     * edicao concorrente. Usar {@code atualizadoEm} (nao {@code criadoEm}) e o que faz o conflito
     * "curar" quando o atleta revisita e salva o rascunho de novo apos a edicao direta (CA8 —
     * onboarding em multiplas sessoes; correcao QA 2026-07-21, achado do clean-code-reviewer).
     *
     * Idempotente: NAO — muda o status do perfil e os campos do atleta a cada chamada
     * (chamar de novo apos concluido re-executa a migracao, sobrescrevendo os campos com o
     * mesmo rascunho, e recalcula baseline/score/prova).
     * Efeitos colaterais: persiste {@code Atleta} (7 campos migrados) + {@code PerfilOnboardingAtleta}
     * (status -&gt; COMPLETO) + {@code Prova} (cria/atualiza prova-alvo) + {@code AthleteBaselineState}/
     * {@code AthleteBaselineHistory} (via {@code montarContexto}).
     * Tenant-aware: SIM — {@code tenantId} explicito.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta ou o rascunho nao existirem no tenant
     * @throws br.com.menthoros.backend.exception.DomainConflictException se {@code Atleta} foi editado depois do inicio do rascunho
     * @throws br.com.menthoros.backend.exception.AccessDeniedException se o chamador (atleta) tentar concluir o onboarding de outro atleta
     */
    OnboardingConclusionResult concluirOnboarding(UUID atletaId, UUID tenantId, boolean chamadorEhCoach,
            LocalDate dataProva, TipoProva tipoProva, DistanciaProva distancia, BigDecimal distanciaKm, String nomeProva);

    /**
     * Status de calibracao para o {@code CalibrationBanner} do front (task 6.0.4). Mesmo
     * controle de acesso de {@link #salvarRascunho}.
     *
     * Idempotente: SIM — leitura pura, nao avalia nem muda o estado de calibracao (isso e feito
     * por {@link #avaliarCalibracaoSeAplicavel}, chamado na geracao de plano).
     * Efeitos colaterais: NENHUM.
     * Tenant-aware: SIM — {@code tenantId} explicito.
     *
     * @return vazio quando o atleta nao esta em {@code TrainingPhase.CALIBRATION}
     * @throws br.com.menthoros.backend.exception.AccessDeniedException se o chamador (atleta) tentar ler o status de outro atleta
     */
    Optional<CalibrationStatusResult> obterStatusCalibracao(UUID atletaId, UUID tenantId, boolean chamadorEhCoach);

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
