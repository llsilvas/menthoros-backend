package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.mapper.ProvaMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.services.impl.ProvaServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Testa que ProvaServiceImpl exige TenantContext configurado — sem fallback para a primeira assessoria ativa.
 *
 * Use case de segurança: um atacante tenta criar/buscar/deletar provas sem JWT.
 * Antes da correção: o sistema usava resolveTenantId() com fallback para assessoriaRepository.findFirstByAtivoTrue(),
 * permitindo acesso à primeira assessoria ativa (cross-tenant vulnerability).
 * Após a correção: IllegalStateException é lançada imediatamente ao chamar TenantContext.getRequiredTenantId().
 */
@ExtendWith(MockitoExtension.class)
class ProvaServiceTenantTest {

    @Mock private ProvaRepository provaRepository;
    @Mock private AtletaRepository atletaRepository;
    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private ProvaMapper provaMapper;

    private ProvaServiceImpl provaService;

    @BeforeEach
    void setUp() {
        provaService = new ProvaServiceImpl(
                provaRepository,
                atletaRepository,
                assessoriaRepository,
                provaMapper
        );
        // CRÍTICO: TenantContext vazio — simula chamada sem JWT
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ProvaInputDto buildInput() {
        return new ProvaInputDto(
                "Maratona Internacional de São Paulo",
                LocalDate.now().plusDays(90),
                TipoProva.CORRIDA_RUA,
                DistanciaProva.KM_42,
                null, false, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    @DisplayName("criarProva lança IllegalStateException quando TenantContext vazio — sem fallback para primeira assessoria")
    void criarProva_semTenant_lancaIllegalState() {
        UUID atletaId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> provaService.criarProva(atletaId, buildInput()));

        // IMPORTANTE: garantir que o fallback NÃO é mais chamado
        verify(assessoriaRepository, never()).findFirstByAtivoTrue();
    }

    @Test
    @DisplayName("listarProvas lança IllegalStateException quando TenantContext vazio — sem fallback para primeira assessoria")
    void listarProvas_semTenant_lancaIllegalState() {
        UUID atletaId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> provaService.listarProvas(atletaId));

        verify(assessoriaRepository, never()).findFirstByAtivoTrue();
    }

    @Test
    @DisplayName("buscarProvaPorId lança IllegalStateException quando TenantContext vazio — sem fallback para primeira assessoria")
    void buscarProvaPorId_semTenant_lancaIllegalState() {
        UUID atletaId = UUID.randomUUID();
        UUID provaId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> provaService.buscarProvaPorId(atletaId, provaId));

        verify(assessoriaRepository, never()).findFirstByAtivoTrue();
    }

    @Test
    @DisplayName("atualizarProva lança IllegalStateException quando TenantContext vazio — sem fallback para primeira assessoria")
    void atualizarProva_semTenant_lancaIllegalState() {
        UUID atletaId = UUID.randomUUID();
        UUID provaId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> provaService.atualizarProva(atletaId, provaId, buildInput()));

        verify(assessoriaRepository, never()).findFirstByAtivoTrue();
    }

    @Test
    @DisplayName("deletarProva lança IllegalStateException quando TenantContext vazio — sem fallback para primeira assessoria")
    void deletarProva_semTenant_lancaIllegalState() {
        UUID atletaId = UUID.randomUUID();
        UUID provaId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> provaService.deletarProva(atletaId, provaId));

        verify(assessoriaRepository, never()).findFirstByAtivoTrue();
    }
}
