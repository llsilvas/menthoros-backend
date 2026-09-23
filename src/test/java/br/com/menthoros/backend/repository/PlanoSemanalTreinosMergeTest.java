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
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão 22/09 17:46: o {@code merge} do Hibernate chama {@code clear()} na coleção gerenciada.
 * Com {@code treinosPlanejados} vindo de {@code Stream.toList()} (imutável), o segundo save
 * estourava {@code UnsupportedOperationException} — e derrubava a geração DEPOIS de o plano já ter
 * sido aprovado pela validação, a 55 s de LLM de distância.
 */
@Transactional
@DisplayName("PlanoSemanal — merge com treinosPlanejados")
class PlanoSemanalTreinosMergeTest extends AbstractIntegrationTest {

    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired
    private PlanoSemanalRepository planoSemanalRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("lista imutável de treinos não estoura ao salvar o plano já gerenciado")
    void salvarDuasVezesComListaImutavel() {
        PlanoSemanal plano = novoPlano();
        Atleta atleta = plano.getAtleta();

        // Exatamente o que o PlanGenerationPersister faz: Stream.toList(), imutável.
        plano.setTreinosPlanejados(List.of(
                treino(plano, atleta, DiaSemana.TERCA),
                treino(plano, atleta, DiaSemana.QUINTA)));
        PlanoSemanal salvo = planoSemanalRepository.save(plano);
        entityManager.flush();

        // Segundo save sobre a entidade já gerenciada: o merge chama clear() na coleção.
        planoSemanalRepository.save(salvo);
        entityManager.flush();
        entityManager.clear();

        assertThat(planoSemanalRepository.findById(salvo.getId()).orElseThrow().getTreinosPlanejados())
                .hasSize(2);
    }

    private TreinoPlanejado treino(PlanoSemanal plano, Atleta atleta, DiaSemana dia) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setPlanoSemanal(plano);
        treino.setAtleta(atleta);
        treino.setTenantId(plano.getAssessoria().getId());
        treino.setDiaSemana(dia);
        treino.setTipoTreino(TipoTreino.CONTINUO);
        treino.setDuracaoMin(Duration.ofMinutes(40));
        treino.setDataTreino(LocalDate.of(2026, 9, 29));
        return treino;
    }

    private PlanoSemanal novoPlano() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Merge");
        assessoria.setDominio("merge-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Merge");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(assessoria);
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(LocalDate.of(2026, 9, 28));
        plano.setSemanaFim(LocalDate.of(2026, 10, 4));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(30));
        plano.setStatus(PlanoStatus.PLANEJADO);
        plano.setReviewStatus(br.com.menthoros.backend.enums.PlanoReviewStatus.AGUARDANDO_REVISAO);
        return plano;
    }
}
