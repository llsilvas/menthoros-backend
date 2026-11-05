package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O perfil do coach lê {@code treinoRealizadoId} de cada treino do plano vigente
 * (coach-ver-treino-realizado, task 1.5). O vínculo é o lado inverso de um {@code @OneToOne}: sem
 * fetch join, o Hibernate faz um SELECT por treino planejado para resolvê-lo.
 */
@Transactional
class PlanoSemanalRepositoryVigenteRealizadoIT extends AbstractIntegrationTest {

    @Autowired private PlanoSemanalRepository planoSemanalRepository;
    @Autowired private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Autowired private TreinoRealizadoRepository treinoRealizadoRepository;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("plano vigente traz o realizado de cada treino na mesma consulta")
    void planoVigenteTrazRealizadoSemConsultaPorTreino() {
        Atleta atleta = seedAtleta();
        PlanoSemanal plano = seedPlano(atleta);
        UUID realizado1 = seedTreino(plano, atleta, DiaSemana.SEGUNDA, true);
        UUID realizado2 = seedTreino(plano, atleta, DiaSemana.QUARTA, true);
        seedTreino(plano, atleta, DiaSemana.SEXTA, false);
        entityManager.flush();
        entityManager.clear();

        Statistics stats = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        PlanoSemanal vigente = planoSemanalRepository
                .findMostRecentRelevantPlano(atleta.getId(), atleta.getAssessoria().getId())
                .orElseThrow();
        var vinculos = vigente.getTreinosPlanejados().stream()
                .map(tp -> tp.getTreinoRealizado() != null ? tp.getTreinoRealizado().getId() : null)
                .toList();

        assertThat(vinculos).containsExactlyInAnyOrder(realizado1, realizado2, null);
        assertThat(stats.getPrepareStatementCount())
                .as("sem fetch join, um SELECT extra por treino planejado")
                .isEqualTo(1);
    }

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Vigente Realizado");
        assessoria.setDominio("vigente-realizado-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Vigente");
        atleta.setEmail("vigente-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 21km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private PlanoSemanal seedPlano(Atleta atleta) {
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(atleta.getAssessoria());
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(LocalDate.now().minusDays(2));
        plano.setSemanaFim(LocalDate.now().plusDays(4));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(30));
        plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setObjetivoSemanal("Semana de teste");
        return planoSemanalRepository.save(plano);
    }

    private UUID seedTreino(PlanoSemanal plano, Atleta atleta, DiaSemana dia, boolean comRealizado) {
        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setPlanoSemanal(plano);
        planejado.setAtleta(atleta);
        planejado.setTenantId(atleta.getAssessoria().getId());
        planejado.setDataTreino(LocalDate.now());
        planejado.setDiaSemana(dia);
        planejado.setTipoTreino(TipoTreino.FACIL);
        planejado.setDuracaoMin(Duration.ofMinutes(45));
        planejado.setStatusSincronizacao(StatusSincronizacao.NAO_SINCRONIZADO);
        planejado.setTentativasSincronizacao(0);
        planejado = treinoPlanejadoRepository.save(planejado);
        if (!comRealizado) return null;

        TreinoRealizado realizado = new TreinoRealizado();
        realizado.setAtleta(atleta);
        realizado.setTenantId(atleta.getAssessoria().getId());
        realizado.setDataTreino(LocalDate.now());
        realizado.setDiaSemana(dia);
        realizado.setTipoTreino(TipoTreino.FACIL);
        realizado.setDuracaoMin(Duration.ofMinutes(45));
        realizado.setDistanciaKm(BigDecimal.valueOf(8));
        realizado.setFonteDados(FonteDados.GARMIN);
        realizado.setStatus(TreinoExecucaoStatus.REALIZADO);
        realizado.setTreinoPlanejado(planejado);
        return treinoRealizadoRepository.save(realizado).getId();
    }
}
