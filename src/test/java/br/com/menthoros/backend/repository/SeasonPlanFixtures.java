package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.WeekValueSource;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * Montagem do grafo mínimo do plano de temporada para testes contra Postgres real
 * (assessoria → atleta → prova → temporada → semanas / planos semanais).
 */
public final class SeasonPlanFixtures {

    private final EntityManager em;

    public SeasonPlanFixtures(EntityManager em) {
        this.em = em;
    }

    public Assessoria assessoria() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria temporada");
        assessoria.setDominio("temporada-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        em.persist(assessoria);
        return assessoria;
    }

    public Atleta atleta(Assessoria assessoria) {
        Atleta atleta = new Atleta();
        atleta.setNome("Atleta temporada");
        atleta.setEmail("temporada-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Maratona");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        em.persist(atleta);
        return atleta;
    }

    public Prova prova(Atleta atleta) {
        Prova prova = new Prova();
        prova.setNomeProva("Maratona alvo");
        prova.setDataProva(LocalDate.now().plusWeeks(16));
        prova.setDistancia(DistanciaProva.KM_42);
        prova.setTipoProva(TipoProva.CORRIDA_RUA);
        prova.setProvaAlvo(true);
        prova.setAtleta(atleta);
        prova.setAssessoria(atleta.getAssessoria());
        em.persist(prova);
        return prova;
    }

    /** {@code status} nulo = PlanoTreino legado, anterior ao plano de temporada. */
    public PlanoTreino planoTreino(Atleta atleta, Prova prova, SeasonPlanStatus status) {
        PlanoTreino plano = new PlanoTreino();
        plano.setAtleta(atleta);
        plano.setProva(prova);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setNome("Temporada " + prova.getNomeProva());
        plano.setDescricao("Plano de temporada");
        plano.setObjetivo("Completar a prova");
        plano.setDataInicio(LocalDate.now());
        plano.setStatus(PlanoStatus.ATIVO);
        plano.setSeasonStatus(status);
        em.persist(plano);
        return plano;
    }

    public SeasonWeek semana(PlanoTreino plano, int index) {
        SeasonWeek semana = new SeasonWeek();
        semana.setTenantId(plano.getAssessoria().getId());
        semana.setSeasonPlan(plano);
        semana.setWeekIndex(index);
        semana.setWeekStart(plano.getDataInicio().plusWeeks(index - 1L));
        semana.setPhase(TrainingPhase.BASE);
        semana.setTargetVolumeKm(BigDecimal.valueOf(40));
        semana.setValueSource(WeekValueSource.ENGINE);
        em.persist(semana);
        return semana;
    }

    public PlanoSemanal planoSemanalAprovado(PlanoTreino plano) {
        Atleta atleta = plano.getAtleta();
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(atleta.getAssessoria());
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        em.persist(meta);

        PlanoSemanal semanal = new PlanoSemanal();
        semanal.setAtleta(atleta);
        semanal.setAssessoria(atleta.getAssessoria());
        semanal.setPlanoMetaDados(meta);
        semanal.setPlanoTreino(plano);
        semanal.setSemanaInicio(plano.getDataInicio());
        semanal.setSemanaFim(plano.getDataInicio().plusDays(6));
        semanal.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        semanal.setStatus(PlanoStatus.EM_ANDAMENTO);
        semanal.setReviewStatus(PlanoReviewStatus.APROVADO);
        semanal.setObjetivoSemanal("Semana da temporada");
        em.persist(semanal);
        return semanal;
    }

    /**
     * Histórico de volume para a geração (season-plan-start-volume): nas 8 semanas completas antes da de
     * {@code hoje}, quatro treinos FACIL de 9 + 9 + 9 + 8 km — mediana de 35 km e longão de partida de 9 km.
     */
    public void historicoDeVolume(Atleta atleta, LocalDate hoje) {
        LocalDate segunda = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String[] kms = {"9", "9", "9", "8"};
        for (int semana = 1; semana <= 8; semana++) {
            for (int dia = 0; dia < kms.length; dia++) {
                TreinoRealizado t = new TreinoRealizado();
                t.setAtleta(atleta);
                t.setDataTreino(segunda.minusWeeks(semana).plusDays(dia));
                t.setDiaSemana(DiaSemana.SEGUNDA);
                t.setTipoTreino(TipoTreino.FACIL);
                t.setDuracaoMin(Duration.ofMinutes(50));
                t.setDistanciaKm(new BigDecimal(kms[dia]));
                t.setTenantId(atleta.getAssessoria().getId());
                em.persist(t);
            }
        }
    }
}
