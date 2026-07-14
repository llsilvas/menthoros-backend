package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AdesaoSemanalDto;
import br.com.menthoros.backend.dto.output.AdesaoDiariaDto;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.MetricasAdesaoService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/atletas/{atletaId}/metricas")
@Tag(name = "metricas", description = "Operações relacionadas às métricas de atletas")
public class MetricasController {

    private final MetricasAdesaoService metricasAdesaoService;

    @GetMapping("/adesao-semanal")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Obter adesão semanal de atleta",
               description = "Retorna a taxa de adesão da semana atual e das 4 últimas semanas, com média")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Adesão semanal obtida com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdesaoSemanalDto.class))),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "401", description = "Não autenticado"),
        @ApiResponse(responseCode = "403", description = "Sem papel TECNICO/ADMIN ou atleta de outro tenant")
    })
    public ResponseEntity<AdesaoSemanalDto> getAdesaoSemanal(
            @Parameter(description = "ID do atleta", required = true)
            @PathVariable UUID atletaId) {
        AdesaoSemanalDto adesao = metricasAdesaoService.getAdesaoSemanal(atletaId.toString());
        return ResponseEntity.ok(adesao);
    }

    @GetMapping("/adesao-diaria")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Obter adesão diária de atleta",
               description = "Retorna a taxa de adesão por dia da semana para a semana atual e 4 últimas semanas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Adesão diária obtida com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdesaoDiariaDto.class))),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "401", description = "Não autenticado"),
        @ApiResponse(responseCode = "403", description = "Sem papel TECNICO/ADMIN ou atleta de outro tenant")
    })
    public ResponseEntity<AdesaoDiariaDto> getAdesaoDiaria(
            @Parameter(description = "ID do atleta", required = true)
            @PathVariable UUID atletaId) {
        AdesaoDiariaDto adesao = metricasAdesaoService.getAdesaoDiaria(atletaId.toString());
        return ResponseEntity.ok(adesao);
    }

}
