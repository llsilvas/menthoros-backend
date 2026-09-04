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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V89 (prova-no-plano-semanal, D4): {@code motivo_reabertura} e {@code reaberto_em} em
 * {@code tb_plano_semanal} — as duas colunas nascem nulas e sobrevivem a um reload com o valor
 * gravado.
 */
@Transactional
class PlanoSemanalReaberturaMigrationTest extends AbstractIntegrationTest {

    @Autowired private PlanoSemanalRepository planoSemanalRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("colunas motivo_reabertura e reaberto_em")
    class Colunas {

        @Test
        @DisplayName("plano pré-existente (sem reabertura) nasce com as duas colunas nulas")
        void planoNasceSemReabertura() {
            Atleta atleta = seedAtleta();
            PlanoSemanal plano = seedPlano(atleta, null, null);
            entityManager.flush();

            Map<String, Object> linha = jdbc.queryForMap(
                    "SELECT motivo_reabertura, reaberto_em FROM tb_plano_semanal WHERE id = ?", plano.getId());

            assertThat(linha.get("motivo_reabertura")).isNull();
            assertThat(linha.get("reaberto_em")).isNull();
        }

        @Test
        @DisplayName("motivo e carimbo gravados sobrevivem a um reload")
        void motivoESobrevivemAoReload() {
            Atleta atleta = seedAtleta();
            LocalDateTime agora = LocalDateTime.now().withNano(0);
            PlanoSemanal plano = seedPlano(atleta, MotivoReaberturaRevisao.PROVA_INSERIDA, agora);

            entityManager.flush();
            entityManager.clear();

            PlanoSemanal recarregado = planoSemanalRepository.findById(plano.getId()).orElseThrow();

            assertThat(recarregado.getMotivoReabertura()).isEqualTo(MotivoReaberturaRevisao.PROVA_INSERIDA);
            assertThat(recarregado.getReabertoEm()).isEqualTo(agora);
        }
    }

    // ---- helpers (espelha TreinoPlanejadoRepositoryTest) ----

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Reabertura");
        assessoria.setDominio("reabertura-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Reabertura");
        atleta.setEmail("reabertura-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private PlanoSemanal seedPlano(Atleta atleta, MotivoReaberturaRevisao motivo, LocalDateTime reabertoEm) {
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
        plano.setSemanaFim(LocalDate.now().plusDays(1));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setObjetivoSemanal("Semana de teste da reabertura");
        plano.setMotivoReabertura(motivo);
        plano.setReabertoEm(reabertoEm);
        return planoSemanalRepository.save(plano);
    }
}
