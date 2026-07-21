package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.InjuryRiskLevel;
import br.com.menthoros.backend.domain.planner.OnboardingContext;
import br.com.menthoros.backend.domain.planner.PlanningPolicy;
import br.com.menthoros.backend.domain.planner.ReviewMode;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.AthleteBaselineHistory;
import br.com.menthoros.backend.entity.AthleteBaselineState;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.CanalIntegracao;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DispositivoMarca;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.AthleteBaselineHistoryRepository;
import br.com.menthoros.backend.repository.AthleteBaselineStateRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    @Mock private AthleteBaselineStateRepository athleteBaselineStateRepository;
    @Mock private AthleteBaselineHistoryRepository athleteBaselineHistoryRepository;
    @Mock private ProvaRepository provaRepository;
    @Mock private ActivityNormalizer activityNormalizer;
    @Mock private ActivityDedupService activityDedupService;
    @Mock private BaselineCalculator baselineCalculator;
    @Mock private ConfidenceScorer confidenceScorer;
    @Mock private PlanningPolicyResolver planningPolicyResolver;
    @Mock private CalibrationService calibrationService;

    private OnboardingServiceImpl service;

    private UUID atletaId;
    private UUID tenantId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        service = new OnboardingServiceImpl(
                atletaRepository, treinoRealizadoRepository, perfilOnboardingAtletaRepository,
                athleteBaselineStateRepository, athleteBaselineHistoryRepository, provaRepository,
                activityNormalizer, activityDedupService,
                baselineCalculator, confidenceScorer, planningPolicyResolver, calibrationService);
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
            when(athleteBaselineStateRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            OnboardingContext contexto = service.montarContexto(atletaId, tenantId);

            assertThat(contexto.baseline().ctlEstimado()).isEqualTo(50.0);
            assertThat(contexto.confidenceScore()).isEqualTo(0.80); // normalizado 0-100 -> 0.0-1.0
            assertThat(contexto.planningPolicy().reviewMode()).isEqualTo(ReviewMode.EXCEPTION_ONLY);
            verify(activityDedupService).deduplicar(List.of(normalizada), tenantId);
        }

        @Test
        @DisplayName("persiste AthleteBaselineState (cria quando nao existe)")
        void persisteSnapshotNovo() {
            stubFluxoMinimo();
            when(athleteBaselineStateRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            service.montarContexto(atletaId, tenantId);

            ArgumentCaptor<AthleteBaselineState> captor = ArgumentCaptor.forClass(AthleteBaselineState.class);
            verify(athleteBaselineStateRepository).save(captor.capture());
            assertThat(captor.getValue().getCtlEstimado()).isEqualTo(50.0);
            assertThat(captor.getValue().getConfidenceScore()).isEqualTo(80);
            assertThat(captor.getValue().getConfidenceTier()).isEqualTo("A");
        }

        @Test
        @DisplayName("persiste AthleteBaselineState (atualiza quando ja existe — upsert)")
        void atualizaSnapshotExistente() {
            stubFluxoMinimo();
            AthleteBaselineState existente = new AthleteBaselineState();
            existente.setId(UUID.randomUUID());
            when(athleteBaselineStateRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(existente));

            service.montarContexto(atletaId, tenantId);

            ArgumentCaptor<AthleteBaselineState> captor = ArgumentCaptor.forClass(AthleteBaselineState.class);
            verify(athleteBaselineStateRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(existente.getId()); // mesma linha, nao duplicou
        }

        @Test
        @DisplayName("grava uma linha nova em AthleteBaselineHistory a cada recalculo (nunca sobrescreve)")
        void gravaLinhaDeHistoricoACadaRecalculo() {
            stubFluxoMinimo();
            when(athleteBaselineStateRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            service.montarContexto(atletaId, tenantId);
            service.montarContexto(atletaId, tenantId);

            ArgumentCaptor<AthleteBaselineHistory> captor = ArgumentCaptor.forClass(AthleteBaselineHistory.class);
            verify(athleteBaselineHistoryRepository, times(2)).save(captor.capture());
            for (AthleteBaselineHistory linha : captor.getAllValues()) {
                assertThat(linha.getAtletaId()).isEqualTo(atletaId);
                assertThat(linha.getTenantId()).isEqualTo(tenantId);
                assertThat(linha.getEvento()).isEqualTo("RECALCULO_ONBOARDING_CONTEXT");
                assertThat(linha.getCtlEstimado()).isEqualTo(50.0);
                assertThat(linha.getConfidenceScore()).isEqualTo(80);
            }
        }

        @Test
        @DisplayName("onboardingCompleto reflete o status do PerfilOnboardingAtleta quando existe")
        void onboardingCompletoReflitaPerfil() {
            stubFluxoMinimo();
            PerfilOnboardingAtleta perfil = new PerfilOnboardingAtleta();
            perfil.setStatus("COMPLETO");
            perfil.setPreenchidoPorCoach(true);
            when(perfilOnboardingAtletaRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(perfil));
            when(athleteBaselineStateRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

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
            when(athleteBaselineStateRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

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

    @Nested
    @DisplayName("salvarRascunho")
    class SalvarRascunho {

        @Test
        @DisplayName("cria novo PerfilOnboardingAtleta sem tocar Atleta")
        void criaNovoRascunhoSemTocarAtleta() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(perfilOnboardingAtletaRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());
            when(perfilOnboardingAtletaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PerfilOnboardingAtleta salvo = service.salvarRascunho(atletaId, tenantId, draftInput());

            assertThat(salvo.getObjetivo()).isEqualTo("Correr uma maratona");
            assertThat(salvo.getNivelExperiencia()).isEqualTo(NivelExperiencia.INTERMEDIARIO);
            assertThat(salvo.getDiasDisponiveis()).containsExactly(DiaSemana.SEGUNDA, DiaSemana.QUARTA);
            assertThat(salvo.getVolumeSemanalMax()).isEqualTo(40);
            assertThat(salvo.getTemLesao()).isFalse();
            verify(atletaRepository, org.mockito.Mockito.never()).save(any());
        }

        @Test
        @DisplayName("atualiza rascunho existente (upsert — nao duplica)")
        void atualizaRascunhoExistente() {
            PerfilOnboardingAtleta existente = new PerfilOnboardingAtleta();
            existente.setId(UUID.randomUUID());
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(perfilOnboardingAtletaRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(existente));
            when(perfilOnboardingAtletaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PerfilOnboardingAtleta salvo = service.salvarRascunho(atletaId, tenantId, draftInput());

            assertThat(salvo.getId()).isEqualTo(existente.getId());
            assertThat(salvo.getObjetivo()).isEqualTo("Correr uma maratona");
        }

        @Test
        @DisplayName("lanca DomainNotFoundException quando atleta nao existe no tenant")
        void lancaExcecaoQuandoAtletaNaoExiste() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.salvarRascunho(atletaId, tenantId, draftInput()))
                    .isInstanceOf(DomainNotFoundException.class);
        }

        @Test
        @DisplayName("lanca IllegalArgumentException quando algum argumento e nulo")
        void lancaExcecaoParaArgumentosNulos() {
            assertThatThrownBy(() -> service.salvarRascunho(null, tenantId, draftInput()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.salvarRascunho(atletaId, null, draftInput()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.salvarRascunho(atletaId, tenantId, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("concluirOnboarding")
    class ConcluirOnboarding {

        @Test
        @DisplayName("migra os campos do rascunho para Atleta e marca perfil COMPLETO, na mesma transacao")
        void migraCamposEMarcaCompleto() {
            Instant agora = Instant.now();
            atleta.setUpdatedAt(LocalDateTime.now().minusDays(2));
            PerfilOnboardingAtleta perfil = rascunhoPreenchido(agora.minusSeconds(3600));

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(perfilOnboardingAtletaRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(perfil));
            when(atletaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(perfilOnboardingAtletaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PerfilOnboardingAtleta resultado = service.concluirOnboarding(atletaId, tenantId);

            assertThat(resultado.getStatus()).isEqualTo("COMPLETO");
            ArgumentCaptor<Atleta> captor = ArgumentCaptor.forClass(Atleta.class);
            verify(atletaRepository).save(captor.capture());
            assertThat(captor.getValue().getObjetivo()).isEqualTo("Correr uma maratona");
            assertThat(captor.getValue().getNivelExperiencia()).isEqualTo(NivelExperiencia.INTERMEDIARIO);
            assertThat(captor.getValue().getVolumeSemanalMax()).isEqualTo(40);
        }

        @Test
        @DisplayName("lanca DomainConflictException quando Atleta foi editado depois do inicio do rascunho")
        void lancaConflitoQuandoAtletaEditadoDepoisDoRascunho() {
            Instant inicioRascunho = Instant.now().minusSeconds(3600);
            atleta.setUpdatedAt(LocalDateTime.ofInstant(inicioRascunho.plusSeconds(60), ZoneId.systemDefault()));
            PerfilOnboardingAtleta perfil = rascunhoPreenchido(inicioRascunho);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(perfilOnboardingAtletaRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(perfil));

            assertThatThrownBy(() -> service.concluirOnboarding(atletaId, tenantId))
                    .isInstanceOf(DomainConflictException.class);
            verify(atletaRepository, org.mockito.Mockito.never()).save(any());
            verify(perfilOnboardingAtletaRepository, org.mockito.Mockito.never()).save(any());
        }

        @Test
        @DisplayName("lanca DomainNotFoundException quando rascunho nao existe")
        void lancaExcecaoQuandoRascunhoNaoExiste() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(perfilOnboardingAtletaRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.concluirOnboarding(atletaId, tenantId))
                    .isInstanceOf(DomainNotFoundException.class);
        }

        private PerfilOnboardingAtleta rascunhoPreenchido(Instant criadoEm) {
            PerfilOnboardingAtleta perfil = new PerfilOnboardingAtleta();
            perfil.setId(UUID.randomUUID());
            perfil.setCriadoEm(criadoEm);
            perfil.setObjetivo("Correr uma maratona");
            perfil.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
            perfil.setDiasDisponiveis(List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA));
            perfil.setVolumeSemanalMax(40);
            perfil.setTemLesao(false);
            return perfil;
        }
    }

    @Nested
    @DisplayName("avaliarCalibracaoSeAplicavel")
    class AvaliarCalibracaoSeAplicavel {

        @Test
        @DisplayName("retorna vazio quando o atleta nao esta em calibracao (calibracaoIniciadaEm nulo)")
        void retornaVazioQuandoNaoEstaEmCalibracao() {
            AthleteBaselineState estado = new AthleteBaselineState();
            when(athleteBaselineStateRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(estado));

            Optional<CalibrationEvaluation> resultado = service.avaliarCalibracaoSeAplicavel(
                    atletaId, tenantId, LocalDate.now(), InjuryRiskLevel.SAFE);

            assertThat(resultado).isEmpty();
            org.mockito.Mockito.verifyNoInteractions(calibrationService);
        }

        @Test
        @DisplayName("retorna vazio quando nao ha AthleteBaselineState (atleta nunca passou pelo onboarding)")
        void retornaVazioSemBaseline() {
            when(athleteBaselineStateRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            Optional<CalibrationEvaluation> resultado = service.avaliarCalibracaoSeAplicavel(
                    atletaId, tenantId, LocalDate.now(), InjuryRiskLevel.SAFE);

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("avalia a semana e mantem calibracaoIniciadaEm quando ainda nao elegivel para sair")
        void avaliaSemanaSemSairDaCalibracao() {
            Instant inicioCalibracao = LocalDate.now().minusWeeks(2).atStartOfDay(ZoneId.systemDefault()).toInstant();
            AthleteBaselineState estado = new AthleteBaselineState();
            estado.setCalibracaoIniciadaEm(inicioCalibracao);
            when(athleteBaselineStateRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(estado));
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(perfilOnboardingAtletaRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(List.of());
            when(activityDedupService.deduplicar(List.of(), tenantId)).thenReturn(List.of());
            CalibrationEvaluation evaluation = new CalibrationEvaluation(
                    CalibrationStage.STABILIZATION,
                    new BaselineResult(50, OrigemDado.MEASURED, 45, OrigemDado.MEASURED, 5, OrigemDado.MEASURED),
                    new ConfidenceScoreResult(40, ConfidenceTier.B, false),
                    false);
            when(calibrationService.avaliarSemana(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), any(), any(), any()))
                    .thenReturn(evaluation);

            Optional<CalibrationEvaluation> resultado = service.avaliarCalibracaoSeAplicavel(
                    atletaId, tenantId, LocalDate.now(), InjuryRiskLevel.SAFE);

            assertThat(resultado).contains(evaluation);
            org.mockito.Mockito.verify(athleteBaselineStateRepository, org.mockito.Mockito.never()).save(any());
            assertThat(estado.getCalibracaoIniciadaEm()).isEqualTo(inicioCalibracao);
        }

        @Test
        @DisplayName("limpa calibracaoIniciadaEm quando elegivel para sair da calibracao")
        void limpaCalibracaoQuandoElegivel() {
            Instant inicioCalibracao = LocalDate.now().minusWeeks(3).atStartOfDay(ZoneId.systemDefault()).toInstant();
            AthleteBaselineState estado = new AthleteBaselineState();
            estado.setCalibracaoIniciadaEm(inicioCalibracao);
            when(athleteBaselineStateRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(estado));
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(perfilOnboardingAtletaRepository.findByAtletaIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId)).thenReturn(List.of());
            when(activityDedupService.deduplicar(List.of(), tenantId)).thenReturn(List.of());
            CalibrationEvaluation evaluation = new CalibrationEvaluation(
                    CalibrationStage.STABILIZATION,
                    new BaselineResult(60, OrigemDado.MEASURED, 50, OrigemDado.MEASURED, 10, OrigemDado.MEASURED),
                    new ConfidenceScoreResult(70, ConfidenceTier.B, false),
                    true);
            when(calibrationService.avaliarSemana(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), any(), any(), any()))
                    .thenReturn(evaluation);
            when(athleteBaselineStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.avaliarCalibracaoSeAplicavel(atletaId, tenantId, LocalDate.now(), InjuryRiskLevel.SAFE);

            ArgumentCaptor<AthleteBaselineState> captor = ArgumentCaptor.forClass(AthleteBaselineState.class);
            verify(athleteBaselineStateRepository).save(captor.capture());
            assertThat(captor.getValue().getCalibracaoIniciadaEm()).isNull();
        }

        @Test
        @DisplayName("lanca IllegalArgumentException quando algum argumento e nulo")
        void lancaExcecaoParaArgumentosNulos() {
            assertThatThrownBy(() -> service.avaliarCalibracaoSeAplicavel(null, tenantId, LocalDate.now(), InjuryRiskLevel.SAFE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.avaliarCalibracaoSeAplicavel(atletaId, tenantId, null, InjuryRiskLevel.SAFE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.avaliarCalibracaoSeAplicavel(atletaId, tenantId, LocalDate.now(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private OnboardingDraftInput draftInput() {
        return new OnboardingDraftInput(
                "Correr uma maratona",
                NivelExperiencia.INTERMEDIARIO,
                List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA),
                40,
                false,
                null,
                null,
                null,
                null,
                60,
                null,
                "CORRIDA",
                "BOA",
                false,
                CanalIntegracao.INTERVALS_ICU,
                DispositivoMarca.GARMIN,
                null
        );
    }

    private NormalizedActivity normalizedActivity() {
        return new NormalizedActivity(
                UUID.randomUUID(), "a1", atletaId, LocalDate.now(), Sport.RUNNING,
                45, 10.0, 150, 170, Duration.ofSeconds(270), null, 6,
                br.com.menthoros.backend.enums.FonteDados.GARMIN, 0.9);
    }
}
