package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.OnboardingContext;
import br.com.menthoros.backend.domain.planner.PlanningPolicy;
import br.com.menthoros.backend.domain.planner.ReviewMode;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.AthleteBaselineSnapshot;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.AthleteBaselineSnapshotRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PerfilOnboardingAtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private PerfilOnboardingAtletaRepository perfilOnboardingAtletaRepository;
    @Mock private AthleteBaselineSnapshotRepository athleteBaselineSnapshotRepository;
    @Mock private ProvaRepository provaRepository;
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
                athleteBaselineSnapshotRepository, provaRepository, activityNormalizer, activityDedupService,
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

    @Nested
    @DisplayName("criarOuAtualizarProvaAlvo")
    class CriarOuAtualizarProvaAlvo {

        private Atleta atletaComAssessoria;

        @BeforeEach
        void setUpAtleta() {
            atletaComAssessoria = Atleta.builder().id(atletaId).nome("João")
                    .assessoria(Assessoria.builder().id(tenantId).build())
                    .build();
            org.mockito.Mockito.lenient().when(atletaRepository.findByIdAndTenantId(atletaId, tenantId))
                    .thenReturn(Optional.of(atletaComAssessoria));
        }

        @Test
        @DisplayName("cria nova Prova quando o atleta nao tem prova-alvo")
        void criaNovaProvaQuandoNaoHaProvaAlvo() {
            LocalDate dataProva = LocalDate.now().plusMonths(3);
            when(provaRepository.findByAtletaAndProvaAlvoTrue(atletaComAssessoria)).thenReturn(List.of());
            when(provaRepository.save(any(Prova.class))).thenAnswer(inv -> {
                Prova p = inv.getArgument(0);
                if (p.getId() == null) p.setId(UUID.randomUUID());
                return p;
            });

            Prova resultado = service.criarOuAtualizarProvaAlvo(
                    atletaId, tenantId, dataProva, TipoProva.CORRIDA_RUA, DistanciaProva.KM_21, null, "Meia SP");

            assertThat(resultado.isProvaAlvo()).isTrue();
            assertThat(resultado.getDataProva()).isEqualTo(dataProva);
            assertThat(resultado.getDistancia()).isEqualTo(DistanciaProva.KM_21);
            assertThat(resultado.getNomeProva()).isEqualTo("Meia SP");
            assertThat(resultado.getAtleta()).isEqualTo(atletaComAssessoria);
            verify(provaRepository).save(any(Prova.class));
        }

        @Test
        @DisplayName("atualiza a Prova existente quando dataProva/distancia coincidem com a prova-alvo atual")
        void atualizaProvaExistenteQuandoCoincide() {
            LocalDate dataProva = LocalDate.now().plusMonths(3);
            Prova existente = Prova.builder()
                    .id(UUID.randomUUID()).dataProva(dataProva).distancia(DistanciaProva.KM_21)
                    .provaAlvo(true).nomeProva("Nome antigo").build();
            when(provaRepository.findByAtletaAndProvaAlvoTrue(atletaComAssessoria)).thenReturn(List.of(existente));
            when(provaRepository.save(any(Prova.class))).thenAnswer(inv -> inv.getArgument(0));

            Prova resultado = service.criarOuAtualizarProvaAlvo(
                    atletaId, tenantId, dataProva, TipoProva.CORRIDA_RUA, DistanciaProva.KM_21, null, "Nome novo");

            assertThat(resultado.getId()).isEqualTo(existente.getId());
            assertThat(resultado.getNomeProva()).isEqualTo("Nome novo");
            verify(provaRepository, times(1)).save(any(Prova.class)); // so a propria prova, nao ha outra pra desmarcar
        }

        @Test
        @DisplayName("desmarca provaAlvo de outra Prova quando dataProva/distancia mudam (unicidade, pre-mortem rodada 2)")
        void desmarcaOutraProvaAlvoQuandoMudaDataOuDistancia() {
            LocalDate dataAntiga = LocalDate.now().plusMonths(1);
            LocalDate dataNova = LocalDate.now().plusMonths(4);
            Prova provaAntiga = Prova.builder()
                    .id(UUID.randomUUID()).dataProva(dataAntiga).distancia(DistanciaProva.KM_10)
                    .provaAlvo(true).nomeProva("Prova antiga").build();
            when(provaRepository.findByAtletaAndProvaAlvoTrue(atletaComAssessoria)).thenReturn(List.of(provaAntiga));
            when(provaRepository.save(any(Prova.class))).thenAnswer(inv -> {
                Prova p = inv.getArgument(0);
                if (p.getId() == null) p.setId(UUID.randomUUID());
                return p;
            });

            Prova resultado = service.criarOuAtualizarProvaAlvo(
                    atletaId, tenantId, dataNova, TipoProva.MARATONA, DistanciaProva.KM_42, null, "Maratona nova");

            assertThat(resultado.getId()).isNotEqualTo(provaAntiga.getId());
            assertThat(provaAntiga.isProvaAlvo()).isFalse(); // desmarcada na mesma chamada

            ArgumentCaptor<Prova> captor = ArgumentCaptor.forClass(Prova.class);
            verify(provaRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues()).extracting(Prova::isProvaAlvo).containsExactly(true, false);
        }

        @Test
        @DisplayName("lanca DomainNotFoundException quando atleta nao existe no tenant")
        void lancaExcecaoQuandoAtletaNaoExiste() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.criarOuAtualizarProvaAlvo(
                    atletaId, tenantId, LocalDate.now(), TipoProva.CORRIDA_RUA, DistanciaProva.KM_10, null, "X"))
                    .isInstanceOf(DomainNotFoundException.class);
        }

        @Test
        @DisplayName("lanca IllegalArgumentException quando campos obrigatorios sao nulos")
        void lancaExcecaoParaCamposObrigatoriosNulos() {
            assertThatThrownBy(() -> service.criarOuAtualizarProvaAlvo(
                    null, tenantId, LocalDate.now(), TipoProva.CORRIDA_RUA, DistanciaProva.KM_10, null, "X"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private NormalizedActivity normalizedActivity() {
        return new NormalizedActivity(
                UUID.randomUUID(), "a1", atletaId, LocalDate.now(), Sport.RUNNING,
                45, 10.0, 150, 170, Duration.ofSeconds(270), null, 6,
                br.com.menthoros.backend.enums.FonteDados.GARMIN, 0.9);
    }
}
