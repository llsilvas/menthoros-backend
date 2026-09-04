package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.mapper.TreinoMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V88 (prova-no-plano-semanal, D1): vínculo persistido {@code TreinoPlanejado.prova}, com
 * {@code ON DELETE SET NULL} e o {@link TreinoMapper} carregando {@code provaId} nos dois
 * sentidos (DTO da LLM → entidade, entidade → DTO de saída).
 */
@Transactional
class TreinoPlanejadoProvaVinculoTest extends AbstractIntegrationTest {

    @Autowired private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Autowired private ProvaRepository provaRepository;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;
    @Autowired private TreinoMapper treinoMapper;
    @Autowired private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("índice idx_treino_planejado_prova")
    class IndiceParcial {

        @Test
        @DisplayName("existe em tb_treino_planejado")
        void indiceExiste() {
            Integer total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'tb_treino_planejado' AND indexname = 'idx_treino_planejado_prova'",
                    Integer.class);

            assertThat(total).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("vínculo TreinoPlanejado.prova")
    class VinculoProva {

        @Test
        @DisplayName("persiste e sobrevive a um reload")
        void persisteVinculo() {
            Atleta atleta = seedAtleta();
            Prova prova = seedProva(atleta, "Meia Maratona de SP");
            PlanoSemanal plano = seedPlano(atleta);

            TreinoPlanejado treino = seedTreinoPlanejado(plano, atleta, prova);
            entityManager.flush();
            entityManager.clear();

            TreinoPlanejado recarregado = treinoPlanejadoRepository.findById(treino.getId()).orElseThrow();

            assertThat(recarregado.getProva()).isNotNull();
            assertThat(recarregado.getProva().getId()).isEqualTo(prova.getId());
        }

        @Test
        @DisplayName("deletar a prova põe prova_id NULL no treino (ON DELETE SET NULL)")
        void deletarProvaDesvinculaTreino() {
            Atleta atleta = seedAtleta();
            Prova prova = seedProva(atleta, "Prova que será deletada");
            PlanoSemanal plano = seedPlano(atleta);

            TreinoPlanejado treino = seedTreinoPlanejado(plano, atleta, prova);
            UUID treinoId = treino.getId();
            UUID provaId = prova.getId();
            entityManager.flush();
            entityManager.clear();

            provaRepository.deleteById(provaId);
            entityManager.flush();
            entityManager.clear();

            TreinoPlanejado recarregado = treinoPlanejadoRepository.findById(treinoId).orElseThrow();
            assertThat(recarregado.getProva()).isNull();
        }
    }

    @Nested
    @DisplayName("TreinoMapper — provaId")
    class MapperProvaId {

        @Test
        @DisplayName("toOutputDto expõe provaId quando o treino está vinculado")
        void toOutputDtoComProva() {
            Atleta atleta = seedAtleta();
            Prova prova = seedProva(atleta, "Prova mapeada");
            PlanoSemanal plano = seedPlano(atleta);
            TreinoPlanejado treino = seedTreinoPlanejado(plano, atleta, prova);
            entityManager.flush();
            entityManager.clear();

            TreinoPlanejado recarregado = treinoPlanejadoRepository.findById(treino.getId()).orElseThrow();
            TreinoPlanejadoOutputDto dto = treinoMapper.toOutputDto(recarregado);

            assertThat(dto.provaId()).isEqualTo(prova.getId());
        }

        @Test
        @DisplayName("toOutputDto devolve provaId nulo para treino sem prova")
        void toOutputDtoSemProva() {
            Atleta atleta = seedAtleta();
            PlanoSemanal plano = seedPlano(atleta);
            TreinoPlanejado treino = seedTreinoPlanejado(plano, atleta, null);
            entityManager.flush();
            entityManager.clear();

            TreinoPlanejado recarregado = treinoPlanejadoRepository.findById(treino.getId()).orElseThrow();
            TreinoPlanejadoOutputDto dto = treinoMapper.toOutputDto(recarregado);

            assertThat(dto.provaId()).isNull();
        }

        @Test
        @DisplayName("toEntity(LlmDto) com provaId preenchido vincula a prova pelo id")
        void toEntityComProvaId() {
            Atleta atleta = seedAtleta();
            Prova prova = seedProva(atleta, "Prova do DTO da LLM");

            TreinoPlanejadoLlmDto dto = new TreinoPlanejadoLlmDto(
                    "DOMINGO", "PROVA", null, null, null, null, null,
                    "01:45:00", 21.1, "4:59", null,
                    prova.getNomeProva(), "Zona 3-4", prova.getId());

            TreinoPlanejado entidade = treinoMapper.toEntity(dto);

            assertThat(entidade.getProva()).isNotNull();
            assertThat(entidade.getProva().getId()).isEqualTo(prova.getId());
            assertThat(entidade.getDescricao()).isEqualTo(prova.getNomeProva());
            assertThat(entidade.getZonaAlvo()).isEqualTo("Zona 3-4");
        }

        @Test
        @DisplayName("toEntity(LlmDto) com provaId nulo não vincula prova (treinos comuns)")
        void toEntitySemProvaId() {
            TreinoPlanejadoLlmDto dto = new TreinoPlanejadoLlmDto(
                    "SEGUNDA", "CONTINUO", null, null, null, null, null,
                    "00:45:00", 8.0, "5:30", null,
                    null, null, null);

            TreinoPlanejado entidade = treinoMapper.toEntity(dto);

            assertThat(entidade.getProva()).isNull();
        }
    }

    // ---- helpers (espelha TreinoPlanejadoRepositoryTest / ProvaRepositoryTest) ----

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Vínculo Prova");
        assessoria.setDominio("vinculo-prova-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Vínculo Prova");
        atleta.setEmail("vinculo-prova-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr a prova");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private Prova seedProva(Atleta atleta, String nome) {
        Prova prova = Prova.builder()
                .nomeProva(nome)
                .dataProva(LocalDate.now().plusWeeks(10))
                .distancia(DistanciaProva.KM_21)
                .tipoProva(TipoProva.MEIA)
                .statusProva(ProvaStatus.PLANEJADA)
                .provaAlvo(true)
                .foiRealizada(false)
                .build();
        prova.setAtleta(atleta);
        prova.setAssessoria(atleta.getAssessoria());
        return provaRepository.save(prova);
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
        plano.setSemanaInicio(LocalDate.now().minusDays(6));
        plano.setSemanaFim(LocalDate.now().plusDays(1));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setObjetivoSemanal("Semana de teste do vínculo prova");
        return planoSemanalRepository.save(plano);
    }

    private TreinoPlanejado seedTreinoPlanejado(PlanoSemanal plano, Atleta atleta, Prova prova) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setPlanoSemanal(plano);
        treino.setAtleta(atleta);
        treino.setTenantId(atleta.getAssessoria().getId());
        treino.setDataTreino(LocalDate.now());
        treino.setDiaSemana(DiaSemana.DOMINGO);
        treino.setTipoTreino(prova != null ? TipoTreino.PROVA : TipoTreino.CONTINUO);
        treino.setDuracaoMin(Duration.ofMinutes(30));
        treino.setProva(prova);
        return treinoPlanejadoRepository.save(treino);
    }
}
