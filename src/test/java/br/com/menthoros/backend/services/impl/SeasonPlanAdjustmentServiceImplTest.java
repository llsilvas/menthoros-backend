package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.dto.input.SeasonWeekUpdateInputDto;
import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.enums.WeekValueSource;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.SeasonPlanDisabledException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.PlanoTreinoRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.SeasonWeekRepository;
import br.com.menthoros.backend.services.SeasonPlanService.Generation;
import br.com.menthoros.backend.services.helper.SeasonPlanResponseMapper;
import br.com.menthoros.backend.services.helper.SeasonPlanVersioning;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeasonPlanAdjustmentServiceImplTest {

    /** 2026-11-16 (segunda) 12:00 UTC = 09:00 em São Paulo: a semana 1 está em andamento. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 16);
    private static final LocalDate PROVA_LONGE = LocalDate.of(2027, 4, 4);

    @Mock private PlanoTreinoRepository planoTreinoRepository;
    @Mock private ProvaRepository provaRepository;
    @Mock private SeasonWeekRepository seasonWeekRepository;
    @Captor private ArgumentCaptor<List<SeasonWeek>> semanasCaptor;

    private SeasonPlanAdjustmentServiceImpl service;
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
    @DisplayName("createDraft")
    class CreateDraft {

        @Test
        @DisplayName("flag desligada responde SeasonPlanDisabledException sem tocar no banco")
        void flagDesligada() {
            service = novo(false);

            assertThatThrownBy(() -> service.createDraft(atleta.getId(), UUID.randomUUID()))
                    .isInstanceOf(SeasonPlanDisabledException.class);
            verifyNoInteractions(planoTreinoRepository, provaRepository);
        }

        @Test
        @DisplayName("cria PROPOSED v2 com a base e a cópia de todas as semanas, depois do lock da prova")
        void cria() {
            PlanoTreino ativa = travada(temporada(SeasonPlanStatus.ACTIVE, 1));
            List<SeasonWeek> semanas = List.of(semana(ativa, 1, "40"), semana(ativa, 2, "42"));
            semanas.get(1).setKeySessions("6x1km");
            when(seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(ativa.getId(), tenantId))
                    .thenReturn(semanas);
            propostas();
            when(planoTreinoRepository.saveAndFlush(any())).thenAnswer(inv -> {
                PlanoTreino p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            Generation g = service.createDraft(atleta.getId(), ativa.getId());

            assertThat(g.created()).isTrue();
            assertThat(g.plan().seasonStatus()).isEqualTo(SeasonPlanStatus.PROPOSED);
            assertThat(g.plan().version()).isEqualTo(2);
            assertThat(g.plan().baseSeasonPlanId()).isEqualTo(ativa.getId());
            verify(provaRepository).findByIdAndTenantIdForUpdate(ativa.getProva().getId(), tenantId);
            verify(seasonWeekRepository).saveAll(semanasCaptor.capture());
            assertThat(semanasCaptor.getValue()).extracting(SeasonWeek::getWeekIndex).containsExactly(1, 2);
            assertThat(semanasCaptor.getValue().get(1).getKeySessions()).isEqualTo("6x1km");
            assertThat(semanasCaptor.getValue()).allSatisfy(w -> assertThat(w.getSeasonPlan()).isNotSameAs(ativa));
        }

        @Test
        @DisplayName("com ajuste da mesma base já aberto devolve o mesmo (created=false), sem criar")
        void idempotente() {
            PlanoTreino ativa = travada(temporada(SeasonPlanStatus.ACTIVE, 1));
            PlanoTreino rascunho = temporada(SeasonPlanStatus.PROPOSED, 2);
            rascunho.setBaseSeasonPlanId(ativa.getId());
            propostas(rascunho);

            Generation g = service.createDraft(atleta.getId(), ativa.getId());

            assertThat(g.created()).isFalse();
            assertThat(g.plan().id()).isEqualTo(rascunho.getId());
            verify(planoTreinoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("outra proposta aberta do atleta responde 409")
        void outraProposta() {
            PlanoTreino ativa = travada(temporada(SeasonPlanStatus.ACTIVE, 1));
            propostas(temporada(SeasonPlanStatus.PROPOSED, 1));

            assertThatThrownBy(() -> service.createDraft(atleta.getId(), ativa.getId()))
                    .isInstanceOf(DomainConflictException.class);
            verify(planoTreinoRepository, never()).saveAndFlush(any());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = SeasonPlanStatus.class, names = {"PROPOSED", "ARCHIVED", "COMPLETED"})
        @DisplayName("só a ACTIVE pode ser ajustada")
        void naoAtiva(SeasonPlanStatus status) {
            PlanoTreino t = travada(temporada(status, 1));

            assertThatThrownBy(() -> service.createDraft(atleta.getId(), t.getId()))
                    .isInstanceOf(DomainConflictException.class);
        }

        @Test
        @DisplayName("temporada de outro atleta ou tenant vira não encontrado, sem lock")
        void naoEncontrada() {
            UUID id = UUID.randomUUID();
            when(planoTreinoRepository.findRaceIdBySeasonPlan(id, atleta.getId(), tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createDraft(atleta.getId(), id)).isInstanceOf(DomainNotFoundException.class);
            verifyNoInteractions(provaRepository);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("ACTIVE com ajuste aberto traz openDraftId")
        void comAjusteAberto() {
            PlanoTreino ativa = temporada(SeasonPlanStatus.ACTIVE, 1);
            when(planoTreinoRepository.findRaceIdBySeasonPlan(ativa.getId(), atleta.getId(), tenantId))
                    .thenReturn(Optional.of(ativa.getProva().getId()));
            when(planoTreinoRepository.findByIdAndAssessoriaId(ativa.getId(), tenantId)).thenReturn(Optional.of(ativa));
            PlanoTreino rascunho = temporada(SeasonPlanStatus.PROPOSED, 2);
            rascunho.setBaseSeasonPlanId(ativa.getId());
            propostas(rascunho);

            SeasonPlanResponseDto dto = service.findById(atleta.getId(), ativa.getId());

            assertThat(dto.openDraftId()).isEqualTo(rascunho.getId());
        }

        @Test
        @DisplayName("temporada que não é do atleta no tenant vira não encontrado")
        void naoEncontrada() {
            UUID id = UUID.randomUUID();
            when(planoTreinoRepository.findRaceIdBySeasonPlan(id, atleta.getId(), tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(atleta.getId(), id)).isInstanceOf(DomainNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateWeek")
    class UpdateWeek {

        @Test
        @DisplayName("semana futura recebe os valores, vira COACH e o pico é recalculado; as demais não mudam")
        void editaFutura() {
            PlanoTreino rascunho = travada(temporada(SeasonPlanStatus.PROPOSED, 2));
            rascunho.setPeakVolumeKm(new BigDecimal("42"));
            List<SeasonWeek> semanas = semanas(rascunho, "40", "42", "41");

            SeasonPlanResponseDto dto = service.updateWeek(atleta.getId(), rascunho.getId(), 3,
                    new SeasonWeekUpdateInputDto(TrainingPhase.BUILD, 350.0, new BigDecimal("50"), new BigDecimal("18"),
                            true, " 6x1km "));

            SeasonWeek editada = semanas.get(2);
            assertThat(editada.getPhase()).isEqualTo(TrainingPhase.BUILD);
            assertThat(editada.getTargetTss()).isEqualTo(350.0);
            assertThat(editada.getTargetVolumeKm()).isEqualByComparingTo("50");
            assertThat(editada.isDeload()).isTrue();
            assertThat(editada.getKeySessions()).isEqualTo("6x1km");
            assertThat(editada.getValueSource()).isEqualTo(WeekValueSource.COACH);
            assertThat(semanas.get(1).getValueSource()).isEqualTo(WeekValueSource.ENGINE);
            assertThat(rascunho.getPeakVolumeKm()).isEqualByComparingTo("50");
            assertThat(rascunho.getMaxLongRunKm()).isEqualByComparingTo("18");
            assertThat(dto.weeks().get(2).keySessions()).isEqualTo("6x1km");
            verify(seasonWeekRepository).save(editada);
            verify(planoTreinoRepository).save(rascunho);
        }

        @Test
        @DisplayName("campo ausente não muda e sessões-chave em branco limpam")
        void parcial() {
            PlanoTreino rascunho = travada(temporada(SeasonPlanStatus.PROPOSED, 2));
            List<SeasonWeek> semanas = semanas(rascunho, "40", "42");
            semanas.get(1).setKeySessions("antigo");

            service.updateWeek(atleta.getId(), rascunho.getId(), 2,
                    new SeasonWeekUpdateInputDto(null, null, null, null, null, "  "));

            assertThat(semanas.get(1).getTargetVolumeKm()).isEqualByComparingTo("42");
            assertThat(semanas.get(1).getPhase()).isEqualTo(TrainingPhase.BASE);
            assertThat(semanas.get(1).getKeySessions()).isNull();
        }

        @Test
        @DisplayName("semana em andamento responde 422 com o motivo, sem gravar")
        void emAndamento() {
            PlanoTreino rascunho = travada(temporada(SeasonPlanStatus.PROPOSED, 2));
            semanas(rascunho, "40", "42");

            assertThatThrownBy(() -> service.updateWeek(atleta.getId(), rascunho.getId(), 1, volume("45")))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("em andamento");
            verify(seasonWeekRepository, never()).save(any());
        }

        @Test
        @DisplayName("semana da prova-alvo responde 422")
        void semanaDaProva() {
            PlanoTreino rascunho = travada(temporada(SeasonPlanStatus.PROPOSED, 2));
            rascunho.getProva().setDataProva(HOJE.plusWeeks(2).plusDays(6));
            semanas(rascunho, "40", "42", "30");

            assertThatThrownBy(() -> service.updateWeek(atleta.getId(), rascunho.getId(), 3, volume("20")))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("prova-alvo");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TrainingPhase.class, names = {"BASE", "BUILD", "PEAK", "TAPER"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("fases fora de BASE/BUILD/PEAK/TAPER respondem 422 antes do lock")
        void faseNaoPermitida(TrainingPhase fase) {
            UUID id = UUID.randomUUID();

            assertThatThrownBy(() -> service.updateWeek(atleta.getId(), id, 3,
                    new SeasonWeekUpdateInputDto(fase, null, null, null, null, null)))
                    .isInstanceOf(DomainRuleViolationException.class);
            verifyNoInteractions(provaRepository);
        }

        @Test
        @DisplayName("valor negativo ou sessões-chave acima de 500 caracteres respondem 422")
        void valoresInvalidos() {
            UUID id = UUID.randomUUID();

            assertThatThrownBy(() -> service.updateWeek(atleta.getId(), id, 3, volume("-1")))
                    .isInstanceOf(DomainRuleViolationException.class);
            assertThatThrownBy(() -> service.updateWeek(atleta.getId(), id, 3,
                    new SeasonWeekUpdateInputDto(null, -5.0, null, null, null, null)))
                    .isInstanceOf(DomainRuleViolationException.class);
            assertThatThrownBy(() -> service.updateWeek(atleta.getId(), id, 3,
                    new SeasonWeekUpdateInputDto(null, null, null, null, null, "x".repeat(501))))
                    .isInstanceOf(DomainRuleViolationException.class);
        }

        @Test
        @DisplayName("temporada ACTIVE (aprovada no meio do caminho) responde 409")
        void naoProposta() {
            PlanoTreino ativa = travada(temporada(SeasonPlanStatus.ACTIVE, 2));

            assertThatThrownBy(() -> service.updateWeek(atleta.getId(), ativa.getId(), 3, volume("45")))
                    .isInstanceOf(DomainConflictException.class);
        }

        @Test
        @DisplayName("semana inexistente responde 404")
        void semanaInexistente() {
            PlanoTreino rascunho = travada(temporada(SeasonPlanStatus.PROPOSED, 2));
            semanas(rascunho, "40", "42");

            assertThatThrownBy(() -> service.updateWeek(atleta.getId(), rascunho.getId(), 9, volume("45")))
                    .isInstanceOf(DomainNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("discard")
    class Discard {

        @Test
        @DisplayName("ajuste aberto vira ARCHIVED com o motivo de descarte, sem apagar")
        void descarta() {
            PlanoTreino rascunho = travada(temporada(SeasonPlanStatus.PROPOSED, 2));
            rascunho.setBaseSeasonPlanId(UUID.randomUUID());

            service.discard(atleta.getId(), rascunho.getId());

            assertThat(rascunho.getSeasonStatus()).isEqualTo(SeasonPlanStatus.ARCHIVED);
            assertThat(rascunho.getVersionReason()).isEqualTo("rascunho descartado");
            verify(planoTreinoRepository).save(rascunho);
            verify(planoTreinoRepository, never()).delete(any());
        }

        @Test
        @DisplayName("proposta v1 (sem base) responde 409")
        void propostaV1() {
            PlanoTreino proposta = travada(temporada(SeasonPlanStatus.PROPOSED, 1));

            assertThatThrownBy(() -> service.discard(atleta.getId(), proposta.getId()))
                    .isInstanceOf(DomainConflictException.class);
        }

        @Test
        @DisplayName("ajuste já aprovado (ACTIVE) responde 409")
        void jaAprovado() {
            PlanoTreino ativa = travada(temporada(SeasonPlanStatus.ACTIVE, 2));
            ativa.setBaseSeasonPlanId(UUID.randomUUID());

            assertThatThrownBy(() -> service.discard(atleta.getId(), ativa.getId()))
                    .isInstanceOf(DomainConflictException.class);
            verify(planoTreinoRepository, never()).save(any());
        }
    }

    private SeasonPlanAdjustmentServiceImpl novo(boolean enabled) {
        return new SeasonPlanAdjustmentServiceImpl(planoTreinoRepository, seasonWeekRepository,
                new SeasonPlanVersioning(planoTreinoRepository, provaRepository, seasonWeekRepository, CLOCK),
                new SeasonPlanResponseMapper(provaRepository, seasonWeekRepository, CLOCK), CLOCK, enabled);
    }

    /** Temporada com a prova travada e relida pelo id, como no fluxo real. */
    private PlanoTreino travada(PlanoTreino t) {
        when(planoTreinoRepository.findRaceIdBySeasonPlan(t.getId(), atleta.getId(), tenantId))
                .thenReturn(Optional.of(t.getProva().getId()));
        when(provaRepository.findByIdAndTenantIdForUpdate(t.getProva().getId(), tenantId)).thenReturn(Optional.of(t.getProva()));
        when(planoTreinoRepository.findByIdAndAssessoriaId(t.getId(), tenantId)).thenReturn(Optional.of(t));
        return t;
    }

    private void propostas(PlanoTreino... temporadas) {
        when(planoTreinoRepository.findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(
                atleta.getId(), tenantId, List.of(SeasonPlanStatus.PROPOSED))).thenReturn(List.of(temporadas));
    }

    private List<SeasonWeek> semanas(PlanoTreino t, String... kms) {
        List<SeasonWeek> lista = new java.util.ArrayList<>();
        for (int i = 0; i < kms.length; i++) {
            lista.add(semana(t, i + 1, kms[i]));
        }
        when(seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(t.getId(), tenantId)).thenReturn(lista);
        return lista;
    }

    private static SeasonWeekUpdateInputDto volume(String km) {
        return new SeasonWeekUpdateInputDto(null, null, new BigDecimal(km), null, null, null);
    }

    private SeasonWeek semana(PlanoTreino t, int indice, String km) {
        SeasonWeek w = new SeasonWeek();
        w.setId(UUID.randomUUID());
        w.setTenantId(tenantId);
        w.setSeasonPlan(t);
        w.setWeekIndex(indice);
        w.setWeekStart(HOJE.plusWeeks(indice - 1L));
        w.setPhase(TrainingPhase.BASE);
        w.setTargetVolumeKm(new BigDecimal(km));
        w.setTargetTss(300.0);
        w.setLongRunKm(new BigDecimal("12"));
        w.setValueSource(WeekValueSource.ENGINE);
        return w;
    }

    private PlanoTreino temporada(SeasonPlanStatus status, int versao) {
        Prova prova = new Prova();
        prova.setId(UUID.randomUUID());
        prova.setNomeProva("Maratona alvo");
        prova.setDataProva(PROVA_LONGE);
        prova.setDistancia(DistanciaProva.KM_42);
        prova.setProvaAlvo(true);
        PlanoTreino p = new PlanoTreino();
        p.setId(UUID.randomUUID());
        p.setAtleta(atleta);
        p.setProva(prova);
        p.setAssessoria(atleta.getAssessoria());
        p.setSeasonStatus(status);
        p.setVersion(versao);
        return p;
    }
}
