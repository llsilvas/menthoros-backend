package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.TreinoPlanejadoAddDto;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.TreinoPlanejadoAddService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adição manual de treinos pelo coach durante revisão do plano semanal.
 *
 * <p>Restrito a {@code TECNICO}/{@code ADMIN}. Tenant resolvido internamente pelo service.
 * Apenas planos em {@code AGUARDANDO_REVISAO} aceitam novos treinos.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach/planos")
@Tag(name = "coach-treino-add", description = "Adição manual de treino pelo coach durante revisão de plano")
public class CoachTreinoAddController {

    private final TreinoPlanejadoAddService addService;

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

        TreinoPlanejadoOutputDto resultado = addService.adicionarTreino(planoId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
    }
}
