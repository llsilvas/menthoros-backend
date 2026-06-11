package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.AtletaMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import br.com.menthoros.backend.services.TsbService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtletaServiceImplConviteTest {

    @Mock
    private AtletaRepository atletaRepository;

    @Mock
    private AssessoriaRepository assessoriaRepository;

    @Mock
    private AtletaMapper atletaMapper;

    @Mock
    private PlanoMetadadosRepository planoMetadadosRepository;

    @Mock
    private TsbService tsbService;

    @Mock
    private KeycloakOrganizationGateway keycloakOrganizationGateway;

    @InjectMocks
    private AtletaServiceImpl atletaService;

    private UUID tenantId;
    private UUID atletaId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void gerarConviteEnviaConviteParaAtletaDoTenant() {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        assessoria.setKeycloakOrganizationId("org-123");

        Atleta atleta = new Atleta();
        atleta.setId(atletaId);
        atleta.setEmail("ana@teste.com");
        atleta.setAssessoria(assessoria);

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));

        atletaService.gerarConvite(atletaId);

        verify(keycloakOrganizationGateway).enviarConviteAtleta("org-123", "ana@teste.com", atletaId);
    }

    @Test
    void gerarConviteRejeitaAtletaSemEmail() {
        Atleta atleta = new Atleta();
        atleta.setId(atletaId);
        atleta.setEmail(null);

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));

        assertThatThrownBy(() -> atletaService.gerarConvite(atletaId))
                .isInstanceOf(DomainRuleViolationException.class);

        verify(keycloakOrganizationGateway, never()).enviarConviteAtleta(any(), any(), any());
    }

    @Test
    void gerarConviteRejeitaAssessoriaSemOrganizationId() {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        assessoria.setKeycloakOrganizationId(null);

        Atleta atleta = new Atleta();
        atleta.setId(atletaId);
        atleta.setEmail("ana@teste.com");
        atleta.setAssessoria(assessoria);

        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));

        assertThatThrownBy(() -> atletaService.gerarConvite(atletaId))
                .isInstanceOf(DomainRuleViolationException.class);

        verify(keycloakOrganizationGateway, never()).enviarConviteAtleta(any(), any(), any());
    }

    @Test
    void gerarConviteRejeitaAtletaDeOutroTenant() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> atletaService.gerarConvite(atletaId))
                .isInstanceOf(DomainNotFoundException.class);

        verify(keycloakOrganizationGateway, never()).enviarConviteAtleta(any(), any(), any());
    }
}
