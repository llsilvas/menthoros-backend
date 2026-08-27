package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AtletaPerfilCoachOutputDto;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.CoachAthleteProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CoachAthleteProfileController")
class CoachAthleteProfileControllerTest {

    @Mock private CoachAthleteProfileService service;
    @InjectMocks private CoachAthleteProfileController controller;

    private UUID atletaId;

    @BeforeEach
    void setUp() {
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("buscarPerfil")
    class BuscarPerfil {

        @Test
        @DisplayName("GET /{atletaId}/perfil → 200 com DTO completo")
        void retornaPerfil() {
            AtletaPerfilCoachOutputDto dto = perfil();
            when(service.buscarPerfil(atletaId)).thenReturn(dto);

            ResponseEntity<AtletaPerfilCoachOutputDto> resp = controller.buscarPerfil(atletaId);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isEqualTo(dto);
            assertThat(resp.getBody().atletaId()).isEqualTo(atletaId);
        }

        @Test
        @DisplayName("atleta não encontrado no tenant → DomainNotFoundException propagada (GlobalExceptionHandler mapeia para 404)")
        void atletaNaoEncontrado() {
            when(service.buscarPerfil(atletaId))
                    .thenThrow(new DomainNotFoundException("Atleta não encontrado"));

            assertThatThrownBy(() -> controller.buscarPerfil(atletaId))
                    .isInstanceOf(DomainNotFoundException.class)
                    .hasMessageContaining("não encontrado");
        }
    }

    // ===== helpers =====

    private AtletaPerfilCoachOutputDto perfil() {
        return new AtletaPerfilCoachOutputDto(
                atletaId, "Ana Silva", "Correr maratona", null, "INTERMEDIARIO", null,
                List.of(), List.of(), null, List.of(), List.of(), List.of(),
                Instant.now(), null, null, null, null, null, List.of());
    }
}
