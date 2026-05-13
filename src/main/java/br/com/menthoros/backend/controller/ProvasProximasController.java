package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.ProvasProximasResponseDto;
import br.com.menthoros.backend.services.ProvaService;
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
@RequestMapping("/api/v1/provas")
@RequiredArgsConstructor
@Tag(name = "Provas Próximas", description = "Operações para listagem de provas próximas de todos os atletas")
public class ProvasProximasController {

    private final ProvaService provaService;

    @GetMapping("/proximas")
    @Operation(summary = "Listar provas próximas de todos os atletas",
               description = "Retorna todas as provas de todos os atletas nos próximos 15 dias, ordenadas pela data mais próxima")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de provas próximas retornada com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProvasProximasResponseDto.class))),
        @ApiResponse(responseCode = "500", description = "Erro ao buscar provas",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ProvasProximasResponseDto> getProvasProximas() {
        ProvasProximasResponseDto response = provaService.getProvasProximas();
        return ResponseEntity.ok(response);
    }
}
