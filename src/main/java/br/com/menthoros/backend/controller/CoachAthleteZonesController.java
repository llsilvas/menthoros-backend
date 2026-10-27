package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto;
import br.com.menthoros.backend.services.CoachAthleteZonesService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Zonas de pace e FC de um atleta para o treinador. Restrito a {@code TECNICO}/{@code ADMIN}. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach/atletas")
@PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
@Tag(name = "coach-athlete-zones", description = "Zonas de treino do atleta para o treinador")
public class CoachAthleteZonesController {

    private final CoachAthleteZonesService coachAthleteZonesService;

    @GetMapping("/{atletaId}/zonas")
    // Sem @RequireTenant de propósito: o aspecto responde 403 para atleta de outro tenant, e o 404 do
    // serviço (findByIdAndTenantId) não revela que o atleta existe em outra assessoria (CA4).
    @Operation(
            summary = "Zonas de treino do atleta",
            description = "Seis zonas com faixa de pace e FC, as âncoras (limiares e datas dos testes), "
                    + "o ajuste de pace por fadiga de hoje (informativo, fora das faixas) e a "
                    + "disponibilidade de cada eixo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zonas calculadas",
                    content = @Content(schema = @Schema(implementation = AthleteTrainingZonesOutputDto.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado no tenant")
    })
    public ResponseEntity<AthleteTrainingZonesOutputDto> buscarZonas(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        return ResponseEntity.ok(coachAthleteZonesService.buscarZonas(atletaId));
    }
}
