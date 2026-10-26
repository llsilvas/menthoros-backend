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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Queries novas de garmin-connection (D3, D4, D5) contra o Postgres real, com dois tenants. */
@Transactional
class IntegracaoExternaGarminQueriesTest extends AbstractIntegrationTest {

    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private IntegracaoExternaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("userId ativo é achado sem filtro de tenant (guard global e webhook)")
    void userIdGlobalAtravessaTenants() {
        String userId = "garmin-" + UUID.randomUUID();
        IntegracaoExterna outroTenant = seed(novaAssessoria(), FonteDados.GARMIN, true, userId, null);

        var achado = repository.findActiveByExternalAthleteIdAndPlataforma(userId, FonteDados.GARMIN);

        assertThat(achado).map(IntegracaoExterna::getId).contains(outroTenant.getId());
    }

    @Test
    @DisplayName("canal ativo: acha GARMIN e INTERVALS_ICU do atleta, ignora inativo e outro tenant")
    void canalAtivoPorAtletaETenant() {
        Assessoria a = novaAssessoria();
        IntegracaoExterna garmin = seed(a, FonteDados.GARMIN, true, "g-" + UUID.randomUUID(), null);
        UUID atletaId = garmin.getAtleta().getId();

        List<FonteDados> canais = List.of(FonteDados.INTERVALS_ICU, FonteDados.GARMIN);
        assertThat(repository.findActiveByAtletaIdAndPlataformaInAndTenantId(atletaId, canais, a.getId()))
                .extracting(IntegracaoExterna::getId).containsExactly(garmin.getId());
        assertThat(repository.findActiveByAtletaIdAndPlataformaInAndTenantId(atletaId, canais, UUID.randomUUID()))
                .isEmpty();

        garmin.setAtivo(false);
        repository.save(garmin);
        entityManager.flush();
        assertThat(repository.findActiveByAtletaIdAndPlataformaInAndTenantId(atletaId, canais, a.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("job diário: só ativas com refresh a vencer; salvar lastSyncError não move refreshTokenExpiraEm")
    void refreshExpirandoAntesDe() {
        Instant agora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant limite = agora.plus(30, ChronoUnit.DAYS);
        IntegracaoExterna dentro = seed(novaAssessoria(), FonteDados.GARMIN, true, "d-" + UUID.randomUUID(),
                agora.plus(10, ChronoUnit.DAYS));
        seed(novaAssessoria(), FonteDados.GARMIN, true, "f-" + UUID.randomUUID(), agora.plus(60, ChronoUnit.DAYS));
        seed(novaAssessoria(), FonteDados.GARMIN, false, "i-" + UUID.randomUUID(), agora.plus(10, ChronoUnit.DAYS));
        seed(novaAssessoria(), FonteDados.INTERVALS_ICU, true, "icu-" + UUID.randomUUID(),
                agora.plus(10, ChronoUnit.DAYS));

        assertThat(repository.findActiveByPlataformaAndRefreshTokenExpiraEmBefore(FonteDados.GARMIN, limite))
                .extracting(IntegracaoExterna::getId).containsExactly(dentro.getId());

        Instant antes = dentro.getRefreshTokenExpiraEm();
        dentro.setLastSyncError("erro qualquer");
        dentro.setScopes("WORKOUT_IMPORT");
        repository.save(dentro);
        entityManager.flush();
        entityManager.clear();
        assertThat(repository.findById(dentro.getId()).orElseThrow().getRefreshTokenExpiraEm()).isEqualTo(antes);
    }

    private Assessoria novaAssessoria() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Garmin Q");
        assessoria.setDominio("garmin-q-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(assessoria);
    }

    private IntegracaoExterna seed(Assessoria assessoria, FonteDados plataforma, boolean ativo, String externalId,
                                   Instant refreshExpira) {
        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Q");
        atleta.setEmail("garmin-q-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        IntegracaoExterna i = new IntegracaoExterna();
        i.setAtleta(atleta);
        i.setTenantId(assessoria.getId());
        i.setPlataforma(plataforma);
        i.setAtivo(ativo);
        i.setExternalAthleteId(externalId);
        i.setAccessToken("token-fake");
        i.setRefreshTokenExpiraEm(refreshExpira);
        return repository.save(i);
    }
}
