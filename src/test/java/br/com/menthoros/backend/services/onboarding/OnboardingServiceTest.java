package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.OnboardingContext;
import br.com.menthoros.backend.domain.planner.PlanningPolicy;
import br.com.menthoros.backend.domain.planner.ReviewMode;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.AthleteBaselineSnapshot;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.AthleteBaselineSnapshotRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PerfilOnboardingAtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.onboarding.impl.OnboardingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private PerfilOnboardingAtletaRepository perfilOnboardingAtletaRepository;
    @Mock private AthleteBaselineSnapshotRepository athleteBaselineSnapshotRepository;
    @Mock private ActivityNormalizer activityNormalizer;
    @Mock private ActivityDedupService activityDedupService;
    @Mock private BaselineCalculator baselineCalculator;
    @Mock private ConfidenceScorer confidenceScorer;
    @Mock private PlanningPolicyResolver planningPolicyResolver;

    private OnboardingServiceImpl service;

    private UUID atletaId;
    private UUID tenantId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        service = new OnboardingServiceImpl(
                atletaRepository, treinoRealizadoRepository, perfilOnboardingAtletaRepository,
                athleteBaselineSnapshotRepository, activityNormalizer, activityDedupService,
                baselineCalculator, confidenceScorer, planningPolicyResolver);
        atletaId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        atleta = Atleta.builder().id(atletaId).nome("João").nivelExperiencia(NivelExperiencia.INTERMEDIARIO).build();
    }

    @Nested
    @DisplayName("montarContexto")
    class MontarContexto {

        @Test
        @DisplayName("orquestra Normalizer -> Dedup -> BaselineCalculator -> ConfidenceScorer -> PlanningPolicyResolver")
        void orquestraFluxoCompleto() {
            TreinoRealizado treino = new TreinoRealizado();
            treino.setId(UUID.randomUUID());
            NormalizedActivity normalizada = normalizedActivity();

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(perfilOnboardingAtletaRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(List.of(treino));
            when(activityNormalizer.toCanonical(treino)).thenReturn(normalizada);
            when(activityDedupService.deduplicar(List.of(normalizada), tenantId)).thenReturn(List.of(normalizada));
            when(baselineCalculator.calcular(atletaId, NivelExperiencia.INTERMEDIARIO, List.of(normalizada)))
                    .thenReturn(new BaselineResult(50, OrigemDado.MEASURED, 45, OrigemDado.MEASURED, 5, OrigemDado.MEASURED));
            when(confidenceScorer.calcular(any())).thenReturn(new ConfidenceScoreResult(80, ConfidenceTier.A, false));
            when(planningPolicyResolver.resolver(ConfidenceTier.A))
                    .thenReturn(new PlanningPolicy(ReviewMode.EXCEPTION_ONLY, 1.0, true));
            when(athleteBaselineSnapshotRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            OnboardingContext contexto = service.montarContexto(atletaId, tenantId);

            assertThat(contexto.baseline().ctlEstimado()).isEqualTo(50.0);
            assertThat(contexto.confidenceScore()).isEqualTo(0.80); // normalizado 0-100 -> 0.0-1.0
            assertThat(contexto.planningPolicy().reviewMode()).isEqualTo(ReviewMode.EXCEPTION_ONLY);
            verify(activityDedupService).deduplicar(List.of(normalizada), tenantId);
        }

        @Test
        @DisplayName("persiste AthleteBaselineSnapshot (cria quando nao existe)")
        void persisteSnapshotNovo() {
            stubFluxoMinimo();
            when(athleteBaselineSnapshotRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            service.montarContexto(atletaId, tenantId);

            ArgumentCaptor<AthleteBaselineSnapshot> captor = ArgumentCaptor.forClass(AthleteBaselineSnapshot.class);
            verify(athleteBaselineSnapshotRepository).save(captor.capture());
            assertThat(captor.getValue().getCtlEstimado()).isEqualTo(50.0);
            assertThat(captor.getValue().getConfidenceScore()).isEqualTo(80);
            assertThat(captor.getValue().getConfidenceTier()).isEqualTo("A");
        }

        @Test
        @DisplayName("persiste AthleteBaselineSnapshot (atualiza quando ja existe — upsert)")
        void atualizaSnapshotExistente() {
            stubFluxoMinimo();
            AthleteBaselineSnapshot existente = new AthleteBaselineSnapshot();
            existente.setId(UUID.randomUUID());
            when(athleteBaselineSnapshotRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(existente));

            service.montarContexto(atletaId, tenantId);

            ArgumentCaptor<AthleteBaselineSnapshot> captor = ArgumentCaptor.forClass(AthleteBaselineSnapshot.class);
            verify(athleteBaselineSnapshotRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(existente.getId()); // mesma linha, nao duplicou
        }

        @Test
        @DisplayName("onboardingCompleto reflete o status do PerfilOnboardingAtleta quando existe")
        void onboardingCompletoReflitaPerfil() {
            stubFluxoMinimo();
            PerfilOnboardingAtleta perfil = new PerfilOnboardingAtleta();
            perfil.setStatus("COMPLETO");
            perfil.setPreenchidoPorCoach(true);
            when(perfilOnboardingAtletaRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(perfil));
            when(athleteBaselineSnapshotRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            service.montarContexto(atletaId, tenantId);

            ArgumentCaptor<ConfidenceScorerInput> captor = ArgumentCaptor.forClass(ConfidenceScorerInput.class);
            verify(confidenceScorer).calcular(captor.capture());
            assertThat(captor.getValue().onboardingCompleto()).isTrue();
            assertThat(captor.getValue().preenchidoPorCoach()).isTrue();
        }

        @Test
        @DisplayName("atleta legado sem PerfilOnboardingAtleta: onboardingCompleto=false, sem quebrar")
        void atletaLegadoSemPerfil() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(perfilOnboardingAtletaRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(List.of());
            when(activityDedupService.deduplicar(List.of(), tenantId)).thenReturn(List.of());
            when(baselineCalculator.calcular(any(), any(), any()))
                    .thenReturn(new BaselineResult(25, OrigemDado.ESTIMATED, 25, OrigemDado.ESTIMATED, 0, OrigemDado.ESTIMATED));
            when(confidenceScorer.calcular(any())).thenReturn(new ConfidenceScoreResult(0, ConfidenceTier.C, false));
            when(planningPolicyResolver.resolver(ConfidenceTier.C))
                    .thenReturn(new PlanningPolicy(ReviewMode.MANDATORY_BLOCKING, 0.0, true));
            when(athleteBaselineSnapshotRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            OnboardingContext contexto = service.montarContexto(atletaId, tenantId);

            assertThat(contexto).isNotNull();
            assertThat(contexto.planningPolicy().reviewMode()).isEqualTo(ReviewMode.MANDATORY_BLOCKING);
        }

        @Test
        @DisplayName("lanca DomainNotFoundException quando atleta nao existe no tenant")
        void lancaExcecaoQuandoAtletaNaoExiste() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.montarContexto(atletaId, tenantId))
                    .isInstanceOf(DomainNotFoundException.class);
        }

        private void stubFluxoMinimo() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(perfilOnboardingAtletaRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(List.of());
            when(activityDedupService.deduplicar(List.of(), tenantId)).thenReturn(List.of());
            when(baselineCalculator.calcular(any(), any(), any()))
                    .thenReturn(new BaselineResult(50, OrigemDado.MEASURED, 45, OrigemDado.MEASURED, 5, OrigemDado.MEASURED));
            when(confidenceScorer.calcular(any())).thenReturn(new ConfidenceScoreResult(80, ConfidenceTier.A, false));
            when(planningPolicyResolver.resolver(ConfidenceTier.A))
                    .thenReturn(new PlanningPolicy(ReviewMode.EXCEPTION_ONLY, 1.0, true));
        }
    }

    private NormalizedActivity normalizedActivity() {
        return new NormalizedActivity(
                UUID.randomUUID(), "a1", atletaId, LocalDate.now(), Sport.RUNNING,
                45, 10.0, 150, 170, Duration.ofSeconds(270), null, 6,
                br.com.menthoros.backend.enums.FonteDados.GARMIN, 0.9);
    }
}
