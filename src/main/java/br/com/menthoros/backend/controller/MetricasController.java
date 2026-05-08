package br.com.menthoros.backend.controller;

import com.menthoros.api.dtos.AdesaoSemanalDto;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/atletas/{atletaId}/metricas")
@Tag(name = "Métricas", description = "Operações relacionadas às métricas de atletas")
public class MetricasController {

    private final MetricasAdesaoService metricasAdesaoService;

    @GetMapping("/adesao-semanal")
    @Operation(summary = "Obter adesão semanal de atleta",
               description = "Retorna a taxa de adesão da semana atual e das 4 últimas semanas, com média")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Adesão semanal obtida com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdesaoSemanalDto.class))),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<AdesaoSemanalDto> getAdesaoSemanal(
            @Parameter(description = "ID do atleta", required = true)
            @PathVariable String atletaId) {
        AdesaoSemanalDto adesao = metricasAdesaoService.getAdesaoSemanal(atletaId);
        return ResponseEntity.ok(adesao);
    }
}
