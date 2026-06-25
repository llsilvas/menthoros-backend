package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Atleta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantValidationRepositoryTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private ProvaRepository provaRepository;
    @Mock private SugestaoCoachRepository sugestaoCoachRepository;

    @InjectMocks private TenantValidationRepository repository;

    @Test
    void resourceBelongsToTenant_deveValidarUuidSemTocarReconciliacao() {
        UUID resourceId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(atletaRepository.findByIdAndTenantId(resourceId, tenantId)).thenReturn(java.util.Optional.empty());
        when(treinoPlanejadoRepository.existsByIdAndAtleta_TenantId(resourceId, tenantId)).thenReturn(false);
        when(treinoRealizadoRepository.existsByIdAndAtleta_TenantId(resourceId, tenantId)).thenReturn(false);
        when(planoSemanalRepository.existsByIdAndAtleta_TenantId(resourceId, tenantId)).thenReturn(false);
        when(provaRepository.existsByIdAndAtleta_TenantId(resourceId, tenantId)).thenReturn(false);
        when(sugestaoCoachRepository.existsByIdAndTenantId(resourceId, tenantId)).thenReturn(true);

        assertThat(repository.resourceBelongsToTenant(resourceId, tenantId)).isTrue();
    }
}
