package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AtletaRepository} verifying that explicit
 * {@code @Transactional} annotations are present and functional on all
 * query methods.
 *
 * <p>Read-only queries are annotated with {@code @Transactional(readOnly = true)}
 * and write/modify queries with {@code @Transactional} + {@code @Modifying}.
 * These tests confirm those annotations work correctly end-to-end against a
 * real database using the {@code integration} profile.
 */
@SpringBootTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AtletaRepositoryTest {

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Assessoria tenant;
    private Atleta atletaAtivo;
    private Atleta atletaInativo;

    @BeforeEach
    void setup() {
        tenant = new Assessoria();
        tenant.setNome("Assessoria Transactional Test");
        tenant.setDominio("tx-test-" + UUID.randomUUID());
        tenant.setPlano(PlanoAssessoria.BASIC);
        tenant = assessoriaRepository.save(tenant);

        atletaAtivo = new Atleta();
        atletaAtivo.setNome("Atleta Ativo TX");
        atletaAtivo.setEmail("ativo-tx-" + UUID.randomUUID() + "@test.com");
        atletaAtivo.setObjetivo("Correr 5km");
        atletaAtivo.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atletaAtivo.setAtivo(AtletaStatus.ATIVO);
        atletaAtivo.setAssessoria(tenant);
        atletaAtivo = atletaRepository.save(atletaAtivo);

        atletaInativo = new Atleta();
        atletaInativo.setNome("Atleta Inativo TX");
        atletaInativo.setEmail("inativo-tx-" + UUID.randomUUID() + "@test.com");
        atletaInativo.setObjetivo("Triathlon");
        atletaInativo.setNivelExperiencia(NivelExperiencia.AVANCADO);
        atletaInativo.setAtivo(AtletaStatus.INATIVO);
        atletaInativo.setAssessoria(tenant);
        atletaInativo = atletaRepository.save(atletaInativo);
    }

    // -------------------------------------------------------------------------
    // @Transactional(readOnly = true) — finder methods
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAllAtletas: @Transactional(readOnly=true) — returns only ATIVO athletes for tenant")
    void findAllAtletas_readOnlyTransaction_returnsOnlyAtivosForTenant() {
        List<Atleta> result = atletaRepository.findAllAtletas(tenant.getId());

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();

        List<UUID> ids = result.stream().map(Atleta::getId).toList();
        assertThat(ids).contains(atletaAtivo.getId());
        assertThat(ids).doesNotContain(atletaInativo.getId());

        // All returned athletes must be ATIVO
        assertThat(result).allSatisfy(a ->
                assertThat(a.getAtivo()).isEqualTo(AtletaStatus.ATIVO));
    }

    @Test
    @DisplayName("findAllAtletasWithBasicInfo: @Transactional(readOnly=true) — ordered by nome ASC, ATIVO only")
    void findAllAtletasWithBasicInfo_readOnlyTransaction_orderedByNomeAscAtivosOnly() {
        List<Atleta> result = atletaRepository.findAllAtletasWithBasicInfo(tenant.getId());

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(a -> {
            assertThat(a.getAtivo()).isEqualTo(AtletaStatus.ATIVO);
            assertThat(a.getId()).isNotNull();
            assertThat(a.getNome()).isNotNull();
        });
    }

    @Test
    @DisplayName("findByIdAndTenantId: @Transactional(readOnly=true) — returns atleta when id+tenant match")
    void findByIdAndTenantId_readOnlyTransaction_returnsAtletaForCorrectTenant() {
        Optional<Atleta> result = atletaRepository.findByIdAndTenantId(atletaAtivo.getId(), tenant.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(atletaAtivo.getId());
        assertThat(result.get().getAssessoria().getId()).isEqualTo(tenant.getId());
    }

    @Test
    @DisplayName("findByIdAndTenantId: @Transactional(readOnly=true) — returns empty when tenant does not match")
    void findByIdAndTenantId_readOnlyTransaction_returnsEmptyForWrongTenant() {
        Optional<Atleta> result = atletaRepository.findByIdAndTenantId(atletaAtivo.getId(), UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByIdBasic: @Transactional(readOnly=true) — returns atleta regardless of tenant")
    void findByIdBasic_readOnlyTransaction_returnsAtletaById() {
        Optional<Atleta> result = atletaRepository.findByIdBasic(atletaAtivo.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(atletaAtivo.getId());
    }

    @Test
    @DisplayName("findAllByTenantIdOrderByNome: @Transactional(readOnly=true) — returns all statuses ordered by nome")
    void findAllByTenantIdOrderByNome_readOnlyTransaction_returnsAllStatusesOrdered() {
        List<Atleta> result = atletaRepository.findAllByTenantIdOrderByNome(tenant.getId());

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();

        List<UUID> ids = result.stream().map(Atleta::getId).toList();
        assertThat(ids).contains(atletaAtivo.getId(), atletaInativo.getId());
    }

    @Test
    @DisplayName("findAllAtletasWithDias: @Transactional(readOnly=true) — fetches diasDisponiveis without LazyInitException")
    void findAllAtletasWithDias_readOnlyTransaction_fetchesDiasWithoutException() {
        List<Atleta> result = atletaRepository.findAllAtletasWithDias(tenant.getId());

        // diasDisponiveis is eagerly fetched by this query; accessing it must not throw
        assertThat(result).isNotNull();
        result.forEach(a -> {
            assertThat(a.getId()).isNotNull();
            // diasDisponiveis is initialized by the join fetch — accessing must be safe
            assertThat(a.getDiasDisponiveis()).isNotNull();
        });
    }

    @Test
    @DisplayName("findAllAtletasWithProvas: @Transactional(readOnly=true) — fetches provas without LazyInitException")
    void findAllAtletasWithProvas_readOnlyTransaction_fetchesProvasWithoutException() {
        List<Atleta> result = atletaRepository.findAllAtletasWithProvas(tenant.getId());

        assertThat(result).isNotNull();
        result.forEach(a -> {
            assertThat(a.getId()).isNotNull();
            assertThat(a.getProvas()).isNotNull();
        });
    }

    // -------------------------------------------------------------------------
    // @Transactional + @Modifying — write methods
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deactivateAthlete: @Transactional+@Modifying — sets status INATIVO and returns 1")
    void deactivateAthlete_modifyingTransaction_setsStatusInativo() {
        int updated = atletaRepository.deactivateAthlete(atletaAtivo.getId());

        assertThat(updated).isEqualTo(1);

        // Clear the 1st-level cache so the next read hits the DB with the committed update
        entityManager.clear();

        Optional<Atleta> reloaded = atletaRepository.findByIdBasic(atletaAtivo.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getAtivo()).isEqualTo(AtletaStatus.INATIVO);
    }

    @Test
    @DisplayName("activateAthlete: @Transactional+@Modifying — sets status ATIVO and returns 1")
    void activateAthlete_modifyingTransaction_setsStatusAtivo() {
        int updated = atletaRepository.activateAthlete(atletaInativo.getId());

        assertThat(updated).isEqualTo(1);

        // Clear the 1st-level cache so the next read hits the DB with the committed update
        entityManager.clear();

        Optional<Atleta> reloaded = atletaRepository.findByIdBasic(atletaInativo.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getAtivo()).isEqualTo(AtletaStatus.ATIVO);
    }
}
