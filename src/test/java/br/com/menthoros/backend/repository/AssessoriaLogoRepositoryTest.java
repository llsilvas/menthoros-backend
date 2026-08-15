package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.AssessoriaLogo;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tabela separada existe por um motivo mecânico: manter os bytes fora de qualquer
 * {@code SELECT} de {@link Assessoria}, que é carregada em caminhos quentes. Se um dia alguém
 * mover o LOB para a entidade principal "para simplificar", o teste de isolamento abaixo quebra.
 */
@Transactional
class AssessoriaLogoRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AssessoriaLogoRepository logoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("isolamento dos bytes")
    class IsolamentoDosBytes {

        @Test
        @DisplayName("carregar a Assessoria não toca tb_assessoria_logo")
        void carregarAssessoriaNaoTocaLogo() {
            Assessoria assessoria = seedAssessoriaComLogo();
            entityManager.flush();
            entityManager.clear();

            Statistics stats = entityManager.unwrap(Session.class)
                    .getSessionFactory().getStatistics();
            stats.setStatisticsEnabled(true);
            stats.clear();

            Assessoria recarregada = assessoriaRepository.findById(assessoria.getId()).orElseThrow();
            assertThat(recarregada.getNome()).isNotBlank();

            assertThat(stats.getEntityLoadCount())
                    .as("apenas a Assessoria deve ter sido carregada, nunca a logo")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("existsByAssessoriaId responde sem carregar o conteúdo")
        void existsNaoCarregaConteudo() {
            Assessoria assessoria = seedAssessoriaComLogo();
            entityManager.flush();
            entityManager.clear();

            assertThat(logoRepository.existsByAssessoriaId(assessoria.getId())).isTrue();
            assertThat(logoRepository.existsByAssessoriaId(UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("findEtagByAssessoriaId devolve só o hash")
        void etagSemConteudo() {
            Assessoria assessoria = seedAssessoriaComLogo();
            entityManager.flush();
            entityManager.clear();

            assertThat(logoRepository.findEtagByAssessoriaId(assessoria.getId()))
                    .contains("etag-de-teste");
        }
    }

    @Nested
    @DisplayName("ciclo de vida")
    class CicloDeVida {

        /**
         * {@code ON DELETE CASCADE} não é conveniência: sem ele, apagar uma assessoria deixaria
         * bytes órfãos que nenhuma consulta da aplicação alcança — invisíveis e permanentes.
         */
        @Test
        @DisplayName("apagar a assessoria leva a logo junto")
        void cascadeNaExclusao() {
            Assessoria assessoria = seedAssessoriaComLogo();
            entityManager.flush();
            UUID id = assessoria.getId();

            entityManager.createNativeQuery("DELETE FROM tb_assessoria WHERE id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
            entityManager.clear();

            assertThat(logoRepository.existsByAssessoriaId(id)).isFalse();
        }

        @Test
        @DisplayName("substituir a logo é UPDATE, não linha nova")
        void substituicaoNaoAcumula() {
            Assessoria assessoria = seedAssessoriaComLogo();
            entityManager.flush();
            entityManager.clear();

            AssessoriaLogo nova = logoRepository.findById(assessoria.getId()).orElseThrow();
            nova.setContent("outro-conteudo".getBytes(StandardCharsets.UTF_8));
            nova.setEtag("etag-novo");
            logoRepository.saveAndFlush(nova);
            entityManager.clear();

            assertThat(logoRepository.count()).isEqualTo(1L);
            assertThat(logoRepository.findEtagByAssessoriaId(assessoria.getId()))
                    .contains("etag-novo");
        }
    }

    private Assessoria seedAssessoriaComLogo() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Logo Test");
        assessoria.setDominio("logo-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        byte[] conteudo = "conteudo-binario-de-teste".getBytes(StandardCharsets.UTF_8);
        logoRepository.save(AssessoriaLogo.builder()
                .assessoriaId(assessoria.getId())
                .content(conteudo)
                .contentType("image/png")
                .sizeBytes(conteudo.length)
                .etag("etag-de-teste")
                .updatedAt(OffsetDateTime.now())
                .build());

        return assessoria;
    }
}
