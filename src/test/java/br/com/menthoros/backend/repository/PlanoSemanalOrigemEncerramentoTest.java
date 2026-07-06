package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.OrigemEncerramento;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integração da coluna {@code origem_encerramento} (métrica-farol de adoção): verifica o
 * round-trip do enum contra o schema real, o default nulo (planos não encerrados) e que a
 * query de agregação {@code GROUP BY origem_encerramento} é executável e segmenta as origens.
 */
@Transactional
class PlanoSemanalOrigemEncerramentoTest extends AbstractIntegrationTest {

    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired
    private PlanoSemanalRepository planoSemanalRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("persiste e relê origem ON_DEMAND (round-trip do enum contra o schema real)")
    void roundTripOrigemOnDemand() {
        Atleta atleta = seedAtleta();
        PlanoSemanal plano = salvarPlano(atleta, OrigemEncerramento.ON_DEMAND, PlanoStatus.CONCLUIDO);

        entityManager.flush();
        entityManager.clear();

        PlanoSemanal recarregado = planoSemanalRepository.findById(plano.getId()).orElseThrow();
        assertThat(recarregado.getOrigemEncerramento()).isEqualTo(OrigemEncerramento.ON_DEMAND);
    }

    @Test
    @DisplayName("plano não encerrado tem origem nula (sem backfill)")
    void planoNaoEncerradoTemOrigemNula() {
        Atleta atleta = seedAtleta();
        PlanoSemanal plano = salvarPlano(atleta, null, PlanoStatus.EM_ANDAMENTO);

        entityManager.flush();
        entityManager.clear();

        PlanoSemanal recarregado = planoSemanalRepository.findById(plano.getId()).orElseThrow();
        assertThat(recarregado.getOrigemEncerramento()).isNull();
    }

    @Test
    @DisplayName("a query GROUP BY origem_encerramento é executável e segmenta as origens")
    void queryDeMetricaSegmentaOrigens() {
        Atleta atleta = seedAtleta();
        salvarPlano(atleta, OrigemEncerramento.ON_DEMAND, PlanoStatus.CONCLUIDO);
        salvarPlano(atleta, OrigemEncerramento.AUTOMATICO, PlanoStatus.CONCLUIDO);
        salvarPlano(atleta, null, PlanoStatus.EM_ANDAMENTO);
        entityManager.flush();

        @SuppressWarnings("unchecked")
        List<Object[]> linhas = entityManager.createNativeQuery(
                "SELECT origem_encerramento, COUNT(*) FROM tb_plano_semanal GROUP BY origem_encerramento")
                .getResultList();

        assertThat(linhas).isNotEmpty();
        assertThat(contar(linhas, "ON_DEMAND")).isGreaterThanOrEqualTo(1);
        assertThat(contar(linhas, "AUTOMATICO")).isGreaterThanOrEqualTo(1);
        assertThat(contar(linhas, null)).isGreaterThanOrEqualTo(1); // bucket dos não encerrados
    }

    // ---- helpers ----

    private long contar(List<Object[]> linhas, String origem) {
        return linhas.stream()
                .filter(l -> origem == null ? l[0] == null : origem.equals(l[0]))
                .mapToLong(l -> ((Number) l[1]).longValue())
                .sum();
    }

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Origem Test");
        assessoria.setDominio("origem-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Origem");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private PlanoSemanal salvarPlano(Atleta atleta, OrigemEncerramento origem, PlanoStatus status) {
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(atleta.getAssessoria());
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(LocalDate.now().minusDays(6));
        plano.setSemanaFim(LocalDate.now());
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setStatus(status);
        plano.setReviewStatus(PlanoReviewStatus.AGUARDANDO_REVISAO);
        plano.setObjetivoSemanal("Semana base");
        plano.setOrigemEncerramento(origem);
        return planoSemanalRepository.save(plano);
    }
}
