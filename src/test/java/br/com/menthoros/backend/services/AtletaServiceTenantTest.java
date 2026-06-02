package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.AtletaInputDto;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.mapper.AtletaMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.services.impl.AtletaServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Testa que AtletaServiceImpl exige TenantContext configurado — sem fallback para a primeira assessoria ativa.
 *
 * Use case de segurança: um atacante tenta criar/buscar/deletar atleta sem JWT.
 * Antes da correção: o sistema usava resolveTenantId() com fallback para assessoriaRepository.findFirstByAtivoTrue(),
 * criando o atleta na primeira assessoria ativa (cross-tenant vulnerability).
 * Após a correção: IllegalStateException é lançada imediatamente ao chamar TenantContext.getRequiredTenantId().
 */
@ExtendWith(MockitoExtension.class)
class AtletaServiceTenantTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private AtletaMapper atletaMapper;
    @Mock private PlanoMetadadosRepository planoMetaDadosRepository;
    @Mock private TsbService tsbService;

    private AtletaServiceImpl atletaService;

    @BeforeEach
    void setUp() {
        atletaService = new AtletaServiceImpl(
                atletaRepository,
                assessoriaRepository,
                atletaMapper,
                planoMetaDadosRepository,
                tsbService
        );
        // CRÍTICO: TenantContext vazio — simula chamada sem JWT
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private AtletaInputDto buildInput() {
        return new AtletaInputDto(
                "João Silva",
                "1990-01-01",
                new BigDecimal("75.0"),
                new BigDecimal("175.0"),
                "Completar maratona",
                NivelExperiencia.INTERMEDIARIO,
                Set.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA),
                DiaSemana.SABADO,
                false,
                null
        );
    }

    @Test
    @DisplayName("createAtleta lança IllegalStateException quando TenantContext vazio — sem fallback para primeira assessoria")
    void createAtleta_semTenant_lancaIllegalState() {
        AtletaInputDto input = buildInput();

        assertThrows(IllegalStateException.class, () -> atletaService.createAtleta(input));

        // IMPORTANTE: garantir que o fallback NÃO é mais chamado
        verify(assessoriaRepository, never()).findFirstByAtivoTrue();
    }

    @Test
    @DisplayName("updateAtleta lança IllegalStateException quando TenantContext vazio — sem fallback para primeira assessoria")
    void updateAtleta_semTenant_lancaIllegalState() {
        java.util.UUID id = java.util.UUID.randomUUID();
        AtletaInputDto input = buildInput();

        assertThrows(IllegalStateException.class, () -> atletaService.updateAtleta(id, input));

        verify(assessoriaRepository, never()).findFirstByAtivoTrue();
    }

    @Test
    @DisplayName("deleteAtleta lança IllegalStateException quando TenantContext vazio — sem fallback para primeira assessoria")
    void deleteAtleta_semTenant_lancaIllegalState() {
        java.util.UUID id = java.util.UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> atletaService.deleteAtleta(id));

        verify(assessoriaRepository, never()).findFirstByAtivoTrue();
    }

    @Test
    @DisplayName("getAtletaById lança IllegalStateException quando TenantContext vazio — sem fallback para primeira assessoria")
    void getAtletaById_semTenant_lancaIllegalState() {
        java.util.UUID id = java.util.UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> atletaService.getAtletaById(id));

        verify(assessoriaRepository, never()).findFirstByAtivoTrue();
    }

    @Test
    @DisplayName("getAllAtletas lança IllegalStateException quando TenantContext vazio — sem fallback para primeira assessoria")
    void getAllAtletas_semTenant_lancaIllegalState() {
        assertThrows(IllegalStateException.class,
                () -> atletaService.getAllAtletas(null, null, null));

        verify(assessoriaRepository, never()).findFirstByAtivoTrue();
    }
}
