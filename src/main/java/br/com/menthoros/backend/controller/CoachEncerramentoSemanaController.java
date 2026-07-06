package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.EncerramentoSemanaOutputDto;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.EncerramentoSemanaResultado;
import br.com.menthoros.backend.services.EncerramentoSemanaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Encerramento da semana de treino pelo treinador.
 *
 * <p>Restrito a {@code TECNICO}/{@code ADMIN}. O tenant é validado por {@code @RequireTenant}
 * (o plano precisa pertencer ao tenant corrente). A ação é on-demand (origem ON_DEMAND) e não
 * aplica carência — o treinador é a autoridade da decisão.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach")
@Tag(name = "coach-week-closure", description = "Encerramento da semana de treino pelo treinador")
public class CoachEncerramentoSemanaController {

    private final EncerramentoSemanaService encerramentoSemanaService;

    @PostMapping("/planos/{planoId}/encerrar-semana")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Encerra a semana de um plano",
            description = "Finaliza os treinos PENDENTE elegíveis como PERDIDO e fecha o plano semanal. "
                    + "Ação explícita do treinador (origem ON_DEMAND); não aplica carência. O treino do dia "
                    + "corrente só é finalizado no fim da semana (hoje == semanaFim).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo do encerramento",
                    content = @Content(schema = @Schema(implementation = EncerramentoSemanaOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Plano não encontrado no tenant")
    })
    public ResponseEntity<EncerramentoSemanaOutputDto> encerrarSemana(
            @Parameter(description = "ID do plano semanal") @PathVariable UUID planoId) {
        EncerramentoSemanaResultado resultado = encerramentoSemanaService.encerrarSemana(planoId);
        return ResponseEntity.ok(EncerramentoSemanaOutputDto.from(resultado));
    }
}
