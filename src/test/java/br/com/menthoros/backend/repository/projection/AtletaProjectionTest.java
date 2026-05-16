package br.com.menthoros.backend.repository.projection;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import br.com.menthoros.backend.AbstractIntegrationTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AtletaProjection and AtletaListProjection.
 *
 * Validates that the projection query methods in AtletaRepository return
 * the correct subset of fields and respect tenant isolation.
 */
class AtletaProjectionTest extends AbstractIntegrationTest {

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    private Assessoria tenant1;
    private Assessoria tenant2;
    private Atleta atleta1;
    private Atleta atleta2;
    private Atleta atletaTenant2;

    @BeforeEach
    void setup() {
        tenant1 = new Assessoria();
        tenant1.setNome("Assessoria Projection Test 1");
        tenant1.setDominio("proj-tenant1-" + UUID.randomUUID());
        tenant1.setPlano(PlanoAssessoria.BASIC);
        tenant1 = assessoriaRepository.save(tenant1);

        tenant2 = new Assessoria();
        tenant2.setNome("Assessoria Projection Test 2");
        tenant2.setDominio("proj-tenant2-" + UUID.randomUUID());
        tenant2.setPlano(PlanoAssessoria.BASIC);
        tenant2 = assessoriaRepository.save(tenant2);

        atleta1 = new Atleta();
        atleta1.setNome("Ana");
        atleta1.setEmail("ana-" + UUID.randomUUID() + "@test.com");
        atleta1.setObjetivo("Correr 10km");
        atleta1.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta1.setAtivo(AtletaStatus.ATIVO);
        atleta1.setAssessoria(tenant1);
        atleta1 = atletaRepository.save(atleta1);

        atleta2 = new Atleta();
        atleta2.setNome("Bruno");
        atleta2.setEmail("bruno-" + UUID.randomUUID() + "@test.com");
        atleta2.setObjetivo("Maratona");
        atleta2.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta2.setAtivo(AtletaStatus.ATIVO);
        atleta2.setAssessoria(tenant1);
        atleta2 = atletaRepository.save(atleta2);

        atletaTenant2 = new Atleta();
        atletaTenant2.setNome("Carlos");
        atletaTenant2.setEmail("carlos-" + UUID.randomUUID() + "@test.com");
        atletaTenant2.setObjetivo("Triathlon");
        atletaTenant2.setNivelExperiencia(NivelExperiencia.AVANCADO);
        atletaTenant2.setAtivo(AtletaStatus.ATIVO);
        atletaTenant2.setAssessoria(tenant2);
        atletaTenant2 = atletaRepository.save(atletaTenant2);
    }

    @Test
    @DisplayName("findProjectedAtletas: should return non-empty list with id, nome, email, and ativo")
    void findProjectedAtletas_shouldReturnListProjectionWithRequiredFields() {
        List<AtletaListProjection> result = atletaRepository.findProjectedAtletas();

        assertThat(result).isNotEmpty();

        // Verify the athletes saved in this test are present
        List<UUID> returnedIds = result.stream()
                .map(AtletaListProjection::getId)
                .toList();
        assertThat(returnedIds).contains(atleta1.getId(), atleta2.getId(), atletaTenant2.getId());

        // Spot-check one projection for non-null required fields
        AtletaListProjection sample = result.stream()
                .filter(p -> p.getId().equals(atleta1.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(sample.getId()).isEqualTo(atleta1.getId());
        assertThat(sample.getNome()).isEqualTo("Ana");
        assertThat(sample.getEmail()).isNotNull();
        assertThat(sample.getAtivo()).isEqualTo(AtletaStatus.ATIVO);
    }

    @Test
    @DisplayName("findProjectedByTenant: should return only athletes belonging to given tenant")
    void findProjectedByTenant_shouldReturnOnlyAtletasForTenant() {
        List<AtletaProjection> result = atletaRepository.findProjectedByTenant(tenant1.getId());

        assertThat(result).isNotEmpty();

        List<UUID> returnedIds = result.stream()
                .map(AtletaProjection::getId)
                .toList();

        // Tenant1 athletes must be present
        assertThat(returnedIds).contains(atleta1.getId(), atleta2.getId());

        // Tenant2 athlete must NOT be present
        assertThat(returnedIds).doesNotContain(atletaTenant2.getId());
    }

    @Test
    @DisplayName("findProjectedByTenant: should return id, nome, and email for each projection")
    void findProjectedByTenant_shouldReturnRequiredFields() {
        List<AtletaProjection> result = atletaRepository.findProjectedByTenant(tenant1.getId());

        AtletaProjection projection = result.stream()
                .filter(p -> p.getId().equals(atleta1.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(projection.getId()).isEqualTo(atleta1.getId());
        assertThat(projection.getNome()).isEqualTo("Ana");
        assertThat(projection.getEmail()).isNotNull();
    }

    @Test
    @DisplayName("findProjectedByTenant: should return empty list for unknown tenant")
    void findProjectedByTenant_shouldReturnEmptyForUnknownTenant() {
        List<AtletaProjection> result = atletaRepository.findProjectedByTenant(UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}
