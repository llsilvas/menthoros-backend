package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integração real (Testcontainers) da query {@code findAllActiveByPlataforma}: prova, contra o
 * schema/JPQL executado de verdade, que o guard {@code autoSyncPausado} (D5.2, CA10) exclui o
 * atleta pausado da listagem que {@code StravaActivitySyncScheduler.runDailyIncrementalSync}
 * itera — este é o caminho automático REAL de ingestão diária do Strava (fetch + insert de
 * {@code TreinoRealizado} novos), distinto do {@code DailyActivitySyncSchedulerImpl} (que só
 * reconcilia registros Strava já {@code PENDENTE} contra planejados, sem inserir nada novo).
 */
@Transactional
class IntegracaoExternaRepositoryFindAllActiveByPlataformaTest extends AbstractIntegrationTest {

    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private IntegracaoExternaRepository integracaoExternaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("atleta com autoSyncPausado=true NÃO aparece na listagem do daily incremental sync")
    void atletaPausadoNaoAparece() {
        IntegracaoExterna pausado = seedIntegracaoStrava(true);
        IntegracaoExterna naoPausado = seedIntegracaoStrava(false);
        entityManager.flush();
        entityManager.clear();

        List<IntegracaoExterna> resultado = integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.STRAVA);

        assertThat(resultado.stream().anyMatch(i -> i.getId().equals(pausado.getId()))).isFalse();
        assertThat(resultado.stream().anyMatch(i -> i.getId().equals(naoPausado.getId()))).isTrue();
    }

    private IntegracaoExterna seedIntegracaoStrava(boolean autoSyncPausado) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Strava Daily Sync Test");
        assessoria.setDominio("strava-daily-sync-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Strava Daily Sync");
        atleta.setEmail("strava-daily-sync-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        IntegracaoExterna integracao = new IntegracaoExterna();
        integracao.setAtleta(atleta);
        integracao.setTenantId(assessoria.getId());
        integracao.setPlataforma(FonteDados.STRAVA);
        integracao.setAtivo(true);
        integracao.setAccessToken("token-fake");
        integracao.setAutoSyncPausado(autoSyncPausado);
        return integracaoExternaRepository.save(integracao);
    }
}
