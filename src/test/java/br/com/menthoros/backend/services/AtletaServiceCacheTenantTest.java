package br.com.menthoros.backend.services;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de integração para garantir isolamento de cache por tenant.
 *
 * Use case real: coach do Tenant A busca atleta X → vai para cache.
 * Coach do Tenant B (atacante) busca atleta X com mesmo ID → sem segmentação
 * por tenant, recebe dados do Tenant A.
 *
 * Section 6 — TDD: estes testes devem falhar (RED) antes da implementação
 * da chave de cache tenant-aware no AtletaServiceImpl.
 */
class AtletaServiceCacheTenantTest extends AbstractIntegrationTest {

    @Autowired
    private AtletaService atletaService;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private CacheManager cacheManager;

    private Assessoria tenant1;
    private Assessoria tenant2;
    private UUID atletaId;

    @BeforeEach
    void setup() {
        // Limpar todos os caches antes de cada teste
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });

        // Criar dois tenants distintos
        tenant1 = Assessoria.builder()
                .nome("Assessoria Cache Tenant 1")
                .dominio("cache-t1-" + UUID.randomUUID())
                .plano(PlanoAssessoria.BASIC)
                .build();
        tenant1 = assessoriaRepository.save(tenant1);

        tenant2 = Assessoria.builder()
                .nome("Assessoria Cache Tenant 2")
                .dominio("cache-t2-" + UUID.randomUUID())
                .plano(PlanoAssessoria.BASIC)
                .build();
        tenant2 = assessoriaRepository.save(tenant2);

        // Criar atleta no tenant1
        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Cache Test");
        atleta.setObjetivo("Testar isolamento de cache");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(tenant1);
        atleta = atletaRepository.save(atleta);
        atletaId = atleta.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        // Limpar caches após cada teste
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
    }

    @Test
    @DisplayName("6.1 — Cache de atleta é isolado por tenant: Tenant B não recebe dados do Tenant A do cache")
    void cachePorTenant_isoladoCorretamente() {
        // Tenant A (tenant1) busca atleta → popula cache com chave "id_tenant1"
        TenantContext.setTenantId(tenant1.getId());
        AtletaOutputDto result1 = atletaService.getAtletaById(atletaId);
        assertNotNull(result1, "Tenant1 deve encontrar o atleta");
        assertEquals(atletaId, result1.id());

        // Tenant B (tenant2) busca o MESMO ID — deve retornar ResourceNotFoundException
        // porque o atleta pertence ao tenant1, não ao tenant2
        TenantContext.setTenantId(tenant2.getId());
        assertThrows(ResourceNotFoundException.class,
                () -> atletaService.getAtletaById(atletaId),
                "Tenant2 nao deve receber dados do Tenant1 via cache — deve lancar ResourceNotFoundException");
    }

    @Test
    @DisplayName("6.2 — Lista de atletas é isolada por tenant no cache")
    void cacheListaPorTenant_isoladoCorretamente() {
        // Criar atleta exclusivo no tenant2
        TenantContext.setTenantId(tenant2.getId());
        Atleta atletaTenant2 = new Atleta();
        atletaTenant2.setNome("Atleta Exclusivo Tenant 2");
        atletaTenant2.setObjetivo("Testar isolamento de lista");
        atletaTenant2.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atletaTenant2.setAtivo(AtletaStatus.ATIVO);
        atletaTenant2.setAssessoria(tenant2);
        final Atleta atletaTenant2Saved = atletaRepository.save(atletaTenant2);

        // Tenant1 lista atletas → cache com chave tenant1
        TenantContext.setTenantId(tenant1.getId());
        var listaTenant1 = atletaService.getAllAtletas(null, null, null);
        assertNotNull(listaTenant1);
        // Lista do tenant1 deve conter apenas atleta do tenant1
        assertTrue(listaTenant1.stream().allMatch(a -> a.id().equals(atletaId)),
                "Lista do tenant1 deve conter apenas seu atleta");

        // Tenant2 lista atletas → deve obter sua própria lista, não a do tenant1 cacheada
        TenantContext.setTenantId(tenant2.getId());
        var listaTenant2 = atletaService.getAllAtletas(null, null, null);
        assertNotNull(listaTenant2);
        // Lista do tenant2 deve conter o atleta do tenant2, não o do tenant1
        assertTrue(listaTenant2.stream().anyMatch(a -> a.id().equals(atletaTenant2Saved.getId())),
                "Lista do tenant2 deve conter seu proprio atleta");
        assertTrue(listaTenant2.stream().noneMatch(a -> a.id().equals(atletaId)),
                "Lista do tenant2 nao deve conter atleta do tenant1");
    }
}
