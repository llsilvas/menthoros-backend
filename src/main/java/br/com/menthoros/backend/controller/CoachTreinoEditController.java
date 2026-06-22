package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.TreinoPlanejadoPatchDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.TreinoPlanejadoEditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Edição granular de treinos planejados durante revisão do plano semanal.
 *
 * <p>Restrito a {@code TECNICO}/{@code ADMIN}. Tenant resolvido internamente pelo service.
 * Apenas planos em {@code AGUARDANDO_REVISAO} são editáveis.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach/planos")
@Tag(name = "coach-workout-edit", description = "Edição de treinos planejados durante revisão do coach")
public class CoachTreinoEditController {

    private final TreinoPlanejadoEditService editService;

    @PatchMapping("/{planoId}/treinos/{treinoId}")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(
            summary = "Edita um treino planejado",
            description = "Aplica patch semântico a um TreinoPlanejado. Campos null são ignorados. "
                    + "O plano deve estar em status AGUARDANDO_REVISAO. Recalcula TSS automaticamente "
                    + "quando distanciaKm ou duracaoMin mudam, salvo se tssPlanejado for informado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Treino atualizado",
                    content = @Content(schema = @Schema(implementation = TreinoPlanejadoOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Campos inválidos"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Plano ou treino não encontrado"),
            @ApiResponse(responseCode = "409", description = "Conflito de edição concorrente"),
            @ApiResponse(responseCode = "422", description = "Plano não está em revisão")
    })
    public ResponseEntity<TreinoPlanejadoOutputDto> editarTreino(
            @Parameter(description = "UUID do plano semanal") @PathVariable UUID planoId,
            @Parameter(description = "UUID do treino planejado") @PathVariable UUID treinoId,
            @Valid @RequestBody TreinoPlanejadoPatchDto patch) {

        TreinoPlanejadoOutputDto resultado = editService.editarTreino(planoId, treinoId, patch);
        return ResponseEntity.ok(resultado);
    }
}
