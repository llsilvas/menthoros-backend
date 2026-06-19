package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.SugestaoCoachOutputDto;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.services.SugestaoCoachService;
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
class CoachSugestaoControllerTest {

    @Mock private SugestaoCoachService service;
    @InjectMocks private CoachSugestaoController controller;

    private static final UUID ID = UUID.randomUUID();

    private SugestaoCoachOutputDto dto(StatusSugestao status) {
        return new SugestaoCoachOutputDto(ID, UUID.randomUUID(), "Ana Silva",
                TipoSugestao.RECOVERY, status, "HIGH", "Revisar carga",
                null, Instant.now(), null, null);
    }

    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("GET ?status=PENDING → 200 com lista de DTOs")
        void retornaListaComStatus() {
            SugestaoCoachOutputDto dto = dto(StatusSugestao.PENDING);
            when(service.listar(StatusSugestao.PENDING)).thenReturn(List.of(dto));

            ResponseEntity<List<SugestaoCoachOutputDto>> resp = controller.listar(StatusSugestao.PENDING);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).containsExactly(dto);
        }

        @Test
        @DisplayName("GET sem status → 200 com lista vazia")
        void retornaVazioSemStatus() {
            when(service.listar(null)).thenReturn(List.of());

            ResponseEntity<List<SugestaoCoachOutputDto>> resp = controller.listar(null);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("detalhe")
    class Detalhe {

        @Test
        @DisplayName("GET /{id} → 200 com DTO")
        void retornaDto() {
            SugestaoCoachOutputDto dto = dto(StatusSugestao.PENDING);
            when(service.detalhe(ID)).thenReturn(dto);

            ResponseEntity<SugestaoCoachOutputDto> resp = controller.detalhe(ID);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isEqualTo(dto);
        }

        @Test
        @DisplayName("GET /{id} → DomainNotFoundException propagada (GlobalExceptionHandler → 404)")
        void propagaNotFound() {
            when(service.detalhe(ID)).thenThrow(new DomainNotFoundException("não encontrada"));

            assertThatThrownBy(() -> controller.detalhe(ID))
                    .isInstanceOf(DomainNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("aprovar")
    class Aprovar {

        @Test
        @DisplayName("POST /{id}/aprovar → 200 com DTO atualizado")
        void aprovaCom200() {
            SugestaoCoachOutputDto dto = dto(StatusSugestao.APPROVED);
            when(service.aprovar(ID)).thenReturn(dto);

            ResponseEntity<SugestaoCoachOutputDto> resp = controller.aprovar(ID);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isEqualTo(dto);
        }

        @Test
        @DisplayName("POST /{id}/aprovar → DomainRuleViolationException propagada (GlobalExceptionHandler → 422)")
        void propagaViolacao422() {
            when(service.aprovar(ID)).thenThrow(new DomainRuleViolationException("transição ilegal"));

            assertThatThrownBy(() -> controller.aprovar(ID))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("transição ilegal");
        }
    }

    @Nested
    @DisplayName("rejeitar")
    class Rejeitar {

        @Test
        @DisplayName("POST /{id}/rejeitar → 200 com DTO atualizado")
        void rejeitaCom200() {
            SugestaoCoachOutputDto dto = dto(StatusSugestao.REJECTED);
            when(service.rejeitar(ID)).thenReturn(dto);

            ResponseEntity<SugestaoCoachOutputDto> resp = controller.rejeitar(ID);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isEqualTo(dto);
        }

        @Test
        @DisplayName("POST /{id}/rejeitar → DomainRuleViolationException propagada (GlobalExceptionHandler → 422)")
        void propagaViolacao422() {
            when(service.rejeitar(ID)).thenThrow(new DomainRuleViolationException("transição ilegal"));

            assertThatThrownBy(() -> controller.rejeitar(ID))
                    .isInstanceOf(DomainRuleViolationException.class);
        }
    }
}
