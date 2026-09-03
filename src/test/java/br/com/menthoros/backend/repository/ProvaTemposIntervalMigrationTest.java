package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V90 (prova-no-plano-semanal, D6): {@code tempo_objetivo}/{@code tempo_realizado} de
 * {@code tb_prova} viram {@code interval}, e a view {@code v_historico_provas_completadas} (V9,
 * que lê {@code tempo_realizado}) sobrevive ao drop-and-recreate.
 */
@Transactional
class ProvaTemposIntervalMigrationTest extends AbstractIntegrationTest {

    @Autowired private ProvaRepository provaRepository;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("tipo das colunas")
    class TipoColunas {

        @Test
        @DisplayName("tempo_objetivo e tempo_realizado são interval em tb_prova")
        void colunasSaoInterval() {
            String tipoObjetivo = jdbc.queryForObject("""
                    SELECT data_type FROM information_schema.columns
                    WHERE table_name = 'tb_prova' AND column_name = 'tempo_objetivo'
                    """, String.class);
            String tipoRealizado = jdbc.queryForObject("""
                    SELECT data_type FROM information_schema.columns
                    WHERE table_name = 'tb_prova' AND column_name = 'tempo_realizado'
                    """, String.class);

            assertThat(tipoObjetivo).isEqualTo("interval");
            assertThat(tipoRealizado).isEqualTo("interval");
        }
    }

    @Nested
    @DisplayName("view v_historico_provas_completadas")
    class ViewHistorico {

        @Test
        @DisplayName("existe e devolve a prova realizada com tempo_realizado preenchido")
        void viewSobrevive() {
            Atleta atleta = seedAtleta();
            Prova prova = seedProva(atleta, true, Duration.ofHours(1).plusMinutes(48).plusSeconds(30));
            entityManager.flush();

            Integer totalSegundos = jdbc.queryForObject(
                    "SELECT EXTRACT(EPOCH FROM tempo_realizado)::int FROM v_historico_provas_completadas WHERE id = ?",
                    Integer.class, prova.getId());

            assertThat(totalSegundos).isEqualTo((int) Duration.ofHours(1).plusMinutes(48).plusSeconds(30).getSeconds());
        }
    }

    @Nested
    @DisplayName("round-trip da entidade")
    class RoundTrip {

        @Test
        @DisplayName("tempoObjetivo e tempoRealizado sobrevivem a um reload como Duration")
        void sobrevivemAoReload() {
            Atleta atleta = seedAtleta();
            Duration objetivo = Duration.ofHours(1).plusMinutes(45);
            Duration realizado = Duration.ofHours(1).plusMinutes(48).plusSeconds(12);
            Prova prova = seedProva(atleta, true, realizado);
            prova.setTempoObjetivo(objetivo);
            provaRepository.save(prova);
            entityManager.flush();
            entityManager.clear();

            Prova recarregada = provaRepository.findById(prova.getId()).orElseThrow();

            assertThat(recarregada.getTempoObjetivo()).isEqualTo(objetivo);
            assertThat(recarregada.getTempoRealizado()).isEqualTo(realizado);
        }

        @Test
        @DisplayName("tempoObjetivo e tempoRealizado nulos sobrevivem a um reload")
        void nulosSobrevivemAoReload() {
            Atleta atleta = seedAtleta();
            Prova prova = seedProva(atleta, false, null);
            entityManager.flush();
            entityManager.clear();

            Prova recarregada = provaRepository.findById(prova.getId()).orElseThrow();

            assertThat(recarregada.getTempoObjetivo()).isNull();
            assertThat(recarregada.getTempoRealizado()).isNull();
        }
    }

    // ---- helpers (espelha ProvaRepositoryTest) ----

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Tempos Interval");
        assessoria.setDominio("tempos-interval-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Tempos Interval");
        atleta.setEmail("tempos-interval-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private Prova seedProva(Atleta atleta, boolean foiRealizada, Duration tempoRealizado) {
        Prova prova = Prova.builder()
                .nomeProva("Prova Tempos Interval")
                .dataProva(LocalDate.now().minusDays(1))
                .distancia(DistanciaProva.KM_21)
                .tipoProva(TipoProva.MEIA)
                .statusProva(foiRealizada ? ProvaStatus.CONCLUIDA : ProvaStatus.PLANEJADA)
                .foiRealizada(foiRealizada)
                .tempoRealizado(tempoRealizado)
                .build();
        prova.setAtleta(atleta);
        prova.setAssessoria(atleta.getAssessoria());
        return provaRepository.save(prova);
    }
}
