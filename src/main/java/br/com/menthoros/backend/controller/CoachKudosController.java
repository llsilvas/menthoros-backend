package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.KudosInputDto;
import br.com.menthoros.backend.dto.output.KudosOutputDto;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.KudosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach/atletas")
@PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
@Tag(name = "coach-kudos", description = "Reconhecimento (kudos) do coach para o atleta")
public class CoachKudosController {

    private final KudosService kudosService;

    @PostMapping("/{atletaId}/kudos")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Registra um kudo (reconhecimento) para o atleta",
            description = "Cria um reconhecimento do coach autenticado para o atleta. Bloqueia "
                    + "duplicata do mesmo motivo, para o mesmo atleta, no mesmo dia.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Kudo criado"),
            @ApiResponse(responseCode = "400", description = "Motivo inválido"),
            @ApiResponse(responseCode = "403", description = "Atleta pertence a outro tenant"),
            @ApiResponse(responseCode = "404", description = "Atleta ou coach não encontrado no tenant"),
            @ApiResponse(responseCode = "409", description = "Kudo do mesmo motivo já registrado hoje para este atleta")
    })
    public ResponseEntity<KudosOutputDto> registrarKudo(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Valid @RequestBody KudosInputDto input) {
        KudosOutputDto criado = kudosService.registrar(atletaId, input);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }
}
