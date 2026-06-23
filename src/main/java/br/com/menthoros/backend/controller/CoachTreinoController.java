package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.TreinoPlanejadoAddDto;
import br.com.menthoros.backend.dto.input.TreinoPlanejadoPatchDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.TreinoPlanejadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adição e edição manual de treinos pelo coach durante revisão do plano semanal.
 *
 * <p>Restrito a {@code TECNICO}/{@code ADMIN}. Tenant resolvido internamente pelo service.
 * Apenas planos em {@code AGUARDANDO_REVISAO} aceitam novos treinos e edições.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach/planos")
@Tag(name = "coach-treino", description = "Adição e edição manual de treinos pelo coach durante revisão de plano")
public class CoachTreinoController {

    private final TreinoPlanejadoService treinoPlanejadoService;

    @PostMapping("/{planoId}/treinos")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    // @RequireTenant valida planoId (índice 0): TenantValidationAspect confirma que o plano pertence ao tenant.
    @RequireTenant(resourceParamIndex = 0)
    @Operation(
            summary = "Adiciona treino manual ao plano em revisão",
            description = "Cria um novo TreinoPlanejado no plano indicado. O plano deve estar em status "
                    + "AGUARDANDO_REVISAO e a dataTreino deve estar dentro do intervalo da semana. "
                    + "O treino é marcado como adicionadoPeloCoach=true e fonteDados=MANUAL. "
                    + "O TSS é calculado automaticamente quando duracaoMin e percepcaoEsforcoEsperada "
                    + "são informados, salvo se tssPlanejado for fornecido explicitamente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Treino criado com sucesso",
                    content = @Content(schema = @Schema(implementation = TreinoPlanejadoOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Campos obrigatórios ausentes ou inválidos"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Plano não encontrado no tenant"),
            @ApiResponse(responseCode = "422", description = "Plano não está em revisão, data fora do intervalo ou limite de 14 treinos atingido")
    })
    public ResponseEntity<TreinoPlanejadoOutputDto> adicionarTreino(
            @Parameter(description = "UUID do plano semanal") @PathVariable UUID planoId,
            @Valid @RequestBody TreinoPlanejadoAddDto dto) {

        TreinoPlanejadoOutputDto resultado = treinoPlanejadoService.adicionarTreino(planoId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
    }

    @PatchMapping("/{planoId}/treinos/{treinoId}")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    // @RequireTenant valida planoId (índice 0): TenantValidationAspect confirma que o plano pertence ao tenant.
    // treinoId é validado atomicamente por findByIdAndPlanoSemanalIdAndTenantId no service.
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

        TreinoPlanejadoOutputDto resultado = treinoPlanejadoService.editarTreino(planoId, treinoId, patch);
        return ResponseEntity.ok(resultado);
    }
}
