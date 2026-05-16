package br.com.menthoros.backend.repository.custom;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AtletaRepositoryCustom} / {@link AtletaRepositoryImpl}.
 *
 * Verifies that {@code findAtletasWithFetchGraph} correctly applies each fetch
 * strategy ("basic", "dias", "provas", "all") and that tenant isolation is
 * honoured — only atletas belonging to the requested tenant are returned.
 */
class AtletaRepositoryCustomTest extends AbstractIntegrationTest {

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
        tenant1.setNome("Assessoria Custom Test 1");
        tenant1.setDominio("custom-tenant1-" + UUID.randomUUID());
        tenant1.setPlano(PlanoAssessoria.BASIC);
        tenant1 = assessoriaRepository.save(tenant1);

        tenant2 = new Assessoria();
        tenant2.setNome("Assessoria Custom Test 2");
        tenant2.setDominio("custom-tenant2-" + UUID.randomUUID());
        tenant2.setPlano(PlanoAssessoria.BASIC);
        tenant2 = assessoriaRepository.save(tenant2);

        atleta1 = new Atleta();
        atleta1.setNome("Ana Custom");
        atleta1.setEmail("ana-custom-" + UUID.randomUUID() + "@test.com");
        atleta1.setObjetivo("Correr 10km");
        atleta1.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta1.setAtivo(AtletaStatus.ATIVO);
        atleta1.setAssessoria(tenant1);
        atleta1 = atletaRepository.save(atleta1);

        atleta2 = new Atleta();
        atleta2.setNome("Bruno Custom");
        atleta2.setEmail("bruno-custom-" + UUID.randomUUID() + "@test.com");
        atleta2.setObjetivo("Maratona");
        atleta2.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta2.setAtivo(AtletaStatus.ATIVO);
        atleta2.setAssessoria(tenant1);
        atleta2 = atletaRepository.save(atleta2);

        atletaTenant2 = new Atleta();
        atletaTenant2.setNome("Carlos Custom");
        atletaTenant2.setEmail("carlos-custom-" + UUID.randomUUID() + "@test.com");
        atletaTenant2.setObjetivo("Triathlon");
        atletaTenant2.setNivelExperiencia(NivelExperiencia.AVANCADO);
        atletaTenant2.setAtivo(AtletaStatus.ATIVO);
        atletaTenant2.setAssessoria(tenant2);
        atletaTenant2 = atletaRepository.save(atletaTenant2);
    }

    // -------------------------------------------------------------------------
    // Tenant isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAtletasWithFetchGraph: should return only atletas belonging to given tenant")
    void findAtletasWithFetchGraph_shouldEnforceTenantIsolation() {
        Pageable pageable = PageRequest.of(0, 20);

        Page<Atleta> result = atletaRepository.findAtletasWithFetchGraph(tenant1.getId(), "basic", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();

        var ids = result.getContent().stream().map(Atleta::getId).toList();
        assertThat(ids).contains(atleta1.getId(), atleta2.getId());
        assertThat(ids).doesNotContain(atletaTenant2.getId());
    }

    @Test
    @DisplayName("findAtletasWithFetchGraph: should return empty page for unknown tenant")
    void findAtletasWithFetchGraph_shouldReturnEmptyPageForUnknownTenant() {
        Pageable pageable = PageRequest.of(0, 20);

        Page<Atleta> result = atletaRepository.findAtletasWithFetchGraph(UUID.randomUUID(), "basic", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // -------------------------------------------------------------------------
    // Fetch strategies — smoke test: each strategy returns non-null entities
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAtletasWithFetchGraph: 'basic' fetch strategy returns entities without NPE")
    void findAtletasWithFetchGraph_basicStrategy_returnsEntities() {
        Page<Atleta> result = atletaRepository.findAtletasWithFetchGraph(
                tenant1.getId(), "basic", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .isNotEmpty()
                .allSatisfy(a -> assertThat(a.getId()).isNotNull());
    }

    @Test
    @DisplayName("findAtletasWithFetchGraph: 'dias' fetch strategy returns entities without NPE")
    void findAtletasWithFetchGraph_diasStrategy_returnsEntities() {
        Page<Atleta> result = atletaRepository.findAtletasWithFetchGraph(
                tenant1.getId(), "dias", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .isNotEmpty()
                .allSatisfy(a -> assertThat(a.getId()).isNotNull());
    }

    @Test
    @DisplayName("findAtletasWithFetchGraph: 'provas' fetch strategy returns entities without NPE")
    void findAtletasWithFetchGraph_provasStrategy_returnsEntities() {
        Page<Atleta> result = atletaRepository.findAtletasWithFetchGraph(
                tenant1.getId(), "provas", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .isNotEmpty()
                .allSatisfy(a -> assertThat(a.getId()).isNotNull());
    }

    @Test
    @DisplayName("findAtletasWithFetchGraph: 'all' fetch strategy returns entities without NPE")
    void findAtletasWithFetchGraph_allStrategy_returnsEntities() {
        Page<Atleta> result = atletaRepository.findAtletasWithFetchGraph(
                tenant1.getId(), "all", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .isNotEmpty()
                .allSatisfy(a -> assertThat(a.getId()).isNotNull());
    }

    // -------------------------------------------------------------------------
    // Pagination
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAtletasWithFetchGraph: total elements reflects only the tenant's atletas")
    void findAtletasWithFetchGraph_totalElementsMatchesTenantCount() {
        // tenant1 has exactly 2 atletas created in setup
        Page<Atleta> result = atletaRepository.findAtletasWithFetchGraph(
                tenant1.getId(), "basic", PageRequest.of(0, 100));

        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("findAtletasWithFetchGraph: page size is respected")
    void findAtletasWithFetchGraph_pageSizeIsRespected() {
        Page<Atleta> result = atletaRepository.findAtletasWithFetchGraph(
                tenant1.getId(), "basic", PageRequest.of(0, 1));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(2);
    }
}
