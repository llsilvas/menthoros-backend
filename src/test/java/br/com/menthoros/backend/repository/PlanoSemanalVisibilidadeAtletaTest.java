package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.MotivoReaberturaRevisao;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D4 (prova-no-plano-semanal): {@code findVisiveisParaAtletaOrderBySemanaInicioDesc} — o atleta
 * continua vendo o plano da semana corrente quando ele foi reaberto por uma prova, em vez de cair
 * na semana anterior (achado do levantamento, tratado como requisito).
 */
@Transactional
class PlanoSemanalVisibilidadeAtletaTest extends AbstractIntegrationTest {

    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("findVisiveisParaAtletaOrderBySemanaInicioDesc")
    class VisiveisParaAtleta {

        @Test
        @DisplayName("plano da semana corrente reaberto por prova é devolvido, não o aprovado da semana anterior")
        void devolvePlanoReabertoDaSemanaCorrente() {
            Atleta atleta = seedAtleta();
            LocalDate hoje = LocalDate.now();

            salvarPlano(atleta, PlanoStatus.EM_ANDAMENTO, PlanoReviewStatus.APROVADO, hoje.minusWeeks(1), null);
            PlanoSemanal reaberto = salvarPlano(atleta, PlanoStatus.PLANEJADO,
                    PlanoReviewStatus.AGUARDANDO_REVISAO, hoje, MotivoReaberturaRevisao.PROVA_INSERIDA);
            entityManager.flush();

            List<PlanoSemanal> resultado = planoSemanalRepository
                    .findVisiveisParaAtletaOrderBySemanaInicioDesc(atleta.getId(), atleta.getAssessoria().getId());

            // A lista tem os dois (aprovado antigo + reaberto atual) — quem escolhe "o mais
            // recente" é o service (.stream().findFirst()); aqui a garantia é que o reaberto
            // vem PRIMEIRO, na frente do aprovado antigo, e não fica de fora da lista.
            assertThat(resultado.getFirst().getId()).isEqualTo(reaberto.getId());
        }

        @Test
        @DisplayName("plano AGUARDANDO_REVISAO nunca aprovado continua invisível, cai no aprovado anterior")
        void planoNuncaAprovadoContinuaInvisivel() {
            Atleta atleta = seedAtleta();
            LocalDate hoje = LocalDate.now();

            PlanoSemanal aprovadoAntigo = salvarPlano(atleta, PlanoStatus.EM_ANDAMENTO,
                    PlanoReviewStatus.APROVADO, hoje.minusWeeks(1), null);
            salvarPlano(atleta, PlanoStatus.PLANEJADO, PlanoReviewStatus.AGUARDANDO_REVISAO, hoje, null);
            entityManager.flush();

            List<PlanoSemanal> resultado = planoSemanalRepository
                    .findVisiveisParaAtletaOrderBySemanaInicioDesc(atleta.getId(), atleta.getAssessoria().getId());

            assertThat(resultado).extracting(PlanoSemanal::getId).containsExactly(aprovadoAntigo.getId());
        }

        @Test
        @DisplayName("não vaza plano de outro tenant")
        void naoVazaOutroTenant() {
            Atleta atleta = seedAtleta();
            Atleta outroAtleta = seedAtleta();
            LocalDate hoje = LocalDate.now();

            salvarPlano(outroAtleta, PlanoStatus.EM_ANDAMENTO, PlanoReviewStatus.APROVADO, hoje, null);
            entityManager.flush();

            List<PlanoSemanal> resultado = planoSemanalRepository
                    .findVisiveisParaAtletaOrderBySemanaInicioDesc(atleta.getId(), atleta.getAssessoria().getId());

            assertThat(resultado).isEmpty();
        }
    }

    // ---- helpers (espelha PlanoSemanalBuscaAtualTest) ----

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Visibilidade Atleta");
        assessoria.setDominio("visibilidade-atleta-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Visibilidade");
        atleta.setEmail("visibilidade-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private PlanoSemanal salvarPlano(Atleta atleta, PlanoStatus status, PlanoReviewStatus reviewStatus,
                                     LocalDate semanaFim, MotivoReaberturaRevisao motivoReabertura) {
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(atleta.getAssessoria());
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(semanaFim.minusDays(6));
        plano.setSemanaFim(semanaFim);
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setStatus(status);
        plano.setReviewStatus(reviewStatus);
        plano.setObjetivoSemanal("Semana base");
        plano.setMotivoReabertura(motivoReabertura);
        return planoSemanalRepository.save(plano);
    }
}
