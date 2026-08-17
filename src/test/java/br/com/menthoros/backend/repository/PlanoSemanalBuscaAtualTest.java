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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Busca do plano corrente de um atleta com mais de um plano não-CONCLUIDO.
 *
 * <p>Cenário que originou a correção (relatado em 2026-08-17): o treinador <b>rejeita</b> o plano e
 * gera outro. {@code rejeitarPlano} mexe só em {@code reviewStatus} — o {@code status} continua
 * PLANEJADO —, então os dois registros casavam a query antiga e
 * {@code GET /api/v1/planos/{atletaId}} respondia 500. Ambos são da <b>mesma</b> semana, então
 * ordenar por {@code semanaInicio} não resolve: o que decide é excluir o REJEITADO, o mesmo
 * predicado do índice {@code uk_plano_semanal_atleta_semana_ativo} (V52).</p>
 */
@Transactional
class PlanoSemanalBuscaAtualTest extends AbstractIntegrationTest {

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
    @DisplayName("plano rejeitado e regerado na mesma semana: retorna o novo, sem estourar")
    void ignoraRejeitadoDaMesmaSemana() {
        Atleta atleta = seedAtleta();
        UUID tenantId = atleta.getAssessoria().getId();
        LocalDate hoje = LocalDate.now();

        salvarPlano(atleta, PlanoStatus.PLANEJADO, PlanoReviewStatus.REJEITADO, hoje);
        PlanoSemanal novo = salvarPlano(atleta, PlanoStatus.PLANEJADO,
                PlanoReviewStatus.AGUARDANDO_REVISAO, hoje);
        entityManager.flush();

        List<PlanoSemanal> resultado =
                planoSemanalRepository.findAtivosPorAtleta(atleta.getId(), tenantId);

        assertThat(resultado).extracting(PlanoSemanal::getId).containsExactly(novo.getId());
    }

    @Test
    @DisplayName("com planos ativos em semanas distintas, o mais recente vem primeiro")
    void ordenaMaisRecentePrimeiro() {
        Atleta atleta = seedAtleta();
        UUID tenantId = atleta.getAssessoria().getId();
        LocalDate hoje = LocalDate.now();

        salvarPlano(atleta, PlanoStatus.EM_ANDAMENTO, PlanoReviewStatus.APROVADO, hoje.minusWeeks(1));
        PlanoSemanal maisRecente = salvarPlano(atleta, PlanoStatus.PLANEJADO,
                PlanoReviewStatus.AGUARDANDO_REVISAO, hoje);
        entityManager.flush();

        List<PlanoSemanal> resultado =
                planoSemanalRepository.findAtivosPorAtleta(atleta.getId(), tenantId);

        assertThat(resultado).hasSize(2);
        assertThat(resultado.getFirst().getId()).isEqualTo(maisRecente.getId());
    }

    @Test
    @DisplayName("ignora plano CONCLUIDO ainda que seja o mais recente")
    void ignoraConcluido() {
        Atleta atleta = seedAtleta();
        UUID tenantId = atleta.getAssessoria().getId();
        LocalDate hoje = LocalDate.now();

        PlanoSemanal ativo = salvarPlano(atleta, PlanoStatus.EM_ANDAMENTO,
                PlanoReviewStatus.APROVADO, hoje.minusWeeks(1));
        salvarPlano(atleta, PlanoStatus.CONCLUIDO, PlanoReviewStatus.APROVADO, hoje);
        entityManager.flush();

        List<PlanoSemanal> resultado =
                planoSemanalRepository.findAtivosPorAtleta(atleta.getId(), tenantId);

        assertThat(resultado).extracting(PlanoSemanal::getId).containsExactly(ativo.getId());
    }

    @Test
    @DisplayName("não vaza plano de outro tenant")
    void naoVazaOutroTenant() {
        Atleta atleta = seedAtleta();
        LocalDate hoje = LocalDate.now();
        salvarPlano(atleta, PlanoStatus.EM_ANDAMENTO, PlanoReviewStatus.APROVADO, hoje);
        entityManager.flush();

        List<PlanoSemanal> resultado =
                planoSemanalRepository.findAtivosPorAtleta(atleta.getId(), UUID.randomUUID());

        assertThat(resultado).isEmpty();
    }

    // ---- helpers ----

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Busca Atual");
        assessoria.setDominio("busca-atual-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Busca Atual");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private PlanoSemanal salvarPlano(Atleta atleta, PlanoStatus status,
                                     PlanoReviewStatus reviewStatus, LocalDate semanaFim) {
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
        return planoSemanalRepository.save(plano);
    }
}
