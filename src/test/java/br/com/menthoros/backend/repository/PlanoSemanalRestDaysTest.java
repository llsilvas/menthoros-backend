package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.domain.plano.RestDay;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoStatus;
import jakarta.persistence.EntityManager;
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
 * {@code tb_plano_semanal.rest_days} (V97, add-descanso-explicito-por-fadiga): ida e volta do JSONB
 * com Postgres de verdade — a lista é de records, não de String como o outro campo JSON da tabela.
 */
@Transactional
@DisplayName("PlanoSemanal — rest_days (JSONB)")
class PlanoSemanalRestDaysTest extends AbstractIntegrationTest {

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
    @DisplayName("CA8: descanso gravado volta com dia e motivo")
    void idaEVolta() {
        PlanoSemanal plano = salvarPlano(List.of(
                new RestDay("QUINTA", "check-in de hoje: DESCANSAR"),
                new RestDay("SABADO", "36h desde o último intensivo, mínimo 48h")));

        entityManager.flush();
        entityManager.clear();

        PlanoSemanal recarregado = planoSemanalRepository.findById(plano.getId()).orElseThrow();
        assertThat(recarregado.getRestDays())
                .containsExactly(new RestDay("QUINTA", "check-in de hoje: DESCANSAR"),
                        new RestDay("SABADO", "36h desde o último intensivo, mínimo 48h"));
    }

    @Test
    @DisplayName("CA8: plano anterior à feature (coluna nula) é lido como lista vazia")
    void colunaNulaViraListaVazia() {
        PlanoSemanal plano = salvarPlano(null);

        entityManager.flush();
        entityManager.clear();

        PlanoSemanal recarregado = planoSemanalRepository.findById(plano.getId()).orElseThrow();
        assertThat(recarregado.getRestDays()).isNull();
        assertThat(recarregado.getRestDaysOuVazio()).isEmpty();
    }

    @Test
    @DisplayName("lista vazia persiste como vazia, sem virar nula")
    void listaVazia() {
        PlanoSemanal plano = salvarPlano(List.of());

        entityManager.flush();
        entityManager.clear();

        assertThat(planoSemanalRepository.findById(plano.getId()).orElseThrow().getRestDaysOuVazio()).isEmpty();
    }

    @Test
    @DisplayName("regressão 22/09 17:30: atualizar os descansos e salvar de novo não estoura no merge")
    void atualizarDescansosESalvarDeNovo() {
        PlanoSemanal plano = salvarPlano(new java.util.ArrayList<>(List.of(
                new RestDay("QUINTA", "check-in de hoje: DESCANSAR"),
                new RestDay("SABADO", "motivo"))));
        entityManager.flush();

        // O setter normaliza para lista mutável — com List.of o Hibernate estourava
        // UnsupportedOperationException no clear() do merge.
        plano.setRestDays(List.of(new RestDay("QUINTA", "check-in de hoje: DESCANSAR")));
        planoSemanalRepository.save(plano);
        entityManager.flush();
        entityManager.clear();

        assertThat(planoSemanalRepository.findById(plano.getId()).orElseThrow().getRestDaysOuVazio())
                .containsExactly(new RestDay("QUINTA", "check-in de hoje: DESCANSAR"));
    }

    private PlanoSemanal salvarPlano(List<RestDay> restDays) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Rest Days");
        assessoria.setDominio("rest-days-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Rest Days");
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
        plano.setRestDays(restDays);
        return planoSemanalRepository.save(plano);
    }
}
