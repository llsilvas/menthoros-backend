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
 * Integração real (Testcontainers) da query {@code findAllWithStravaConnected}: prova, contra o
 * schema/JPQL executado de verdade, que o guard {@code autoSyncPausado} (D5.2, CA10) exclui o
 * atleta pausado da listagem que o scheduler diário itera.
 */
@Transactional
class AtletaRepositoryFindAllWithStravaConnectedTest extends AbstractIntegrationTest {

    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private IntegracaoExternaRepository integracaoExternaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("atleta com autoSyncPausado=true NÃO aparece na listagem do scheduler")
    void atletaPausadoNaoAparece() {
        Atleta pausado = seedAtletaComStrava(true);
        Atleta naoPausado = seedAtletaComStrava(false);
        entityManager.flush();
        entityManager.clear();

        List<Atleta> resultado = atletaRepository.findAllWithStravaConnected();

        assertThat(resultado.stream().anyMatch(a -> a.getId().equals(pausado.getId()))).isFalse();
        assertThat(resultado.stream().anyMatch(a -> a.getId().equals(naoPausado.getId()))).isTrue();
    }

    private Atleta seedAtletaComStrava(boolean autoSyncPausado) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Strava Guard Test");
        assessoria.setDominio("strava-guard-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Strava Guard");
        atleta.setEmail("strava-guard-" + UUID.randomUUID() + "@test.com");
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
        integracaoExternaRepository.save(integracao);

        return atleta;
    }
}
