package br.com.menthoros.backend.services.impl;

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
import br.com.menthoros.backend.services.SeasonPlanService.Generation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeasonPlanServiceImplTest {

    /** 2026-11-16 (segunda) 12:00 UTC = 09:00 em São Paulo. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
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

            List<SeasonPlanResponseDto.Week> semanas = resultado.plan().weeks();
            assertThat(semanas).hasSize(20);
            assertThat(semanas).allSatisfy(s -> {
                assertThat(s.valueSource()).isEqualTo(WeekValueSource.ENGINE);
                assertThat(s.deload()).isFalse();
                assertThat(s.longRunKm()).isNull();
            });
            assertThat(semanas.get(19).phase()).isEqualTo(TrainingPhase.RACE_WEEK);
            assertThat(resultado.plan().peakVolumeKm()).isEqualByComparingTo(salvo.getPeakVolumeKm());
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
        @DisplayName("sem temporada aberta devolve vazio")
        void semTemporada() {
            atletaExiste();
            abertas();

            assertThat(service.findCurrent(atleta.getId())).isEmpty();
        }
    }

    private SeasonPlanServiceImpl novo(boolean enabled) {
        return new SeasonPlanServiceImpl(atletaRepository, provaRepository, planoTreinoRepository,
                seasonWeekRepository, metricasDiariasRepository, baselineRepository, perfilRepository,
                treinoRealizadoRepository, CLOCK, enabled);
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

    private PlanoTreino temporada(Prova prova, SeasonPlanStatus status) {
        PlanoTreino p = new PlanoTreino();
        p.setId(UUID.randomUUID());
        p.setAtleta(atleta);
        p.setProva(prova);
        p.setAssessoria(atleta.getAssessoria());
        p.setSeasonStatus(status);
        return p;
    }
}
