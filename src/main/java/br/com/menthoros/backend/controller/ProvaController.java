package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.services.ProvaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/atletas/{atletaId}/provas")
@Tag(name = "Provas", description = "Operações relacionadas ao cadastro de provas do atleta")
@Validated
public class ProvaController {

    private final ProvaService provaService;

    public ProvaController(ProvaService provaService) {
        this.provaService = provaService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Cadastrar prova", description = "Cria uma nova prova para o atleta")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Prova criada com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProvaOutputDto.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ProvaOutputDto> criarProva(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Valid @RequestBody ProvaInputDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(provaService.criarProva(atletaId, dto));
    }

    @GetMapping
    @Operation(summary = "Listar provas", description = "Retorna todas as provas do atleta ordenadas por data")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProvaOutputDto.class))),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<List<ProvaOutputDto>> listarProvas(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        return ResponseEntity.ok(provaService.listarProvas(atletaId));
    }

    @GetMapping("/{provaId}")
    @Operation(summary = "Buscar prova por ID", description = "Retorna os dados de uma prova específica do atleta")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Prova encontrada",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProvaOutputDto.class))),
        @ApiResponse(responseCode = "404", description = "Prova ou atleta não encontrado",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ProvaOutputDto> buscarProvaPorId(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da prova") @PathVariable UUID provaId) {
        return ResponseEntity.ok(provaService.buscarProvaPorId(atletaId, provaId));
    }

    @PutMapping("/{provaId}")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Atualizar prova", description = "Atualiza os dados de uma prova do atleta")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Prova atualizada com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProvaOutputDto.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "404", description = "Prova ou atleta não encontrado",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ProvaOutputDto> atualizarProva(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da prova") @PathVariable UUID provaId,
            @Valid @RequestBody ProvaInputDto dto) {
        return ResponseEntity.ok(provaService.atualizarProva(atletaId, provaId, dto));
    }

    @DeleteMapping("/{provaId}")
    @Operation(summary = "Deletar prova", description = "Remove uma prova do atleta")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Prova removida com sucesso"),
        @ApiResponse(responseCode = "404", description = "Prova ou atleta não encontrado",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Void> deletarProva(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da prova") @PathVariable UUID provaId) {
        provaService.deletarProva(atletaId, provaId);
        return ResponseEntity.noContent().build();
    }
}
