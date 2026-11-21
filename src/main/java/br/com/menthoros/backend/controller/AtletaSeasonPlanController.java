package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AthleteSeasonPlanDto;
import br.com.menthoros.backend.services.SeasonPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/atletas")
@Tag(name = "atleta-season-plan", description = "Temporada aprovada do atleta autenticado")
public class AtletaSeasonPlanController {

    private final SeasonPlanService seasonPlanService;

    // me/* resolve o atleta pelo JWT, sem parâmetro de recurso — não usa @RequireTenant
    // (mesmo padrão de AtletaKudosController).
    @GetMapping("/me/season-plan")
    @PreAuthorize("hasRole('ATLETA')")
    @Operation(summary = "Temporada aprovada do atleta autenticado",
            description = "Só a ACTIVE e só enquanto a prova não passou; proposta, arquivada ou flag desligada respondem 204")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Temporada aprovada",
                    content = @Content(schema = @Schema(implementation = AthleteSeasonPlanDto.class))),
            @ApiResponse(responseCode = "204", description = "Sem temporada aprovada visível"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas ATLETA"),
            @ApiResponse(responseCode = "404", description = "Atleta do usuário autenticado não encontrado")
    })
    public ResponseEntity<AthleteSeasonPlanDto> mine() {
        return seasonPlanService.findForAuthenticatedAthlete()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
