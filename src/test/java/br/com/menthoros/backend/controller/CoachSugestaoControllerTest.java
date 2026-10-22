package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.RejeitarSugestaoRequestDto;
import br.com.menthoros.backend.dto.input.SugestaoCoachEditInputDto;
import br.com.menthoros.backend.dto.output.SugestaoCoachOutputDto;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.services.SugestaoCoachService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachSugestaoControllerTest {

    @Mock private SugestaoCoachService service;
    @InjectMocks private CoachSugestaoController controller;

    private static final UUID ID = UUID.randomUUID();

    private SugestaoCoachOutputDto dto(StatusSugestao status) {
        return new SugestaoCoachOutputDto(ID, UUID.randomUUID(), "Ana Silva",
                TipoSugestao.RECOVERY, status, "HIGH", "Revisar carga",
                null, Instant.now(), null, null, null, null, null, false, null, 0L);
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
            when(service.aprovar(ID, null)).thenReturn(dto);

            ResponseEntity<SugestaoCoachOutputDto> resp = controller.aprovar(ID, null);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isEqualTo(dto);
        }

        @Test
        @DisplayName("POST /{id}/aprovar → DomainRuleViolationException propagada (GlobalExceptionHandler → 422)")
        void propagaViolacao422() {
            when(service.aprovar(ID, null)).thenThrow(new DomainRuleViolationException("transição ilegal"));

            assertThatThrownBy(() -> controller.aprovar(ID, null))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("transição ilegal");
        }

        @Test
        @DisplayName("POST /{id}/aprovar → DomainConflictException propagada (GlobalExceptionHandler → 409, CA6)")
        void propagaConflito409() {
            when(service.aprovar(ID, null)).thenThrow(new DomainConflictException("decisão concorrente"));

            assertThatThrownBy(() -> controller.aprovar(ID, null))
                    .isInstanceOf(DomainConflictException.class);
        }
    }

    @Nested
    @DisplayName("rejeitar")
    class Rejeitar {

        @Test
        @DisplayName("POST /{id}/rejeitar sem corpo → 200, service recebe request null (CA3)")
        void rejeitaSemCorpo() {
            SugestaoCoachOutputDto dto = dto(StatusSugestao.REJECTED);
            when(service.rejeitar(ID, null, null)).thenReturn(dto);

            ResponseEntity<SugestaoCoachOutputDto> resp = controller.rejeitar(ID, null, null);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isEqualTo(dto);
        }

        @Test
        @DisplayName("POST /{id}/rejeitar com rejectionReason → 200, service recebe o request (CA2)")
        void rejeitaComMotivo() {
            RejeitarSugestaoRequestDto request = new RejeitarSugestaoRequestDto("volume alto demais");
            SugestaoCoachOutputDto dto = dto(StatusSugestao.REJECTED);
            ArgumentCaptor<RejeitarSugestaoRequestDto> captor = ArgumentCaptor.forClass(RejeitarSugestaoRequestDto.class);
            when(service.rejeitar(any(), captor.capture(), any())).thenReturn(dto);

            ResponseEntity<SugestaoCoachOutputDto> resp = controller.rejeitar(ID, request, null);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(captor.getValue().rejectionReason()).isEqualTo("volume alto demais");
        }

        @Test
        @DisplayName("POST /{id}/rejeitar → DomainRuleViolationException propagada (GlobalExceptionHandler → 422)")
        void propagaViolacao422() {
            when(service.rejeitar(ID, null, null)).thenThrow(new DomainRuleViolationException("transição ilegal"));

            assertThatThrownBy(() -> controller.rejeitar(ID, null, null))
                    .isInstanceOf(DomainRuleViolationException.class);
        }

        @Test
        @DisplayName("POST /{id}/rejeitar → DomainConflictException propagada (GlobalExceptionHandler → 409, CA6)")
        void propagaConflito409() {
            when(service.rejeitar(ID, null, null)).thenThrow(new DomainConflictException("decisão concorrente"));

            assertThatThrownBy(() -> controller.rejeitar(ID, null, null))
                    .isInstanceOf(DomainConflictException.class);
        }
    }

    @Nested
    @DisplayName("editar")
    class Editar {

        @Test
        @DisplayName("PUT /{id} → 200 delegando summary e versaoEsperada do corpo")
        void editaCom200() {
            SugestaoCoachOutputDto dto = dto(StatusSugestao.PENDING);
            when(service.editar(ID, "novo texto", 4L)).thenReturn(dto);

            ResponseEntity<SugestaoCoachOutputDto> resp =
                    controller.editar(ID, new SugestaoCoachEditInputDto("novo texto", 4L));

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isEqualTo(dto);
        }

        @Test
        @DisplayName("PUT /{id} → 422, 409 e 404 do service propagam (GlobalExceptionHandler mapeia)")
        void propagaErros() {
            when(service.editar(ID, "x", 0L)).thenThrow(new DomainRuleViolationException("não PENDING"));
            assertThatThrownBy(() -> controller.editar(ID, new SugestaoCoachEditInputDto("x", 0L)))
                    .isInstanceOf(DomainRuleViolationException.class);

            when(service.editar(ID, "x", 1L)).thenThrow(new DomainConflictException("versão"));
            assertThatThrownBy(() -> controller.editar(ID, new SugestaoCoachEditInputDto("x", 1L)))
                    .isInstanceOf(DomainConflictException.class);

            when(service.editar(ID, "x", 2L)).thenThrow(new DomainNotFoundException("não achou"));
            assertThatThrownBy(() -> controller.editar(ID, new SugestaoCoachEditInputDto("x", 2L)))
                    .isInstanceOf(DomainNotFoundException.class);
        }
    }
}
