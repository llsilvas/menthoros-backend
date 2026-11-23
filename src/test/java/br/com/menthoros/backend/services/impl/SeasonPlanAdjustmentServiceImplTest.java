package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.planner.SeasonPlanner;
import br.com.menthoros.backend.domain.planner.SeasonPlanner.WeekPlan;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.dto.input.SeasonPlanRecalculateInputDto;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
            ativa.setInitialVolumeKm(new BigDecimal("38.5"));
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
            assertThat(g.plan().initialVolumeKm()).isEqualByComparingTo("38.5");
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
        @DisplayName("valor acima do teto de domínio responde 422, nunca 500 do banco")
        void acimaDoTeto() {
            UUID id = UUID.randomUUID();

            assertThatThrownBy(() -> service.updateWeek(atleta.getId(), id, 3, volume("500.001")))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("limite");
            assertThatThrownBy(() -> service.updateWeek(atleta.getId(), id, 3,
                    new SeasonWeekUpdateInputDto(null, null, null, new BigDecimal("1e9"), null, null)))
                    .isInstanceOf(DomainRuleViolationException.class);
            assertThatThrownBy(() -> service.updateWeek(atleta.getId(), id, 3,
                    new SeasonWeekUpdateInputDto(null, 3001.0, null, null, null, null)))
                    .isInstanceOf(DomainRuleViolationException.class);
            verifyNoInteractions(provaRepository);
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
    @DisplayName("recalculate")
    class Recalculate {

        private final SeasonPlanRecalculateInputDto aceite = entrada(3, 4, 2, 2, "40");

        @Test
        @DisplayName("12 semanas: travadas e COACH ficam iguais, as demais vêm do motor como ENGINE")
        void criterioDeAceite() {
            PlanoTreino rascunho = travada(rascunho12());
            List<SeasonWeek> semanas = ciclo12(rascunho);
            semanas.get(5).setValueSource(WeekValueSource.COACH);
            semanas.get(5).setTargetVolumeKm(new BigDecimal("50"));
            semanas.get(2).setKeySessions("6x1km");
            List<String> antes = semanas.stream().map(SeasonPlanAdjustmentServiceImplTest::retrato).toList();

            SeasonPlanResponseDto dto = service.recalculate(atleta.getId(), rascunho.getId(), aceite);

            List<WeekPlan> motor = motor(rascunho, 3, 4, 2, 2, "40");
            for (int i : List.of(0, 1, 5, 11)) {
                assertThat(retrato(semanas.get(i))).as("semana %d", i + 1).isEqualTo(antes.get(i));
            }
            for (int i : List.of(2, 3, 4, 6, 7, 8, 9, 10)) {
                SeasonWeek w = semanas.get(i);
                WeekPlan esperado = motor.get(i);
                assertThat(w.getPhase()).as("semana %d", i + 1).isEqualTo(esperado.phase());
                assertThat(w.getTargetVolumeKm()).isEqualByComparingTo(esperado.targetVolumeKm());
                assertThat(w.getTargetTss()).isEqualTo(esperado.targetTss());
                assertThat(w.getLongRunKm()).isEqualByComparingTo(esperado.longRunKm());
                assertThat(w.isDeload()).isEqualTo(esperado.deload());
                assertThat(w.getValueSource()).isEqualTo(WeekValueSource.ENGINE);
            }
            assertThat(semanas.get(2).getKeySessions()).isEqualTo("6x1km");
            assertThat(semanas.get(6).getPhase()).isEqualTo(TrainingPhase.BUILD);
            assertThat(semanas.get(9).getPhase()).isEqualTo(TrainingPhase.TAPER);
            assertThat(rascunho.getInitialVolumeKm()).isEqualTo(new BigDecimal("40.0"));
            assertThat(rascunho.getPeakVolumeKm()).isEqualByComparingTo(semanas.stream()
                    .map(SeasonWeek::getTargetVolumeKm).max(BigDecimal::compareTo).orElseThrow());
            assertThat(dto.initialVolumeKm()).isEqualByComparingTo("40.0");
            verify(seasonWeekRepository).saveAll(semanas);
            verify(planoTreinoRepository).save(rascunho);
        }

        @Test
        @DisplayName("marcos do rascunho são reusados: milestoneRaceId igual e a descarga continua adiada")
        void reusaMarcos() {
            PlanoTreino rascunho = travada(rascunho12());
            List<SeasonWeek> semanas = ciclo12(rascunho);
            UUID marco = UUID.randomUUID();
            semanas.get(3).setMilestoneRaceId(marco);

            service.recalculate(atleta.getId(), rascunho.getId(), aceite);

            assertThat(semanas.get(3).getMilestoneRaceId()).isEqualTo(marco);
            assertThat(semanas.get(3).isDeload()).isFalse();
            assertThat(semanas.get(4).isDeload()).isTrue();
            verify(provaRepository, never()).findUpcomingByAtletaIdAndTenantId(any(), any(), any());
        }

        @Test
        @DisplayName("recalcular duas vezes com a mesma entrada dá as mesmas semanas")
        void deterministico() {
            PlanoTreino rascunho = travada(rascunho12());
            List<SeasonWeek> semanas = ciclo12(rascunho);
            semanas.get(3).setMilestoneRaceId(UUID.randomUUID());

            service.recalculate(atleta.getId(), rascunho.getId(), aceite);
            List<String> primeira = semanas.stream().map(SeasonPlanAdjustmentServiceImplTest::retrato).toList();
            service.recalculate(atleta.getId(), rascunho.getId(), aceite);

            assertThat(semanas.stream().map(SeasonPlanAdjustmentServiceImplTest::retrato).toList()).isEqualTo(primeira);
        }

        @Test
        @DisplayName("fases que somam 10 num ciclo de 12 respondem 422 com a soma e o total, sem gravar")
        void somaDivergente() {
            PlanoTreino rascunho = travada(rascunho12());
            List<SeasonWeek> semanas = ciclo12(rascunho);
            List<String> antes = semanas.stream().map(SeasonPlanAdjustmentServiceImplTest::retrato).toList();

            assertThatThrownBy(() -> service.recalculate(atleta.getId(), rascunho.getId(), entrada(3, 3, 2, 1, "40")))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("10").hasMessageContaining("12");
            assertNadaGravado(rascunho, semanas, antes);
        }

        @Test
        @DisplayName("Construção na semana 1 concluída como Base responde 422, sem gravar")
        void fasePassadaDivergente() {
            PlanoTreino rascunho = travada(rascunho12());
            List<SeasonWeek> semanas = ciclo12(rascunho);
            List<String> antes = semanas.stream().map(SeasonPlanAdjustmentServiceImplTest::retrato).toList();

            assertThatThrownBy(() -> service.recalculate(atleta.getId(), rascunho.getId(), entrada(0, 7, 2, 2, "40")))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("semana 1");
            assertNadaGravado(rascunho, semanas, antes);
        }

        @Test
        @DisplayName("prova remarcada (número de semanas mudou) responde 422")
        void provaRemarcada() {
            PlanoTreino rascunho = travada(rascunho12());
            List<SeasonWeek> semanas = ciclo12(rascunho);
            List<String> antes = semanas.stream().map(SeasonPlanAdjustmentServiceImplTest::retrato).toList();
            rascunho.getProva().setDataProva(rascunho.getProva().getDataProva().plusWeeks(1));

            assertThatThrownBy(() -> service.recalculate(atleta.getId(), rascunho.getId(), entrada(4, 4, 2, 2, "40")))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("descarte");
            assertNadaGravado(rascunho, semanas, antes);
        }

        @ParameterizedTest(name = "initialCtl={0}")
        @NullSource
        @ValueSource(doubles = {0.0, -1.0})
        @DisplayName("CTL de partida nulo ou não positivo responde 422")
        void ctlInvalido(Double ctl) {
            PlanoTreino rascunho = travada(rascunho12());
            rascunho.setInitialCtl(ctl);
            ciclo12(rascunho);

            assertThatThrownBy(() -> service.recalculate(atleta.getId(), rascunho.getId(), aceite))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("CTL");
            verify(seasonWeekRepository, never()).saveAll(any());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("br.com.menthoros.backend.services.impl.SeasonPlanAdjustmentServiceImplTest#entradasInvalidas")
        @DisplayName("entrada inválida responde 422 antes do lock")
        void entradaInvalida(String caso, SeasonPlanRecalculateInputDto entrada) {
            UUID id = UUID.randomUUID();

            assertThatThrownBy(() -> service.recalculate(atleta.getId(), id, entrada))
                    .isInstanceOf(DomainRuleViolationException.class);
            verifyNoInteractions(provaRepository, seasonWeekRepository);
        }

        @Test
        @DisplayName("temporada ACTIVE responde 409")
        void ativa() {
            PlanoTreino ativa = travada(temporada(SeasonPlanStatus.ACTIVE, 2));

            assertThatThrownBy(() -> service.recalculate(atleta.getId(), ativa.getId(), aceite))
                    .isInstanceOf(DomainConflictException.class);
            verify(seasonWeekRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("proposta v1 (sem base) responde 409")
        void propostaV1() {
            PlanoTreino proposta = travada(temporada(SeasonPlanStatus.PROPOSED, 1));

            assertThatThrownBy(() -> service.recalculate(atleta.getId(), proposta.getId(), aceite))
                    .isInstanceOf(DomainConflictException.class);
        }

        @Test
        @DisplayName("flag desligada recusa sem tocar o banco")
        void desligada() {
            assertThatThrownBy(() -> novo(false).recalculate(atleta.getId(), UUID.randomUUID(), aceite))
                    .isInstanceOf(SeasonPlanDisabledException.class);
            verifyNoInteractions(planoTreinoRepository, provaRepository, seasonWeekRepository);
        }

        private void assertNadaGravado(PlanoTreino rascunho, List<SeasonWeek> semanas, List<String> antes) {
            assertThat(semanas.stream().map(SeasonPlanAdjustmentServiceImplTest::retrato).toList()).isEqualTo(antes);
            assertThat(rascunho.getInitialVolumeKm()).isNull();
            verify(seasonWeekRepository, never()).saveAll(any());
            verify(planoTreinoRepository, never()).save(any());
        }
    }

    static Stream<Arguments> entradasInvalidas() {
        Map<TrainingPhase, Integer> semTaper = new EnumMap<>(Map.of(TrainingPhase.BASE, 3, TrainingPhase.BUILD, 4,
                TrainingPhase.PEAK, 2));
        Map<TrainingPhase, Integer> comProva = new EnumMap<>(fases(3, 4, 2, 1));
        comProva.put(TrainingPhase.RACE_WEEK, 1);
        return Stream.of(
                Arguments.of("sem entrada", null),
                Arguments.of("sem fases", new SeasonPlanRecalculateInputDto(null, new BigDecimal("40"))),
                Arguments.of("fase ausente", new SeasonPlanRecalculateInputDto(semTaper, new BigDecimal("40"))),
                Arguments.of("fase negativa", entrada(3, -1, 2, 2, "40")),
                Arguments.of("fase fora das quatro", new SeasonPlanRecalculateInputDto(comProva, new BigDecimal("40"))),
                Arguments.of("sem Base, Construção nem Pico", entrada(0, 0, 0, 11, "40")),
                Arguments.of("volume ausente", new SeasonPlanRecalculateInputDto(fases(3, 4, 2, 2), null)),
                Arguments.of("volume zero", entrada(3, 4, 2, 2, "0")),
                Arguments.of("volume negativo", entrada(3, 4, 2, 2, "-5")),
                Arguments.of("volume acima de 500", entrada(3, 4, 2, 2, "500.1")),
                Arguments.of("volume com duas casas", entrada(3, 4, 2, 2, "40.25")));
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

    /**
     * Rascunho v2 de 12 semanas: a 1 (de {@code HOJE - 1 semana}) concluída, a 2 em andamento e a 12 da
     * prova; CTL de partida 40 e sem volume de partida gravado.
     */
    private PlanoTreino rascunho12() {
        PlanoTreino r = temporada(SeasonPlanStatus.PROPOSED, 2);
        r.setBaseSeasonPlanId(UUID.randomUUID());
        r.setInitialCtl(40.0);
        r.setTotalWeeks(12);
        r.getProva().setDataProva(HOJE.plusWeeks(10).plusDays(6));
        return r;
    }

    private List<SeasonWeek> ciclo12(PlanoTreino t) {
        List<SeasonWeek> lista = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            SeasonWeek w = semana(t, i, "40");
            w.setWeekStart(HOJE.plusWeeks(i - 2L));
            w.setPhase(i == 12 ? TrainingPhase.RACE_WEEK : TrainingPhase.BASE);
            lista.add(w);
        }
        when(seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(t.getId(), tenantId)).thenReturn(lista);
        return lista;
    }

    /** Semanas que o motor daria ao rascunho de {@link #rascunho12()} — o oráculo do recálculo. */
    private static List<WeekPlan> motor(PlanoTreino t, int base, int build, int peak, int taper, String km) {
        return SeasonPlanner.planWithPhases(t.getProva().getDistancia(), null, t.getProva().getDataProva(),
                HOJE.minusWeeks(1), t.getInitialCtl(), new BigDecimal(km), List.of(),
                fases(base, build, peak, taper)).weeks();
    }

    private static String retrato(SeasonWeek w) {
        return w.getWeekIndex() + "|" + w.getPhase() + "|" + w.getTargetVolumeKm() + "|" + w.getTargetTss() + "|"
                + w.getLongRunKm() + "|" + w.isDeload() + "|" + w.getKeySessions() + "|" + w.getValueSource() + "|"
                + w.getMilestoneRaceId();
    }

    private static Map<TrainingPhase, Integer> fases(int base, int build, int peak, int taper) {
        return new EnumMap<>(Map.of(TrainingPhase.BASE, base, TrainingPhase.BUILD, build,
                TrainingPhase.PEAK, peak, TrainingPhase.TAPER, taper));
    }

    private static SeasonPlanRecalculateInputDto entrada(int base, int build, int peak, int taper, String km) {
        return new SeasonPlanRecalculateInputDto(fases(base, build, peak, taper), new BigDecimal(km));
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
