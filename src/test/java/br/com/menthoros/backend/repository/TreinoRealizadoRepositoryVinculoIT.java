package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra o schema real, a query de ocupação usada pelo {@code CandidateSelector}
 * (fix-reconciliation-exclude-realized-candidates): devolve só planejados vinculados a OUTRO
 * realizado — o vínculo ao próprio realizado em análise não o exclui (CA3).
 */
@Transactional
class TreinoRealizadoRepositoryVinculoIT extends AbstractIntegrationTest {

    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;
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

    @Test
    @DisplayName("devolve o planejado vinculado a outro realizado e ignora o livre e o vinculado ao próprio")
    void devolveSoOVinculadoAOutro() {
        Atleta atleta = seedAtleta();
        PlanoSemanal plano = seedPlano(atleta);
        TreinoPlanejado ocupadoPorOutro = treinoPlanejadoRepository.save(novoPlanejado(atleta, plano));
        TreinoPlanejado livre = treinoPlanejadoRepository.save(novoPlanejado(atleta, plano));
        TreinoPlanejado doProprio = treinoPlanejadoRepository.save(novoPlanejado(atleta, plano));

        TreinoRealizado outro = novoRealizado(atleta);
        outro.setTreinoPlanejado(ocupadoPorOutro);
        treinoRealizadoRepository.save(outro);

        TreinoRealizado emAnalise = novoRealizado(atleta);
        emAnalise.setTreinoPlanejado(doProprio);
        emAnalise = treinoRealizadoRepository.save(emAnalise);
        entityManager.flush();
        entityManager.clear();

        Set<UUID> ocupados = treinoRealizadoRepository.findPlanejadoIdsVinculadosAOutroRealizado(
                List.of(ocupadoPorOutro.getId(), livre.getId(), doProprio.getId()), emAnalise.getId());

        assertThat(ocupados).containsExactly(ocupadoPorOutro.getId());
    }

    @Test
    @DisplayName("nenhum planejado vinculado devolve conjunto vazio")
    void nenhumVinculadoDevolveVazio() {
        Atleta atleta = seedAtleta();
        PlanoSemanal plano = seedPlano(atleta);
        TreinoPlanejado livre = treinoPlanejadoRepository.save(novoPlanejado(atleta, plano));
        TreinoRealizado emAnalise = treinoRealizadoRepository.save(novoRealizado(atleta));
        entityManager.flush();
        entityManager.clear();

        Set<UUID> ocupados = treinoRealizadoRepository.findPlanejadoIdsVinculadosAOutroRealizado(
                List.of(livre.getId()), emAnalise.getId());

        assertThat(ocupados).isEmpty();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Vinculo Test");
        assessoria.setDominio("vinculo-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Vinculo");
        atleta.setEmail("vinculo-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 21km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
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
        plano.setSemanaFim(LocalDate.now());
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(30));
        plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        plano.setReviewStatus(PlanoReviewStatus.APROVADO);
        plano.setObjetivoSemanal("Semana de teste");
        return planoSemanalRepository.save(plano);
    }

    private TreinoPlanejado novoPlanejado(Atleta atleta, PlanoSemanal plano) {
        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setPlanoSemanal(plano);
        planejado.setAtleta(atleta);
        planejado.setTenantId(atleta.getAssessoria().getId());
        planejado.setDataTreino(LocalDate.now().minusDays(1));
        planejado.setDiaSemana(DiaSemana.SEGUNDA);
        planejado.setTipoTreino(TipoTreino.CONTINUO);
        planejado.setDuracaoMin(Duration.ofMinutes(40));
        planejado.setStatusSincronizacao(StatusSincronizacao.NAO_SINCRONIZADO);
        planejado.setTentativasSincronizacao(0);
        return planejado;
    }

    private TreinoRealizado novoRealizado(Atleta atleta) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setAtleta(atleta);
        tr.setTenantId(atleta.getAssessoria().getId());
        tr.setDataTreino(LocalDate.now().minusDays(1));
        tr.setDiaSemana(DiaSemana.SEGUNDA);
        tr.setTipoTreino(TipoTreino.CONTINUO);
        tr.setDuracaoMin(Duration.ofMinutes(40));
        tr.setDistanciaKm(BigDecimal.valueOf(5));
        tr.setFonteDados(FonteDados.STRAVA);
        tr.setStatus(TreinoExecucaoStatus.REALIZADO);
        return tr;
    }
}
