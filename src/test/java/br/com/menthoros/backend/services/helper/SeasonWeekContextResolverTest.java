package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.planner.SeasonWeekSnapshot;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.repository.PlanoTreinoRepository;
import br.com.menthoros.backend.repository.SeasonWeekRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeasonWeekContextResolverTest {

    private static final LocalDate SEMANA = LocalDate.of(2026, 12, 7);

    @Mock private PlanoTreinoRepository planoTreinoRepository;
    @Mock private SeasonWeekRepository seasonWeekRepository;

    private UUID tenantId;
    private Atleta atleta;
    private PlanoTreino temporada;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setProvas(new ArrayList<>());
        temporada = new PlanoTreino();
        temporada.setId(UUID.randomUUID());
    }

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("flag desligada não consulta nada e devolve vazio")
        void flagDesligada() {
            Optional<SeasonWeekSnapshot> resultado = resolver(false).resolve(atleta, tenantId, SEMANA);

            assertThat(resultado).isEmpty();
            verifyNoInteractions(planoTreinoRepository, seasonWeekRepository);
        }

        @Test
        @DisplayName("sem temporada ACTIVE (inclusive com só PROPOSED) devolve vazio")
        void semTemporadaAtiva() {
            when(planoTreinoRepository.findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(
                    atleta.getId(), tenantId, List.of(SeasonPlanStatus.ACTIVE))).thenReturn(List.of());

            assertThat(resolver(true).resolve(atleta, tenantId, SEMANA)).isEmpty();
            verifyNoInteractions(seasonWeekRepository);
        }

        @Test
        @DisplayName("semana do plano fora da temporada devolve vazio")
        void semanaForaDaTemporada() {
            temporadaAtivaCom(semana(1, SEMANA.minusWeeks(2), null), semana(2, SEMANA.minusWeeks(1), null));

            assertThat(resolver(true).resolve(atleta, tenantId, SEMANA)).isEmpty();
        }

        @Test
        @DisplayName("semana da temporada vira snapshot com N de M e o próximo marco à frente")
        void semanaComMarco() {
            Prova preparatoria = prova("Meia de Natal", SEMANA.plusWeeks(1).plusDays(6), ProvaStatus.CONFIRMADA);
            atleta.getProvas().add(preparatoria);
            SeasonWeek atual = semana(2, SEMANA, null);
            atual.setTargetTss(320.0);
            atual.setTargetVolumeKm(new BigDecimal("45.000"));
            atual.setLongRunKm(new BigDecimal("16.000"));
            atual.setDeload(true);
            temporadaAtivaCom(semana(1, SEMANA.minusWeeks(1), null), atual,
                    semana(3, SEMANA.plusWeeks(1), preparatoria.getId()), semana(4, SEMANA.plusWeeks(2), null));

            SeasonWeekSnapshot snapshot = resolver(true).resolve(atleta, tenantId, SEMANA).orElseThrow();

            assertThat(snapshot.seasonWeekId()).isEqualTo(atual.getId());
            assertThat(snapshot.weekIndex()).isEqualTo(2);
            assertThat(snapshot.totalWeeks()).isEqualTo(4);
            assertThat(snapshot.phase()).isEqualTo(TrainingPhase.BUILD);
            assertThat(snapshot.targetTss()).isEqualTo(320.0);
            assertThat(snapshot.targetVolumeKm()).isEqualByComparingTo("45");
            assertThat(snapshot.longRunKm()).isEqualByComparingTo("16");
            assertThat(snapshot.deload()).isTrue();
            assertThat(snapshot.nextMilestone()).contains(
                    new SeasonWeekSnapshot.Milestone("Meia de Natal", SEMANA.plusWeeks(1).plusDays(6)));
        }

        @Test
        @DisplayName("marco já passado, cancelado ou remarcado para fora da semana não conta")
        void semMarcoValidoAFrente() {
            Prova passada = prova("Passada", SEMANA.minusDays(3), ProvaStatus.CONFIRMADA);
            Prova cancelada = prova("Cancelada", SEMANA.plusWeeks(1).plusDays(2), ProvaStatus.CANCELADA);
            Prova remarcada = prova("Remarcada", SEMANA.plusWeeks(5), ProvaStatus.CONFIRMADA);
            atleta.getProvas().addAll(List.of(passada, cancelada, remarcada));
            temporadaAtivaCom(semana(1, SEMANA.minusWeeks(1), passada.getId()), semana(2, SEMANA, null),
                    semana(3, SEMANA.plusWeeks(1), cancelada.getId()), semana(4, SEMANA.plusWeeks(2), remarcada.getId()));

            SeasonWeekSnapshot snapshot = resolver(true).resolve(atleta, tenantId, SEMANA).orElseThrow();

            assertThat(snapshot.nextMilestone()).isEmpty();
        }
    }

    private SeasonWeekContextResolver resolver(boolean enabled) {
        return new SeasonWeekContextResolver(planoTreinoRepository, seasonWeekRepository, enabled);
    }

    private void temporadaAtivaCom(SeasonWeek... semanas) {
        when(planoTreinoRepository.findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(
                atleta.getId(), tenantId, List.of(SeasonPlanStatus.ACTIVE))).thenReturn(List.of(temporada));
        when(seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(eq(temporada.getId()), eq(tenantId)))
                .thenReturn(List.of(semanas));
    }

    private static SeasonWeek semana(int index, LocalDate inicio, UUID marco) {
        SeasonWeek w = new SeasonWeek();
        w.setId(UUID.randomUUID());
        w.setWeekIndex(index);
        w.setWeekStart(inicio);
        w.setPhase(TrainingPhase.BUILD);
        w.setMilestoneRaceId(marco);
        return w;
    }

    private static Prova prova(String nome, LocalDate data, ProvaStatus status) {
        Prova p = new Prova();
        p.setId(UUID.randomUUID());
        p.setNomeProva(nome);
        p.setDataProva(data);
        p.setStatusProva(status);
        return p;
    }
}
