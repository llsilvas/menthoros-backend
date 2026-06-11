package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.mapper.AssessoriaMapper;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssessoriaServiceImpl - Testes unitários")
class AssessoriaServiceImplTest {

    @Mock
    private AssessoriaRepository assessoriaRepository;

    @Mock
    private AssessoriaMapper assessoriaMapper;

    @Mock
    private KeycloakOrganizationGateway keycloakOrganizationGateway;

    @InjectMocks
    private AssessoriaServiceImpl assessoriaService;

    @Test
    @DisplayName("cria assessoria + Organization e persiste o orgId")
    void criaAssessoriaEOrganizationEPersisteOrgId() {
        AssessoriaInputDto input = new AssessoriaInputDto(
                "Corridas Serra", "corridasserra", PlanoAssessoria.PRO,
                "contato@corridasserra.com", 100, 10);

        Assessoria entity = Assessoria.builder()
                .nome("Corridas Serra")
                .dominio("corridasserra")
                .plano(PlanoAssessoria.PRO)
                .build();

        when(assessoriaRepository.existsByDominio("corridasserra")).thenReturn(false);
        when(assessoriaMapper.toEntity(input)).thenReturn(entity);
        when(assessoriaRepository.save(any(Assessoria.class))).thenAnswer(invocation -> {
            Assessoria a = invocation.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });
        when(keycloakOrganizationGateway.criarOrganization(eq("Corridas Serra"), eq("corridasserra"), any(UUID.class)))
                .thenReturn("org-123");
        when(assessoriaMapper.toOutputDto(any(Assessoria.class))).thenAnswer(invocation -> {
            Assessoria a = invocation.getArgument(0);
            return new AssessoriaOutputDto(
                    a.getId(), a.getNome(), a.getDominio(), a.getPlano(),
                    a.getKeycloakOrganizationId(), a.getMaxAtletas(), a.getMaxTecnicos(),
                    true, LocalDateTime.now());
        });

        AssessoriaOutputDto result = assessoriaService.criarAssessoria(input);

        assertThat(result.keycloakOrganizationId()).isEqualTo("org-123");
        verify(keycloakOrganizationGateway).criarOrganization(eq("Corridas Serra"), eq("corridasserra"), any(UUID.class));
        verify(assessoriaRepository, times(2)).save(any(Assessoria.class));

        ArgumentCaptor<Assessoria> captor = ArgumentCaptor.forClass(Assessoria.class);
        verify(assessoriaRepository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getKeycloakOrganizationId()).isEqualTo("org-123");
    }

    @Test
    @DisplayName("rejeita domínio duplicado")
    void rejeitaDominioDuplicado() {
        AssessoriaInputDto input = new AssessoriaInputDto(
                "Corridas Serra", "corridasserra", PlanoAssessoria.PRO,
                "contato@corridasserra.com", 100, 10);

        when(assessoriaRepository.existsByDominio("corridasserra")).thenReturn(true);

        assertThatThrownBy(() -> assessoriaService.criarAssessoria(input))
                .isInstanceOf(DuplicateResourceException.class);

        verify(keycloakOrganizationGateway, never()).criarOrganization(any(), any(), any());
        verify(assessoriaRepository, never()).save(any());
    }
}
