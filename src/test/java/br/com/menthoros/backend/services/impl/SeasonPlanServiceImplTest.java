package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.services.helper.SeasonPlanResponseMapper;
import br.com.menthoros.backend.services.helper.SeasonPlanVersioning;

import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.AthleteBaselineState;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.WeekValueSource;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.SeasonPlanDisabledException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AthleteBaselineStateRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PerfilOnboardingAtletaRepository;
import br.com.menthoros.backend.repository.PlanoTreinoRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.SeasonWeekRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedAtletaResolver;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.SeasonPlanService.Generation;
import br.com.menthoros.backend.dto.output.AthleteSeasonPlanDto;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.ProvaStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeasonPlanServiceImplTest {

    /** 2026-11-16 (segunda) 12:00 UTC = 09:00 em São Paulo. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T12:00:00.123456789Z"), ZoneOffset.UTC);
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 16);
    private static final LocalDate PROVA_20_SEMANAS = LocalDate.of(2027, 4, 4);

    @Mock private AtletaRepository atletaRepository;
    @Mock private ProvaRepository provaRepository;
    @Mock private PlanoTreinoRepository planoTreinoRepository;
    @Mock private SeasonWeekRepository seasonWeekRepository;
    @Mock private MetricasDiariasRepository metricasDiariasRepository;
    @Mock private AthleteBaselineStateRepository baselineRepository;
    @Mock private PerfilOnboardingAtletaRepository perfilRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuthenticatedPrincipalResolver principalResolver;
    @Mock private AuthenticatedAtletaResolver atletaResolver;
    @Captor private ArgumentCaptor<List<SeasonWeek>> semanasCaptor;

    private SeasonPlanServiceImpl service;
    private UUID tenantId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);
        service = novo(true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("flag desligada responde SeasonPlanDisabledException sem tocar no banco")
        void flagDesligada() {
            service = novo(false);

            assertThatThrownBy(() -> service.generate(atleta.getId())).isInstanceOf(SeasonPlanDisabledException.class);
            verifyNoInteractions(atletaRepository, provaRepository, planoTreinoRepository);
        }

        @Test
        @DisplayName("atleta de outra assessoria vira não encontrado")
        void atletaForaDoTenant() {
            when(atletaRepository.findByIdAndTenantId(atleta.getId(), tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(atleta.getId())).isInstanceOf(DomainNotFoundException.class);
        }

        @Test
        @DisplayName("atleta sem prova-alvo responde 422")
        void semProvaAlvo() {
            atletaExiste();
            when(provaRepository.findTargetRaceIds(atleta.getId(), tenantId)).thenReturn(List.of());

            assertThatThrownBy(() -> service.generate(atleta.getId()))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("prova-alvo");
        }

        @Test
        @DisplayName("prova desmarcada entre a busca e o lock responde 422")
        void desmarcadaAntesDoLock() {
            atletaExiste();
            Prova prova = provaAlvo(PROVA_20_SEMANAS);
            prova.setProvaAlvo(false);
            provaTravada(prova);

            assertThatThrownBy(() -> service.generate(atleta.getId()))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("prova-alvo");
            verify(planoTreinoRepository, never()).save(any());
        }

        @Test
        @DisplayName("prova cancelada entre a busca e o lock responde 422 (cancelar mantém provaAlvo)")
        void canceladaAntesDoLock() {
            atletaExiste();
            Prova prova = provaAlvo(PROVA_20_SEMANAS);
            prova.setStatusProva(br.com.menthoros.backend.enums.ProvaStatus.CANCELADA);
            provaTravada(prova);

            assertThatThrownBy(() -> service.generate(atleta.getId()))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("cancelada");
            verify(planoTreinoRepository, never()).save(any());
        }

        @Test
        @DisplayName("prova sem data responde 422")
        void provaSemData() {
            atletaExiste();
            provaTravada(provaAlvo(null));

            assertThatThrownBy(() -> service.generate(atleta.getId()))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("data");
        }

        @Test
        @DisplayName("prova a menos de 2 semanas (ou passada) responde 422")
        void horizonteCurto() {
            atletaExiste();
            provaTravada(provaAlvo(HOJE.plusDays(6)));

            assertThatThrownBy(() -> service.generate(atleta.getId()))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("2 semanas");
        }

        @Test
        @DisplayName("temporada ACTIVE existente responde 409")
        void ativaExistente() {
            atletaExiste();
            Prova prova = provaTravada(provaAlvo(PROVA_20_SEMANAS));
            abertas(temporada(prova, SeasonPlanStatus.ACTIVE));

            assertThatThrownBy(() -> service.generate(atleta.getId())).isInstanceOf(DomainConflictException.class);
        }

        @Test
        @DisplayName("PROPOSED da mesma prova é devolvida sem gerar outra")
        void propostaMesmaProva() {
            atletaExiste();
            Prova prova = provaTravada(provaAlvo(PROVA_20_SEMANAS));
            PlanoTreino existente = temporada(prova, SeasonPlanStatus.PROPOSED);
            abertas(existente);
            when(seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(existente.getId(), tenantId))
                    .thenReturn(List.of());

            Generation resultado = service.generate(atleta.getId());

            assertThat(resultado.created()).isFalse();
            assertThat(resultado.plan().id()).isEqualTo(existente.getId());
            verify(planoTreinoRepository, never()).save(any());
            verify(seasonWeekRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("PROPOSED de outra prova responde 409")
        void propostaOutraProva() {
            atletaExiste();
            provaTravada(provaAlvo(PROVA_20_SEMANAS));
            abertas(temporada(provaAlvo(PROVA_20_SEMANAS.plusWeeks(4)), SeasonPlanStatus.PROPOSED));

            assertThatThrownBy(() -> service.generate(atleta.getId())).isInstanceOf(DomainConflictException.class);
        }

        @Test
        @DisplayName("gera 20 semanas PROPOSED com nome, objetivo e CTL do PMC projetado com decaimento")
        void geraComPmcDecaido() {
            atletaExiste();
            Prova prova = provaTravada(provaAlvo(PROVA_20_SEMANAS));
            abertas();
            pmc(40.0, HOJE.minusDays(10), HOJE.minusDays(100));
            treinos(treino(HOJE.minusDays(3), "40"), treino(HOJE.minusDays(10), "40"),
                    treino(HOJE.minusDays(17), "40"), treino(HOJE.minusDays(24), "40"));
            when(planoTreinoRepository.save(any(PlanoTreino.class))).thenAnswer(inv -> inv.getArgument(0));

            Generation resultado = service.generate(atleta.getId());

            ArgumentCaptor<PlanoTreino> plano = ArgumentCaptor.forClass(PlanoTreino.class);
            verify(planoTreinoRepository).save(plano.capture());
            PlanoTreino salvo = plano.getValue();
            assertThat(resultado.created()).isTrue();
            assertThat(salvo.getSeasonStatus()).isEqualTo(SeasonPlanStatus.PROPOSED);
            assertThat(salvo.getProva()).isSameAs(prova);
            assertThat(salvo.getNome()).contains("Maratona alvo");
            assertThat(salvo.getDescricao()).contains("Maratona alvo");
            assertThat(salvo.getObjetivo()).contains("Maratona alvo");
            assertThat(salvo.getTotalWeeks()).isEqualTo(20);
            assertThat(salvo.getShortPreparation()).isFalse();
            assertThat(salvo.getInitialCtl()).isCloseTo(40.0 * Math.exp(-10.0 / 42), within(0.01));
            assertThat(salvo.getDataInicio()).isEqualTo(HOJE);
            // add-season-plan-version-diff: o volume de partida fica gravado para o recálculo
            assertThat(salvo.getInitialVolumeKm()).isEqualByComparingTo("40.0");
            assertThat(resultado.plan().initialVolumeKm()).isEqualByComparingTo("40.0");

            List<SeasonPlanResponseDto.Week> semanas = resultado.plan().weeks();
            assertThat(semanas).hasSize(20);
            assertThat(semanas).allSatisfy(s -> assertThat(s.valueSource()).isEqualTo(WeekValueSource.ENGINE));
            // descarga e longão do motor chegam à resposta (T4)
            assertThat(semanas).filteredOn(SeasonPlanResponseDto.Week::deload).isNotEmpty();
            assertThat(semanas.get(0).longRunKm()).isNotNull();
            assertThat(semanas.get(19).longRunKm()).isNull();
            assertThat(resultado.plan().milestones()).isEmpty();
            assertThat(resultado.plan().validations()).isEmpty();
            assertThat(semanas.get(19).phase()).isEqualTo(TrainingPhase.RACE_WEEK);
            assertThat(resultado.plan().peakVolumeKm()).isEqualByComparingTo(salvo.getPeakVolumeKm());
        }

        @Test
        @DisplayName("prova preparatória grava o marco da semana; a alvo não vira marco; candidatas lidas depois do lock")
        void gravaMarcoDaPreparatoria() {
            atletaExiste();
            Prova alvo = provaTravada(provaAlvo(PROVA_20_SEMANAS));
            abertas();
            pmc(40.0, HOJE, HOJE.minusDays(100));
            treinos(treino(HOJE.minusDays(3), "40"));
            Prova meia = provaAlvo(HOJE.plusWeeks(5).plusDays(6));
            meia.setProvaAlvo(false);
            meia.setDistancia(DistanciaProva.KM_21);
            when(provaRepository.findUpcomingByAtletaIdAndTenantId(atleta.getId(), tenantId, HOJE))
                    .thenReturn(List.of(meia, alvo));
            when(planoTreinoRepository.save(any(PlanoTreino.class))).thenAnswer(inv -> inv.getArgument(0));

            service.generate(atleta.getId());

            verify(seasonWeekRepository).saveAll(semanasCaptor.capture());
            assertThat(semanasCaptor.getValue()).filteredOn(w -> w.getMilestoneRaceId() != null)
                    .extracting(SeasonWeek::getWeekIndex, SeasonWeek::getMilestoneRaceId)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(6, meia.getId()));
            InOrder ordem = inOrder(provaRepository);
            ordem.verify(provaRepository).findByIdAndTenantIdForUpdate(alvo.getId(), tenantId);
            ordem.verify(provaRepository).findUpcomingByAtletaIdAndTenantId(atleta.getId(), tenantId, HOJE);
        }

        @Test
        @DisplayName("histórico de PMC com menos de 42 dias usa o ctlEstimado do baseline, limitado a 40")
        void historicoCurtoUsaBaseline() {
            atletaExiste();
            provaTravada(provaAlvo(PROVA_20_SEMANAS));
            abertas();
            pmc(70.0, HOJE.minusDays(1), HOJE.minusDays(20));
            baseline(55.0);
            treinos(treino(HOJE.minusDays(3), "30"));
            when(planoTreinoRepository.save(any(PlanoTreino.class))).thenAnswer(inv -> inv.getArgument(0));

            service.generate(atleta.getId());

            ArgumentCaptor<PlanoTreino> plano = ArgumentCaptor.forClass(PlanoTreino.class);
            verify(planoTreinoRepository).save(plano.capture());
            assertThat(plano.getValue().getInitialCtl()).isEqualTo(40.0);
        }

        @Test
        @DisplayName("sem PMC e sem baseline responde 422 nomeando a base de carga")
        void semBaseDeCarga() {
            atletaExiste();
            provaTravada(provaAlvo(PROVA_20_SEMANAS));
            abertas();
            when(metricasDiariasRepository.findLatestByAtletaId(atleta.getId())).thenReturn(Optional.empty());
            when(baselineRepository.findByAtletaIdAndTenantId(atleta.getId(), tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(atleta.getId()))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("carga");
        }

        @Test
        @DisplayName("volume ignora treino cancelado e distância nula; sem treino usa o volumeSemanalMax")
        void volumeIgnoraCanceladoECaiNoOnboarding() {
            atletaExiste();
            provaTravada(provaAlvo(PROVA_20_SEMANAS));
            abertas();
            pmc(40.0, HOJE, HOJE.minusDays(100));
            TreinoRealizado cancelado = treino(HOJE.minusDays(3), "50");
            cancelado.setStatusSincronizacao(StatusSincronizacao.CANCELADO);
            treinos(cancelado, treino(HOJE.minusDays(4), null));
            PerfilOnboardingAtleta perfil = new PerfilOnboardingAtleta();
            perfil.setVolumeSemanalMax(28);
            when(perfilRepository.findByAtletaIdAndTenantId(atleta.getId(), tenantId)).thenReturn(Optional.of(perfil));
            when(planoTreinoRepository.save(any(PlanoTreino.class))).thenAnswer(inv -> inv.getArgument(0));

            Generation resultado = service.generate(atleta.getId());

            // CTL 40 sem decaimento → semana 1 com TSS 285 = 28 km × 285/280
            assertThat(resultado.plan().weeks().get(0).targetVolumeKm()).isEqualByComparingTo("28.5");
        }

        @Test
        @DisplayName("volume ínfimo que arredonda para 0,0 cai no volumeSemanalMax do onboarding")
        void volumeInfimoCaiNoOnboarding() {
            atletaExiste();
            provaTravada(provaAlvo(PROVA_20_SEMANAS));
            abertas();
            pmc(40.0, HOJE, HOJE.minusDays(100));
            treinos(treino(HOJE.minusDays(3), "0.1"));
            PerfilOnboardingAtleta perfil = new PerfilOnboardingAtleta();
            perfil.setVolumeSemanalMax(28);
            when(perfilRepository.findByAtletaIdAndTenantId(atleta.getId(), tenantId)).thenReturn(Optional.of(perfil));
            when(planoTreinoRepository.save(any(PlanoTreino.class))).thenAnswer(inv -> inv.getArgument(0));

            Generation resultado = service.generate(atleta.getId());

            assertThat(resultado.plan().weeks().get(0).targetVolumeKm()).isEqualByComparingTo("28.5");
        }

        @Test
        @DisplayName("nome de prova com 100 caracteres não estoura os 100 do nome da temporada")
        void nomeLongoTruncado() {
            atletaExiste();
            Prova prova = provaAlvo(PROVA_20_SEMANAS);
            prova.setNomeProva("M".repeat(100));
            provaTravada(prova);
            abertas();
            pmc(40.0, HOJE, HOJE.minusDays(100));
            treinos(treino(HOJE.minusDays(3), "40"));
            when(planoTreinoRepository.save(any(PlanoTreino.class))).thenAnswer(inv -> inv.getArgument(0));

            service.generate(atleta.getId());

            ArgumentCaptor<PlanoTreino> plano = ArgumentCaptor.forClass(PlanoTreino.class);
            verify(planoTreinoRepository).save(plano.capture());
            assertThat(plano.getValue().getNome()).hasSize(100).startsWith("Temporada M");
        }

        @Test
        @DisplayName("sem treino e sem volumeSemanalMax responde 422 nomeando a base de volume")
        void semBaseDeVolume() {
            atletaExiste();
            provaTravada(provaAlvo(PROVA_20_SEMANAS));
            abertas();
            pmc(40.0, HOJE, HOJE.minusDays(100));
            treinos();
            when(perfilRepository.findByAtletaIdAndTenantId(atleta.getId(), tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generate(atleta.getId()))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("volume");
        }

        @Test
        @DisplayName("atleta em calibração não gera: 422 antes de travar a prova e sem gravar")
        void calibracaoRecusa() {
            service = novo(true, true);
            atletaExiste();
            emCalibracao();

            assertThatThrownBy(() -> service.generate(atleta.getId()))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("calibração");
            verifyNoInteractions(provaRepository, planoTreinoRepository);
        }

        @Test
        @DisplayName("em calibração com o shadow desligado: ninguém gradua, então o gate não se aplica")
        void calibracaoComShadowDesligadoSegue() {
            atletaExiste();
            when(provaRepository.findTargetRaceIds(atleta.getId(), tenantId)).thenReturn(List.of());

            assertThatThrownBy(() -> service.generate(atleta.getId()))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("prova-alvo");
            verifyNoInteractions(baselineRepository);
        }

        @Test
        @DisplayName("baseline sem calibracaoIniciadaEm segue o fluxo com o shadow ligado")
        void foraDeCalibracaoSegue() {
            service = novo(true, true);
            atletaExiste();
            when(baselineRepository.findByAtletaIdAndTenantId(atleta.getId(), tenantId))
                    .thenReturn(Optional.of(new AthleteBaselineState()));
            when(provaRepository.findTargetRaceIds(atleta.getId(), tenantId)).thenReturn(List.of());

            assertThatThrownBy(() -> service.generate(atleta.getId()))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("prova-alvo");
        }
    }

    @Nested
    @DisplayName("findCurrent")
    class FindCurrent {

        @Test
        @DisplayName("flag desligada responde SeasonPlanDisabledException")
        void flagDesligada() {
            service = novo(false);

            assertThatThrownBy(() -> service.findCurrent(atleta.getId())).isInstanceOf(SeasonPlanDisabledException.class);
        }

        @Test
        @DisplayName("ACTIVE tem precedência sobre PROPOSED")
        void ativaVenceProposta() {
            atletaExiste();
            Prova prova = provaAlvo(PROVA_20_SEMANAS);
            PlanoTreino ativa = temporada(prova, SeasonPlanStatus.ACTIVE);
            abertas(temporada(prova, SeasonPlanStatus.PROPOSED), ativa);
            when(seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(ativa.getId(), tenantId))
                    .thenReturn(List.of(new SeasonWeek()));

            Optional<SeasonPlanResponseDto> atual = service.findCurrent(atleta.getId());

            assertThat(atual).get().extracting(SeasonPlanResponseDto::id).isEqualTo(ativa.getId());
        }

        @Test
        @DisplayName("marcos de prova cancelada ou remarcada para outra semana saem; validações recalculadas na leitura")
        void marcosEValidacoesNaLeitura() {
            atletaExiste();
            Prova alvo = provaAlvo(PROVA_20_SEMANAS);
            PlanoTreino ativa = temporada(alvo, SeasonPlanStatus.ACTIVE);
            abertas(ativa);
            Prova meia = provaAlvo(HOJE.plusDays(6));
            meia.setProvaAlvo(false);
            meia.setNomeProva("Meia de teste");
            meia.setDistancia(DistanciaProva.KM_21);
            Prova cancelada = provaAlvo(HOJE.plusDays(13));
            cancelada.setProvaAlvo(false);
            cancelada.setStatusProva(br.com.menthoros.backend.enums.ProvaStatus.CANCELADA);
            Prova remarcada = provaAlvo(HOJE.plusDays(30));
            remarcada.setProvaAlvo(false);
            when(seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(ativa.getId(), tenantId))
                    .thenReturn(List.of(semanaGravada(1, "72", "34", meia.getId()),
                            semanaGravada(2, "70", "20", cancelada.getId()),
                            semanaGravada(3, "70", "20", remarcada.getId())));
            when(provaRepository.findByIdInAndAssessoriaId(
                    List.of(meia.getId(), cancelada.getId(), remarcada.getId()), tenantId))
                    .thenReturn(List.of(meia, cancelada, remarcada));

            SeasonPlanResponseDto atual = service.findCurrent(atleta.getId()).orElseThrow();

            assertThat(atual.milestones()).extracting(SeasonPlanResponseDto.Milestone::weekIndex,
                            SeasonPlanResponseDto.Milestone::provaId, SeasonPlanResponseDto.Milestone::name)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(1, meia.getId(), "Meia de teste"));
            assertThat(atual.validations()).extracting(SeasonPlanResponseDto.Validation::rule,
                            SeasonPlanResponseDto.Validation::weekIndex, SeasonPlanResponseDto.Validation::severity)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(
                            br.com.menthoros.backend.domain.planner.SeasonPlanValidator.Rule.LONG_RUN_SHARE, 1, "WARN"));
        }

        @Test
        @DisplayName("sem marco não consulta provas e devolve listas vazias")
        void semMarcoListasVazias() {
            atletaExiste();
            PlanoTreino ativa = temporada(provaAlvo(PROVA_20_SEMANAS), SeasonPlanStatus.ACTIVE);
            abertas(ativa);
            when(seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(ativa.getId(), tenantId))
                    .thenReturn(List.of(semanaGravada(1, "40", "11", null)));

            SeasonPlanResponseDto atual = service.findCurrent(atleta.getId()).orElseThrow();

            assertThat(atual.milestones()).isEmpty();
            assertThat(atual.validations()).isEmpty();
            verify(provaRepository, never()).findByIdInAndAssessoriaId(any(), any());
        }

        @Test
        @DisplayName("sem temporada aberta devolve vazio")
        void semTemporada() {
            atletaExiste();
            abertas();

            assertThat(service.findCurrent(atleta.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("flag desligada responde SeasonPlanDisabledException sem tocar no banco")
        void flagDesligada() {
            service = novo(false);
            UUID id = UUID.randomUUID();

            assertThatThrownBy(() -> service.approve(atleta.getId(), id, null)).isInstanceOf(SeasonPlanDisabledException.class);
            verifyNoInteractions(planoTreinoRepository, provaRepository, usuarioRepository);
        }

        @Test
        @DisplayName("temporada que não é do atleta no tenant vira não encontrado, sem lock")
        void naoEncontrada() {
            UUID id = UUID.randomUUID();
            when(planoTreinoRepository.findRaceIdBySeasonPlan(id, atleta.getId(), tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approve(atleta.getId(), id, null)).isInstanceOf(DomainNotFoundException.class);
            verifyNoInteractions(provaRepository);
        }

        @Test
        @DisplayName("PROPOSED vira ACTIVE com o Usuario.id do treinador e o instante do relógio, lendo depois do lock")
        void aprova() {
            Prova prova = provaAlvo(PROVA_20_SEMANAS);
            PlanoTreino proposta = travadaComTemporada(prova, SeasonPlanStatus.PROPOSED);
            when(planoTreinoRepository.findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(
                    atleta.getId(), tenantId, List.of(SeasonPlanStatus.ACTIVE))).thenReturn(List.of());
            Usuario treinador = treinadorAutenticado();

            SeasonPlanResponseDto dto = service.approve(atleta.getId(), proposta.getId(), null);

            assertThat(dto.seasonStatus()).isEqualTo(SeasonPlanStatus.ACTIVE);
            assertThat(dto.approvedAt()).isEqualTo(Instant.parse("2026-11-16T12:00:00.123456Z"));
            assertThat(proposta.getApprovedBy()).isEqualTo(treinador.getId());
            InOrder ordem = inOrder(provaRepository, planoTreinoRepository);
            ordem.verify(provaRepository).findByIdAndTenantIdForUpdate(prova.getId(), tenantId);
            ordem.verify(planoTreinoRepository).findByIdAndAssessoriaId(proposta.getId(), tenantId);
            ordem.verify(planoTreinoRepository).saveAndFlush(proposta);
        }

        @Test
        @DisplayName("aprovar uma ACTIVE devolve a mesma sem reescrever quem e quando aprovou")
        void idempotente() {
            Prova prova = provaAlvo(PROVA_20_SEMANAS);
            PlanoTreino ativa = travadaComTemporada(prova, SeasonPlanStatus.ACTIVE);
            UUID aprovador = UUID.randomUUID();
            Instant quando = Instant.parse("2026-11-10T10:00:00Z");
            ativa.setApprovedBy(aprovador);
            ativa.setApprovedAt(quando);

            SeasonPlanResponseDto dto = service.approve(atleta.getId(), ativa.getId(), null);

            assertThat(dto.seasonStatus()).isEqualTo(SeasonPlanStatus.ACTIVE);
            assertThat(dto.approvedAt()).isEqualTo(quando);
            assertThat(ativa.getApprovedBy()).isEqualTo(aprovador);
            verify(planoTreinoRepository, never()).saveAndFlush(any());
            verifyNoInteractions(usuarioRepository);
        }

        @Test
        @DisplayName("ARCHIVED responde 409")
        void arquivada() {
            PlanoTreino arquivada = travadaComTemporada(provaAlvo(PROVA_20_SEMANAS), SeasonPlanStatus.ARCHIVED);

            assertThatThrownBy(() -> service.approve(atleta.getId(), arquivada.getId(), null))
                    .isInstanceOf(DomainConflictException.class);
            verify(planoTreinoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("outra ACTIVE do atleta responde 409")
        void outraAtiva() {
            PlanoTreino proposta = travadaComTemporada(provaAlvo(PROVA_20_SEMANAS), SeasonPlanStatus.PROPOSED);
            PlanoTreino outra = temporada(provaAlvo(PROVA_20_SEMANAS.plusWeeks(4)), SeasonPlanStatus.ACTIVE);
            when(planoTreinoRepository.findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(
                    atleta.getId(), tenantId, List.of(SeasonPlanStatus.ACTIVE))).thenReturn(List.of(outra));

            assertThatThrownBy(() -> service.approve(atleta.getId(), proposta.getId(), null))
                    .isInstanceOf(DomainConflictException.class);
            verify(planoTreinoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("proposta com a prova cancelada encontrada depois do lock responde 422")
        void provaCancelada() {
            Prova prova = provaAlvo(PROVA_20_SEMANAS);
            prova.setStatusProva(ProvaStatus.CANCELADA);
            PlanoTreino proposta = travadaComTemporada(prova, SeasonPlanStatus.PROPOSED);

            assertThatThrownBy(() -> service.approve(atleta.getId(), proposta.getId(), null))
                    .isInstanceOf(DomainRuleViolationException.class);
            verify(planoTreinoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("temporada arquivada pelo cancelamento da prova responde 409, não 422")
        void arquivadaPeloCancelamento() {
            Prova prova = provaAlvo(PROVA_20_SEMANAS);
            prova.setStatusProva(ProvaStatus.CANCELADA);
            PlanoTreino arquivada = travadaComTemporada(prova, SeasonPlanStatus.ARCHIVED);

            assertThatThrownBy(() -> service.approve(atleta.getId(), arquivada.getId(), null))
                    .isInstanceOf(DomainConflictException.class);
        }

        @Test
        @DisplayName("proposta não vira ACTIVE com o atleta em calibração")
        void calibracaoRecusa() {
            service = novo(true, true);
            PlanoTreino proposta = travadaComTemporada(provaAlvo(PROVA_20_SEMANAS), SeasonPlanStatus.PROPOSED);
            emCalibracao();

            assertThatThrownBy(() -> service.approve(atleta.getId(), proposta.getId(), null))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("calibração");
            assertThat(proposta.getSeasonStatus()).isEqualTo(SeasonPlanStatus.PROPOSED);
            verify(planoTreinoRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("approve — ajuste v2 (add-season-plan-adjust-v2)")
    class ApproveAjuste {

        @ParameterizedTest(name = "[{index}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("sem motivo, ou em branco, responde 422 e nenhuma versão muda")
        void semMotivo(String motivo) {
            PlanoTreino rascunho = travadaComTemporada(provaAlvo(PROVA_20_SEMANAS), SeasonPlanStatus.PROPOSED);
            rascunho.setBaseSeasonPlanId(UUID.randomUUID());

            assertThatThrownBy(() -> service.approve(atleta.getId(), rascunho.getId(), motivo))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("motivo");
            assertThat(rascunho.getSeasonStatus()).isEqualTo(SeasonPlanStatus.PROPOSED);
            verify(planoTreinoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("motivo com 256 caracteres responde 422")
        void motivoLongoDemais() {
            PlanoTreino rascunho = travadaComTemporada(provaAlvo(PROVA_20_SEMANAS), SeasonPlanStatus.PROPOSED);
            rascunho.setBaseSeasonPlanId(UUID.randomUUID());

            assertThatThrownBy(() -> service.approve(atleta.getId(), rascunho.getId(), "x".repeat(256)))
                    .isInstanceOf(DomainRuleViolationException.class);
            verify(planoTreinoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("com motivo de 255: semana em andamento volta da v1, futura fica do ajuste, v1 arquiva antes da v2 ativar")
        void aprova() {
            Prova prova = provaAlvo(PROVA_20_SEMANAS);
            PlanoTreino base = temporada(prova, SeasonPlanStatus.ACTIVE);
            base.setVersion(1);
            PlanoTreino rascunho = travadaComTemporada(prova, SeasonPlanStatus.PROPOSED);
            rascunho.setVersion(2);
            rascunho.setBaseSeasonPlanId(base.getId());
            when(planoTreinoRepository.findByIdAndAssessoriaId(base.getId(), tenantId)).thenReturn(Optional.of(base));
            SeasonWeek baseCorrente = semanaGravada(1, "40", "10", null);
            SeasonWeek baseFutura = semanaGravada(2, "42", "11", null);
            SeasonWeek corrente = semanaGravada(1, "99", "10", null);
            corrente.setValueSource(WeekValueSource.COACH);
            SeasonWeek futura = semanaGravada(2, "50", "14", null);
            futura.setValueSource(WeekValueSource.COACH);
            when(seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(base.getId(), tenantId))
                    .thenReturn(List.of(baseCorrente, baseFutura));
            when(seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(rascunho.getId(), tenantId))
                    .thenReturn(List.of(corrente, futura));
            Usuario treinador = treinadorAutenticado();
            String motivo = "m".repeat(255);

            SeasonPlanResponseDto dto = service.approve(atleta.getId(), rascunho.getId(), motivo);

            assertThat(dto.seasonStatus()).isEqualTo(SeasonPlanStatus.ACTIVE);
            assertThat(corrente.getTargetVolumeKm()).isEqualByComparingTo("40");
            assertThat(corrente.getValueSource()).isEqualTo(WeekValueSource.ENGINE);
            assertThat(futura.getTargetVolumeKm()).isEqualByComparingTo("50");
            assertThat(rascunho.getPeakVolumeKm()).isEqualByComparingTo("50");
            assertThat(rascunho.getVersionReason()).isEqualTo(motivo);
            assertThat(rascunho.getApprovedBy()).isEqualTo(treinador.getId());
            assertThat(base.getSeasonStatus()).isEqualTo(SeasonPlanStatus.ARCHIVED);
            InOrder ordem = inOrder(planoTreinoRepository);
            ordem.verify(planoTreinoRepository).saveAndFlush(base);
            ordem.verify(planoTreinoRepository).saveAndFlush(rascunho);
            verify(planoTreinoRepository, never()).findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(
                    atleta.getId(), tenantId, List.of(SeasonPlanStatus.ACTIVE));
        }

        @Test
        @DisplayName("a base deixou de ser a ACTIVE (outro ajuste aprovado antes) responde 409 sem trocar nada")
        void baseTrocada() {
            Prova prova = provaAlvo(PROVA_20_SEMANAS);
            PlanoTreino base = temporada(prova, SeasonPlanStatus.ARCHIVED);
            base.setVersion(1);
            PlanoTreino rascunho = travadaComTemporada(prova, SeasonPlanStatus.PROPOSED);
            rascunho.setVersion(2);
            rascunho.setBaseSeasonPlanId(base.getId());
            when(planoTreinoRepository.findByIdAndAssessoriaId(base.getId(), tenantId)).thenReturn(Optional.of(base));
            treinadorAutenticado();

            assertThatThrownBy(() -> service.approve(atleta.getId(), rascunho.getId(), "Atleta lesionou"))
                    .isInstanceOf(DomainConflictException.class);
            assertThat(rascunho.getSeasonStatus()).isEqualTo(SeasonPlanStatus.PROPOSED);
            verify(planoTreinoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("ajuste não vira ACTIVE com o atleta em calibração")
        void calibracao() {
            service = novo(true, true);
            PlanoTreino rascunho = travadaComTemporada(provaAlvo(PROVA_20_SEMANAS), SeasonPlanStatus.PROPOSED);
            rascunho.setBaseSeasonPlanId(UUID.randomUUID());
            emCalibracao();

            assertThatThrownBy(() -> service.approve(atleta.getId(), rascunho.getId(), "Atleta lesionou"))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("calibração");
            verify(planoTreinoRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("findForAuthenticatedAthlete")
    class FindForAuthenticatedAthlete {

        @Test
        @DisplayName("flag desligada devolve vazio sem resolver o atleta")
        void flagDesligada() {
            service = novo(false);

            assertThat(service.findForAuthenticatedAthlete()).isEmpty();
            verifyNoInteractions(atletaResolver, planoTreinoRepository);
        }

        @Test
        @DisplayName("sem ACTIVE devolve vazio")
        void semAtiva() {
            when(atletaResolver.resolverAtletaIdAtual()).thenReturn(atleta.getId());
            ativas();

            assertThat(service.findForAuthenticatedAthlete()).isEmpty();
        }

        @Test
        @DisplayName("prova que já passou devolve vazio")
        void provaPassou() {
            when(atletaResolver.resolverAtletaIdAtual()).thenReturn(atleta.getId());
            ativas(temporada(provaAlvo(HOJE.minusDays(1)), SeasonPlanStatus.ACTIVE));

            assertThat(service.findForAuthenticatedAthlete()).isEmpty();
        }

        @Test
        @DisplayName("ACTIVE traz prova, dias, fase da semana corrente e só os marcos de hoje em diante")
        void ativa() {
            when(atletaResolver.resolverAtletaIdAtual()).thenReturn(atleta.getId());
            PlanoTreino ativa = temporada(provaAlvo(PROVA_20_SEMANAS), SeasonPlanStatus.ACTIVE);
            ativas(ativa);
            Prova passada = preparatoria("Passada", HOJE.minusDays(3));
            Prova futura = preparatoria("Futura", HOJE.plusDays(10));
            SeasonWeek anterior = semanaGravada(0, "38", "10", passada.getId());
            SeasonWeek corrente = semanaGravada(1, "40", "11", null);
            corrente.setPhase(TrainingPhase.BUILD);
            SeasonWeek seguinte = semanaGravada(2, "42", "11", futura.getId());
            when(seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(ativa.getId(), tenantId))
                    .thenReturn(List.of(anterior, corrente, seguinte));
            when(provaRepository.findByIdInAndAssessoriaId(anyCollection(), any())).thenReturn(List.of(passada, futura));

            AthleteSeasonPlanDto dto = service.findForAuthenticatedAthlete().orElseThrow();

            assertThat(dto.raceName()).isEqualTo("Maratona alvo");
            assertThat(dto.raceDate()).isEqualTo(PROVA_20_SEMANAS);
            assertThat(dto.distance()).isEqualTo(DistanciaProva.KM_42);
            assertThat(dto.daysToRace()).isEqualTo(139);
            assertThat(dto.currentPhase()).isEqualTo(TrainingPhase.BUILD);
            assertThat(dto.upcomingMilestones()).extracting(AthleteSeasonPlanDto.Milestone::name).containsExactly("Futura");
        }

        @Test
        @DisplayName("antes da primeira semana a fase atual fica ausente")
        void foraDoHorizonte() {
            when(atletaResolver.resolverAtletaIdAtual()).thenReturn(atleta.getId());
            PlanoTreino ativa = temporada(provaAlvo(PROVA_20_SEMANAS), SeasonPlanStatus.ACTIVE);
            ativas(ativa);
            when(seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(ativa.getId(), tenantId))
                    .thenReturn(List.of(semanaGravada(2, "40", "11", null)));

            AthleteSeasonPlanDto dto = service.findForAuthenticatedAthlete().orElseThrow();

            assertThat(dto.currentPhase()).isNull();
            assertThat(dto.upcomingMilestones()).isEmpty();
        }

        private void ativas(PlanoTreino... temporadas) {
            when(planoTreinoRepository.findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(
                    atleta.getId(), tenantId, List.of(SeasonPlanStatus.ACTIVE))).thenReturn(List.of(temporadas));
        }
    }

    private SeasonPlanServiceImpl novo(boolean enabled) {
        return novo(enabled, false);
    }

    private SeasonPlanServiceImpl novo(boolean enabled, boolean plannerShadow) {
        return new SeasonPlanServiceImpl(atletaRepository, provaRepository, planoTreinoRepository,
                seasonWeekRepository, metricasDiariasRepository, baselineRepository, perfilRepository,
                treinoRealizadoRepository, usuarioRepository, principalResolver, atletaResolver,
                new SeasonPlanResponseMapper(provaRepository, seasonWeekRepository, CLOCK),
                new SeasonPlanVersioning(planoTreinoRepository, provaRepository, seasonWeekRepository, CLOCK), CLOCK, enabled,
                plannerShadow);
    }

    /** Temporada com a prova travada e relida pelo id, como no fluxo de aprovar. */
    private PlanoTreino travadaComTemporada(Prova prova, SeasonPlanStatus status) {
        PlanoTreino t = temporada(prova, status);
        when(planoTreinoRepository.findRaceIdBySeasonPlan(t.getId(), atleta.getId(), tenantId))
                .thenReturn(Optional.of(prova.getId()));
        when(provaRepository.findByIdAndTenantIdForUpdate(prova.getId(), tenantId)).thenReturn(Optional.of(prova));
        when(planoTreinoRepository.findByIdAndAssessoriaId(t.getId(), tenantId)).thenReturn(Optional.of(t));
        return t;
    }

    private Usuario treinadorAutenticado() {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        when(principalResolver.getCurrentSubject()).thenReturn("kc-sub");
        when(usuarioRepository.findByKeycloakIdAndAssessoria_Id("kc-sub", tenantId)).thenReturn(Optional.of(u));
        return u;
    }

    private Prova preparatoria(String nome, LocalDate data) {
        Prova p = provaAlvo(data);
        p.setProvaAlvo(false);
        p.setNomeProva(nome);
        p.setDistancia(DistanciaProva.KM_21);
        return p;
    }

    private void atletaExiste() {
        when(atletaRepository.findByIdAndTenantId(atleta.getId(), tenantId)).thenReturn(Optional.of(atleta));
    }

    private Prova provaTravada(Prova prova) {
        when(provaRepository.findTargetRaceIds(atleta.getId(), tenantId)).thenReturn(List.of(prova.getId()));
        when(provaRepository.findByIdAndTenantIdForUpdate(prova.getId(), tenantId)).thenReturn(Optional.of(prova));
        return prova;
    }

    private void abertas(PlanoTreino... temporadas) {
        when(planoTreinoRepository.findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(
                any(), any(), anyCollection())).thenReturn(List.of(temporadas));
    }

    private void pmc(double ctl, LocalDate ultima, LocalDate primeira) {
        MetricasDiarias m = new MetricasDiarias();
        m.setCtl(ctl);
        m.setData(ultima);
        when(metricasDiariasRepository.findLatestByAtletaId(atleta.getId())).thenReturn(Optional.of(m));
        when(metricasDiariasRepository.findDataPrimeiraMetrica(atleta.getId())).thenReturn(primeira);
    }

    private void baseline(double ctlEstimado) {
        AthleteBaselineState b = new AthleteBaselineState();
        b.setCtlEstimado(ctlEstimado);
        when(baselineRepository.findByAtletaIdAndTenantId(atleta.getId(), tenantId)).thenReturn(Optional.of(b));
    }

    private void treinos(TreinoRealizado... treinos) {
        // janela: as 4 semanas completas antes da corrente (2026-10-19 a 2026-11-15)
        when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(
                atleta.getId(), tenantId, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 15)))
                .thenReturn(List.of(treinos));
    }

    private static TreinoRealizado treino(LocalDate data, String km) {
        TreinoRealizado t = new TreinoRealizado();
        t.setDataTreino(data);
        t.setDistanciaKm(km == null ? null : new BigDecimal(km));
        return t;
    }

    private Prova provaAlvo(LocalDate data) {
        Prova prova = new Prova();
        prova.setId(UUID.randomUUID());
        prova.setNomeProva("Maratona alvo");
        prova.setDataProva(data);
        prova.setDistancia(DistanciaProva.KM_42);
        prova.setProvaAlvo(true);
        prova.setAtleta(atleta);
        prova.setAssessoria(atleta.getAssessoria());
        return prova;
    }

    private static SeasonWeek semanaGravada(int indice, String km, String longao, UUID marco) {
        SeasonWeek w = new SeasonWeek();
        w.setWeekIndex(indice);
        w.setWeekStart(HOJE.plusWeeks(indice - 1L));
        w.setPhase(br.com.menthoros.backend.domain.planner.TrainingPhase.BASE);
        w.setTargetVolumeKm(new BigDecimal(km));
        w.setTargetTss(300.0);
        w.setLongRunKm(new BigDecimal(longao));
        w.setMilestoneRaceId(marco);
        w.setValueSource(WeekValueSource.ENGINE);
        return w;
    }

    private PlanoTreino temporada(Prova prova, SeasonPlanStatus status) {
        PlanoTreino p = new PlanoTreino();
        p.setId(UUID.randomUUID());
        p.setAtleta(atleta);
        p.setProva(prova);
        p.setAssessoria(atleta.getAssessoria());
        p.setSeasonStatus(status);
        return p;
    }

    private void emCalibracao() {
        AthleteBaselineState b = new AthleteBaselineState();
        b.setCalibracaoIniciadaEm(Instant.parse("2026-10-01T00:00:00Z"));
        when(baselineRepository.findByAtletaIdAndTenantId(atleta.getId(), tenantId)).thenReturn(Optional.of(b));
    }
}
