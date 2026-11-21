package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.SeasonPlanService;
import br.com.menthoros.backend.services.SeasonPlanService.Generation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/atletas/{atletaId}/season-plans")
@Tag(name = "season-plan", description = "Plano de temporada do atleta até a prova-alvo (geração e leitura pelo treinador)")
public class SeasonPlanController {

    private final SeasonPlanService seasonPlanService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Gerar a temporada da prova-alvo",
            description = "Cria a temporada PROPOSED; se já existe proposta para a mesma prova, devolve a mesma")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Temporada gerada",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "200", description = "Proposta já existente para a prova-alvo",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas TECNICO e ADMIN da assessoria"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado, ou plano de temporada desabilitado (code SEASON_PLAN_DISABLED)"),
            @ApiResponse(responseCode = "409", description = "Temporada ativa existente, ou proposta de outra prova"),
            @ApiResponse(responseCode = "422", description = "Sem prova-alvo válida, prova a menos de 2 semanas, ou base de partida faltando")
    })
    public ResponseEntity<SeasonPlanResponseDto> generate(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        Generation resultado = seasonPlanService.generate(atletaId);
        return ResponseEntity.status(resultado.created() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado.plan());
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Temporada atual do atleta", description = "A ACTIVE ou, se não houver, a PROPOSED")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Temporada encontrada",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "204", description = "Atleta sem temporada"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas TECNICO e ADMIN da assessoria"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado, ou plano de temporada desabilitado (code SEASON_PLAN_DISABLED)")
    })
    public ResponseEntity<SeasonPlanResponseDto> current(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        return seasonPlanService.findCurrent(atletaId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{seasonPlanId}/approve")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Aprovar a temporada proposta",
            description = "PROPOSED vira ACTIVE com quem e quando aprovou; aprovar uma ACTIVE devolve a mesma, sem alterar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Temporada aprovada (ou já aprovada)",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas TECNICO e ADMIN da assessoria"),
            @ApiResponse(responseCode = "404", description = "Temporada não encontrada para o atleta, ou plano de temporada desabilitado (code SEASON_PLAN_DISABLED)"),
            @ApiResponse(responseCode = "409", description = "Temporada arquivada ou concluída, ou o atleta já tem outra ativa"),
            @ApiResponse(responseCode = "422", description = "A prova deixou de ser prova-alvo ou foi cancelada")
    })
    public ResponseEntity<SeasonPlanResponseDto> approve(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da temporada") @PathVariable UUID seasonPlanId) {
        return ResponseEntity.ok(seasonPlanService.approve(atletaId, seasonPlanId));
    }
}
