package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.EncerramentoLoteOutputDto;
import br.com.menthoros.backend.dto.output.EncerramentoSemanaOutputDto;
import br.com.menthoros.backend.enums.OrigemEncerramento;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.EncerramentoLoteResultado;
import br.com.menthoros.backend.services.EncerramentoSemanaResultado;
import br.com.menthoros.backend.services.EncerramentoSemanaService;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Testes do {@link CoachEncerramentoSemanaController}. A autorização (403, requer TECNICO/ADMIN)
 * é declarativa via {@code @PreAuthorize} e o isolamento de tenant via {@code @RequireTenant}
 * (validados em runtime pelo Spring Security / TenantValidationAspect), como nos demais coach
 * controllers do módulo; aqui verificamos o mapeamento do resultado e a propagação do 404.
 */
@ExtendWith(MockitoExtension.class)
class CoachEncerramentoSemanaControllerTest {

    @Mock
    private EncerramentoSemanaService encerramentoSemanaService;
    @InjectMocks
    private CoachEncerramentoSemanaController controller;

    private UUID planoId;

    @BeforeEach
    void setUp() {
        planoId = UUID.randomUUID();
        TenantContext.setTenantId(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("encerrarSemana")
    class EncerrarSemana {

        @Test
        @DisplayName("POST /planos/{id}/encerrar-semana → 200 com o resumo mapeado")
        void retorna200ComResumo() {
            EncerramentoSemanaResultado resultado = new EncerramentoSemanaResultado(
                    planoId, PlanoStatus.CONCLUIDO, 2, List.of(UUID.randomUUID(), UUID.randomUUID()),
                    true, OrigemEncerramento.ON_DEMAND, null);
            when(encerramentoSemanaService.encerrarSemana(planoId)).thenReturn(resultado);

            ResponseEntity<EncerramentoSemanaOutputDto> resposta = controller.encerrarSemana(planoId);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).isNotNull();
            assertThat(resposta.getBody().planoId()).isEqualTo(planoId);
            assertThat(resposta.getBody().novoStatus()).isEqualTo(PlanoStatus.CONCLUIDO);
            assertThat(resposta.getBody().treinosFinalizados()).isEqualTo(2);
            assertThat(resposta.getBody().prontoParaProximaSemana()).isTrue();
            assertThat(resposta.getBody().origem()).isEqualTo(OrigemEncerramento.ON_DEMAND);
        }

        @Test
        @DisplayName("propaga DomainNotFoundException (→ 404) quando o plano não é do tenant")
        void propagaNotFound() {
            when(encerramentoSemanaService.encerrarSemana(planoId))
                    .thenThrow(new DomainNotFoundException("Plano semanal não encontrado"));

            assertThatThrownBy(() -> controller.encerrarSemana(planoId))
                    .isInstanceOf(DomainNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("encerrarLote / previewLote")
    class Lote {

        @Test
        @DisplayName("POST /semanas/encerrar-lote → 200 com o resumo consolidado")
        void encerrarLoteRetorna200() {
            EncerramentoLoteResultado resultado = new EncerramentoLoteResultado(
                    8, 2, 8, 23, List.of(), List.of());
            when(encerramentoSemanaService.encerrarSemanaLoteAssessoria()).thenReturn(resultado);

            ResponseEntity<EncerramentoLoteOutputDto> resposta = controller.encerrarLote();

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).isNotNull();
            assertThat(resposta.getBody().atletasProcessados()).isEqualTo(8);
            assertThat(resposta.getBody().treinosPerdidosTotal()).isEqualTo(23);
        }

        @Test
        @DisplayName("POST /semanas/encerrar-lote/preview → 200 com a projeção")
        void previewLoteRetorna200() {
            EncerramentoLoteResultado projecao = new EncerramentoLoteResultado(
                    8, 2, 8, 23, List.of(), List.of());
            when(encerramentoSemanaService.previewLoteAssessoria()).thenReturn(projecao);

            ResponseEntity<EncerramentoLoteOutputDto> resposta = controller.previewLote();

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).isNotNull();
            assertThat(resposta.getBody().atletasProcessados()).isEqualTo(8);
        }
    }
}
