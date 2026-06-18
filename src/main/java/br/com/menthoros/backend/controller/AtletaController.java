package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.AtletaInputDto;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.mapper.AtletaMapper;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.AtletaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/atletas")
@Tag(name = "atletas", description = "Operações relacionadas ao gerenciamento de atletas")
@Validated
public class AtletaController {

    private final AtletaService atletaService;
    private final AtletaMapper atletaMapper;

    public AtletaController(AtletaService atletaService, AtletaMapper atletaMapper) {
        this.atletaService = atletaService;
        this.atletaMapper = atletaMapper;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Cadastrar novo atleta", description = "Cria um novo atleta no sistema")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Atleta criado com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = AtletaOutputDto.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "409", description = "Atleta já existe no sistema",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "403", description = "Acesso negado - apenas TECNICO e ADMIN podem criar atletas",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<AtletaOutputDto> cadastraAtleta(
            @Valid @RequestBody @Parameter(description = "Dados do atleta a ser criado") AtletaInputDto atletaInputDto){
        Atleta atleta = atletaService.createAtleta(atletaInputDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(atletaMapper.toOutputDto(atleta));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Atualizar atleta", description = "Atualiza os dados de um atleta existente")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Atleta atualizado com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = AtletaOutputDto.class))),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "403", description = "Acesso negado - apenas TECNICO e ADMIN podem atualizar atletas",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<AtletaOutputDto> atualizarAtleta(
            @Parameter(description = "ID do atleta") @PathVariable UUID id,
            @Valid @RequestBody @Parameter(description = "Dados atualizados do atleta") AtletaInputDto atletaInputDto){
        AtletaOutputDto updated = atletaService.updateAtleta(id, atletaInputDto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deletar atleta", description = "Remove um atleta do sistema (soft delete)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Atleta removido com sucesso"),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "403", description = "Acesso negado - apenas ADMIN pode deletar atletas",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Void> deletarAtleta(
            @Parameter(description = "ID do atleta") @PathVariable UUID id){
        atletaService.deleteAtleta(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Listar atletas", description = "Retorna atletas ativos com filtros opcionais por nome, nível e lesão")
    @ApiResponse(responseCode = "200", description = "Lista de atletas retornada com sucesso",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AtletaOutputDto.class))))
    public ResponseEntity<List<AtletaOutputDto>> listarAtletas(
            @Parameter(description = "Filtrar por nome (busca parcial, case-insensitive)")
            @RequestParam(required = false) String nome,
            @Parameter(description = "Filtrar por nível de experiência")
            @RequestParam(required = false) NivelExperiencia nivelExperiencia,
            @Parameter(description = "Filtrar por presença de lesão")
            @RequestParam(required = false) Boolean temLesao) {
        List<AtletaOutputDto> atletas = atletaService.getAllAtletas(nome, nivelExperiencia, temLesao);
        return ResponseEntity.ok(atletas);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar atleta por ID", description = "Retorna os dados de um atleta específico")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Atleta encontrado com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = AtletaOutputDto.class))),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<AtletaOutputDto> buscarAtletaPorId(
            @Parameter(description = "ID do atleta") @PathVariable UUID id){
        AtletaOutputDto atleta = atletaService.getAtletaById(id);
        return ResponseEntity.ok(atleta);
    }
    
    @PostMapping("/{id}/recalcular-metricas")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Recalcular métricas do atleta", description = "Recalcula as métricas de um atleta específico")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Métricas recalculadas com sucesso"),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "403", description = "Acesso negado - apenas TECNICO e ADMIN podem recalcular métricas",
                content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Void> recalcularMetricasAtleta(@Parameter(description = "ID do atleta") @PathVariable UUID id){
        atletaService.recalcularMetricasAtleta(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/convite")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Convidar atleta", description = "Gera ou reenvia o convite de acesso para o atleta")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Convite gerado/reenviado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado", content = @Content),
            @ApiResponse(responseCode = "422", description = "Atleta sem email", content = @Content)
    })
    public ResponseEntity<Void> convidarAtleta(
            @Parameter(description = "ID do atleta") @PathVariable UUID id) {
        atletaService.gerarConvite(id);
        return ResponseEntity.accepted().build();
    }
}
