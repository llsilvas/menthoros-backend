package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.KudosRecenteOutputDto;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.KudosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/atletas")
@Tag(name = "atleta-kudos", description = "Kudos recebidos pelo atleta autenticado")
public class AtletaKudosController {

    private final KudosService kudosService;
    private final AtletaProgressService atletaProgressService;

    // me/* são auto-resolvidos pelo JWT, sem parâmetro de recurso — não usa @RequireTenant
    // (mesmo padrão de AtletaProgressController).
    @GetMapping("/me/kudos/recentes")
    @PreAuthorize("hasRole('ATLETA')")
    @Operation(summary = "Últimos kudos recebidos pelo atleta autenticado",
            description = "Retorna até os 10 kudos mais recentes recebidos pelo atleta, mais recente primeiro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de kudos (vazia se não houver nenhum)"),
            @ApiResponse(responseCode = "404", description = "Atleta do usuário autenticado não encontrado")
    })
    public ResponseEntity<List<KudosRecenteOutputDto>> getKudosRecentes() {
        UUID atletaId = atletaProgressService.resolverAtletaIdAtual();
        return ResponseEntity.ok(kudosService.listarRecentes(atletaId));
    }
}
