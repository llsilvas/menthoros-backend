package br.com.menthoros.backend.controller;

import com.menthoros.api.dtos.AdesaoDiariaDto;
import br.com.menthoros.backend.services.MetricasAdesaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metricas")
@Tag(name = "Métricas da Assessoria", description = "Operações relacionadas às métricas consolidadas da assessoria")
public class AssessoriaMetricasController {

    private final MetricasAdesaoService metricasAdesaoService;

    @GetMapping("/adesao-diaria-assessoria")
    @Operation(summary = "Obter adesão diária consolidada da assessoria",
               description = "Retorna a média de adesão de todos os atletas por dia da semana para a semana atual e 4 últimas semanas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Adesão diária da assessoria obtida com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdesaoDiariaDto.class)))
    })
    public ResponseEntity<AdesaoDiariaDto> getAdesaoDiariaAssessoria() {
        AdesaoDiariaDto adesao = metricasAdesaoService.getAdesaoDiariaAssessoria();
        return ResponseEntity.ok(adesao);
    }
}
