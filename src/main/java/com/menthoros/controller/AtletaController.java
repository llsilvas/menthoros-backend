package com.menthoros.controller;

import com.menthoros.dto.input.AtletaInputDto;
import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.entity.Atleta;
import com.menthoros.mapper.AtletaMapper;
import com.menthoros.services.AtletaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/atleta")
@Tag(name = "Atletas", description = "Operações relacionadas ao gerenciamento de atletas")
public class AtletaController {

    private final AtletaService atletaService;
    private final AtletaMapper atletaMapper;

    public AtletaController(AtletaService atletaService, AtletaMapper atletaMapper) {
        this.atletaService = atletaService;
        this.atletaMapper = atletaMapper;
    }

    @PostMapping
    @Operation(summary = "Cadastrar novo atleta", description = "Cria um novo atleta no sistema")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Atleta criado com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = AtletaOutputDto.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos"),
        @ApiResponse(responseCode = "409", description = "Atleta já existe")
    })
    public ResponseEntity<AtletaOutputDto> cadastraAtleta(
            @Valid @RequestBody @Parameter(description = "Dados do atleta a ser criado") AtletaInputDto atletaInputDto){
        Atleta atleta = atletaService.createAtleta(atletaInputDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(atletaMapper.toOutputDto(atleta));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar atleta", description = "Atualiza os dados de um atleta existente")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Atleta atualizado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<AtletaOutputDto> atualizarAtleta(
            @Parameter(description = "ID do atleta") @PathVariable UUID id,
            @Valid @RequestBody @Parameter(description = "Dados atualizados do atleta") AtletaInputDto atletaInputDto){
        AtletaOutputDto updated = atletaService.updateAtleta(id, atletaInputDto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar atleta", description = "Remove um atleta do sistema (soft delete)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Atleta removido com sucesso"),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado")
    })
    public ResponseEntity<Void> deletarAtleta(
            @Parameter(description = "ID do atleta") @PathVariable UUID id){
        atletaService.deleteAtleta(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Listar atletas", description = "Retorna a lista de todos os atletas ativos")
    @ApiResponse(responseCode = "200", description = "Lista de atletas retornada com sucesso")
    public ResponseEntity<List<AtletaOutputDto>> listarAtletas(){
        List<AtletaOutputDto> allAtletas = atletaService.getAllAtletas();
        return ResponseEntity.ok(allAtletas);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Buscar atleta por ID", description = "Retorna os dados de um atleta específico")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Atleta encontrado"),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado")
    })
    public ResponseEntity<AtletaOutputDto> buscarAtletaPorId(
            @Parameter(description = "ID do atleta") @PathVariable UUID id){
        AtletaOutputDto atleta = atletaService.getAtletaById(id);
        return ResponseEntity.ok(atleta);
    }
}
