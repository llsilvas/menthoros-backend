package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;
import br.com.menthoros.backend.services.AssessoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/assessorias")
@RequiredArgsConstructor
@Tag(name = "Assessorias", description = "Operações administrativas de cadastro de assessorias (tenants)")
public class AssessoriaController {

    private final AssessoriaService assessoriaService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cadastrar assessoria", description = "Cria uma nova assessoria e a Organization correspondente no Keycloak")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Assessoria criada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AssessoriaOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Acesso negado - apenas ADMIN pode cadastrar assessorias",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "409", description = "Domínio já existe",
                    content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<AssessoriaOutputDto> criarAssessoria(
            @Valid @RequestBody AssessoriaInputDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assessoriaService.criarAssessoria(dto));
    }
}
