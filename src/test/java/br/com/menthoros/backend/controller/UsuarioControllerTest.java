package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.services.UsuarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    @Mock private UsuarioService usuarioService;

    @InjectMocks private UsuarioController usuarioController;

    @Nested
    @DisplayName("getMe")
    class GetMe {

        @Test
        @DisplayName("retorna 200 com a identidade resolvida pelo service")
        void retornaIdentidade() {
            UsuarioMeOutputDto identidade = new UsuarioMeOutputDto(
                    UUID.randomUUID(), "João Silva", "joao@exemplo.com", UserRole.ATLETA, null,
                    UUID.randomUUID());
            when(usuarioService.getCurrentUser()).thenReturn(identidade);

            ResponseEntity<UsuarioMeOutputDto> response = usuarioController.getMe();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(identidade);
            verify(usuarioService).getCurrentUser();
        }
    }
}
