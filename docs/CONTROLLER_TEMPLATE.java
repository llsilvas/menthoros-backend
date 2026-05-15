package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.mapper.ProvaMapper;
import br.com.menthoros.backend.security.RequireTenant;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * TEMPLATE: Reference controller implementation.
 *
 * Use this file as a template when generating new controllers for menthoros-backend.
 * Copy this file, adapt class names and endpoints for your resource, and follow the rules below.
 *
 * MANDATORY RULES:
 * ✅ Injected: Only Service + Mapper (never Repository directly)
 * ✅ All write endpoints have @PreAuthorize("hasAnyRole(...)")
 * ✅ All endpoints have @Operation summary
 * ✅ All endpoints have complete @ApiResponses
 * ✅ Input DTOs validated with @Valid
 * ✅ Returns ResponseEntity<OutputDto> (never Map)
 * ✅ Controller marked with @RequireTenant
 */
@RestController
@RequestMapping("/api/v1/provas")
@Tag(name = "Provas", description = "Operações relacionadas ao gerenciamento de provas (competições)")
@Validated
@RequireTenant
public class ProvaController {

    private final ProvaService provaService;
    private final ProvaMapper provaMapper;

    public ProvaController(ProvaService provaService, ProvaMapper provaMapper) {
        this.provaService = provaService;
        this.provaMapper = provaMapper;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Criar nova prova", description = "Cria uma nova prova (competição) no sistema")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Prova criada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProvaOutputDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados inválidos fornecidos",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acesso negado - apenas TECNICO e ADMIN podem criar provas",
                    content = @Content(mediaType = "application/json")
            )
    })
    public ResponseEntity<ProvaOutputDto> criar(
            @Valid @RequestBody @Parameter(description = "Dados da prova a ser criada") ProvaInputDto input) {
        var prova = provaService.criar(input);
        return ResponseEntity.status(HttpStatus.CREATED).body(provaMapper.toOutputDto(prova));
    }

    @GetMapping
    @Operation(summary = "Listar provas", description = "Retorna a lista de todas as provas ativas do tenant")
    @ApiResponse(
            responseCode = "200",
            description = "Lista de provas retornada com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProvaOutputDto.class))
    )
    public ResponseEntity<List<ProvaOutputDto>> listar() {
        List<ProvaOutputDto> provas = provaService.listarTodas();
        return ResponseEntity.ok(provas);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obter prova por ID", description = "Retorna os dados de uma prova específica")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Prova encontrada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProvaOutputDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Prova não encontrada",
                    content = @Content(mediaType = "application/json")
            )
    })
    public ResponseEntity<ProvaOutputDto> obterPorId(
            @Parameter(description = "ID da prova") @PathVariable UUID id) {
        ProvaOutputDto prova = provaService.obterPorId(id);
        return ResponseEntity.ok(prova);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Atualizar prova", description = "Atualiza os dados de uma prova existente")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Prova atualizada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProvaOutputDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Prova não encontrada",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados inválidos fornecidos",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acesso negado - apenas TECNICO e ADMIN podem atualizar provas",
                    content = @Content(mediaType = "application/json")
            )
    })
    public ResponseEntity<ProvaOutputDto> atualizar(
            @Parameter(description = "ID da prova") @PathVariable UUID id,
            @Valid @RequestBody @Parameter(description = "Dados atualizados da prova") ProvaInputDto input) {
        ProvaOutputDto atualizada = provaService.atualizar(id, input);
        return ResponseEntity.ok(atualizada);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deletar prova", description = "Remove uma prova do sistema (soft delete)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Prova removida com sucesso"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Prova não encontrada",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acesso negado - apenas ADMIN pode deletar provas",
                    content = @Content(mediaType = "application/json")
            )
    })
    public ResponseEntity<Void> deletar(
            @Parameter(description = "ID da prova") @PathVariable UUID id) {
        provaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
