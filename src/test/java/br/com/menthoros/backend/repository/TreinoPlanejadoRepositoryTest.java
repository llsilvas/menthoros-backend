package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integração real (Testcontainers) da query {@code findAllAguardandoRetryIntervalsIcu}: prova,
 * contra o schema/JPQL executado de verdade (não só o texto da anotação), quais estados de
 * {@link StatusSincronizacao} entram na seleção do retry scheduler (spec 3.3 + 8.2).
 */
@Transactional
class TreinoPlanejadoRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TreinoPlanejadoRepository treinoPlanejadoRepository;
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

    @ParameterizedTest(name = "{0}")
    @EnumSource(StatusSincronizacao.class)
    @DisplayName("findAllAguardandoRetryIntervalsIcu seleciona APENAS AGUARDANDO_RETRY/ERRO_TEMPORARIO/ERRO_LIMITE_RATE")
    void selecionaApenasEstadosDeRetry(StatusSincronizacao status) {
        Atleta atleta = seedAtleta();
        TreinoPlanejado treino = salvarTreino(atleta, status);
        entityManager.flush();
        entityManager.clear();

        List<TreinoPlanejado> resultado = treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu();

        boolean esperadoNaSelecao = status == StatusSincronizacao.AGUARDANDO_RETRY
                || status == StatusSincronizacao.ERRO_TEMPORARIO
                || status == StatusSincronizacao.ERRO_LIMITE_RATE;

        boolean presente = resultado.stream().anyMatch(t -> t.getId().equals(treino.getId()));

        assertThat(presente)
                .as("status %s deveria %sser selecionado pelo retry scheduler", status, esperadoNaSelecao ? "" : "NUNCA ")
                .isEqualTo(esperadoNaSelecao);
    }

    // ---- helpers (espelha PlanoSemanalOrigemEncerramentoTest) ----

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Retry Test");
        assessoria.setDominio("retry-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Retry");
        atleta.setEmail("retry-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private TreinoPlanejado salvarTreino(Atleta atleta, StatusSincronizacao status) {
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
        plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setObjetivoSemanal("Semana de teste");
        plano = planoSemanalRepository.save(plano);

        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setPlanoSemanal(plano);
        treino.setAtleta(atleta);
        treino.setTenantId(atleta.getAssessoria().getId());
        treino.setDataTreino(LocalDate.now());
        treino.setDiaSemana(DiaSemana.SABADO);
        treino.setTipoTreino(TipoTreino.REGENERATIVO);
        treino.setDuracaoMin(Duration.ofMinutes(30));
        treino.setStatusSincronizacao(status);
        treino.setTentativasSincronizacao(0);
        return treinoPlanejadoRepository.save(treino);
    }
}
