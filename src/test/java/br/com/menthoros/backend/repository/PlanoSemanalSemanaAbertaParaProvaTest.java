package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D5 (prova-no-plano-semanal): {@code findSemanaAbertaParaProva} — tenant, exclui semana
 * encerrada (`CONCLUIDO`) e plano `REJEITADO`.
 */
@Transactional
class PlanoSemanalSemanaAbertaParaProvaTest extends AbstractIntegrationTest {

    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("findSemanaAbertaParaProva")
    class FindSemanaAbertaParaProva {

        @Test
        @DisplayName("encontra a semana aberta que contém a data")
        void encontraSemanaAberta() {
            Atleta atleta = seedAtleta();
            LocalDate hoje = LocalDate.now();
            PlanoSemanal plano = salvarPlano(atleta, PlanoStatus.EM_ANDAMENTO, PlanoReviewStatus.APROVADO, hoje);
            entityManager.flush();

            Optional<PlanoSemanal> resultado = planoSemanalRepository
                    .findSemanaAbertaParaProva(atleta.getId(), atleta.getAssessoria().getId(), hoje.plusDays(2));

            assertThat(resultado).isPresent();
            assertThat(resultado.get().getId()).isEqualTo(plano.getId());
        }

        @Test
        @DisplayName("semana CONCLUIDO não é encontrada")
        void semanaConcluidaNaoEncontrada() {
            Atleta atleta = seedAtleta();
            LocalDate hoje = LocalDate.now();
            salvarPlano(atleta, PlanoStatus.CONCLUIDO, PlanoReviewStatus.APROVADO, hoje);
            entityManager.flush();

            Optional<PlanoSemanal> resultado = planoSemanalRepository
                    .findSemanaAbertaParaProva(atleta.getId(), atleta.getAssessoria().getId(), hoje.plusDays(2));

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("plano REJEITADO não é encontrado")
        void planoRejeitadoNaoEncontrado() {
            Atleta atleta = seedAtleta();
            LocalDate hoje = LocalDate.now();
            salvarPlano(atleta, PlanoStatus.PLANEJADO, PlanoReviewStatus.REJEITADO, hoje);
            entityManager.flush();

            Optional<PlanoSemanal> resultado = planoSemanalRepository
                    .findSemanaAbertaParaProva(atleta.getId(), atleta.getAssessoria().getId(), hoje.plusDays(2));

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("não vaza plano de outro tenant")
        void naoVazaOutroTenant() {
            Atleta atleta = seedAtleta();
            Atleta outroAtleta = seedAtleta();
            LocalDate hoje = LocalDate.now();
            salvarPlano(outroAtleta, PlanoStatus.EM_ANDAMENTO, PlanoReviewStatus.APROVADO, hoje);
            entityManager.flush();

            Optional<PlanoSemanal> resultado = planoSemanalRepository
                    .findSemanaAbertaParaProva(atleta.getId(), atleta.getAssessoria().getId(), hoje.plusDays(2));

            assertThat(resultado).isEmpty();
        }
    }

    // ---- helpers (espelha PlanoSemanalBuscaAtualTest) ----

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Semana Aberta");
        assessoria.setDominio("semana-aberta-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Semana Aberta");
        atleta.setEmail("semana-aberta-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private PlanoSemanal salvarPlano(Atleta atleta, PlanoStatus status, PlanoReviewStatus reviewStatus, LocalDate diaNaSemana) {
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(atleta.getAssessoria());
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(diaNaSemana.minusDays(1));
        plano.setSemanaFim(diaNaSemana.plusDays(5));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setStatus(status);
        plano.setReviewStatus(reviewStatus);
        plano.setObjetivoSemanal("Semana base");
        return planoSemanalRepository.save(plano);
    }
}
