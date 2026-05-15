package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.services.PlanoService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Planos de Treino", description = "Operações relacionadas ao gerenciamento de planos de treino")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/planos")
@Validated
public class PlanoTreinoController {

    private final PlanoService planoService;
    private final PlanoSemanalMapper planoSemanalMapper;

    @PostMapping("/atletas/{atletaId}/gerar")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Gerar plano de treino", description = "Gera um novo plano de treino semanal para o atleta especificado usando IA")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plano gerado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlanoSemanalOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Acesso negado - apenas TECNICO e ADMIN podem gerar planos",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Erro ao gerar plano",
                    content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<PlanoSemanalOutputDto> gerarPlanoTreino(
            @Parameter(description = "ID do atleta para o qual gerar o plano") @PathVariable UUID atletaId,
            @Parameter(description = "Modo de geração do plano (SEMANA_ATUAL ou PROXIMA_SEMAMA)") @RequestParam(required = false, defaultValue = "PROXIMA_SEMANA") ModoGeracaoPlano modoGeracaoPlano) {
        PlanoSemanal planoSemanal = planoService.gerarPlanoTreino(atletaId, modoGeracaoPlano);
        PlanoSemanalOutputDto planoSemanalOutputDto = planoSemanalMapper.toOutputDto(planoSemanal);

        return ResponseEntity.ok(planoSemanalOutputDto);
    }

    @DeleteMapping("/{planoSemanalId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deletar plano semanal", description = "Remove um plano semanal do sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Plano deletado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Plano não encontrado",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Acesso negado - apenas ADMIN pode deletar planos",
                    content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Void> deletePlanoSemanal(
            @Parameter(description = "ID do plano semanal a ser deletado") @PathVariable UUID planoSemanalId) {
        planoService.deletePlanoSemanal(planoSemanalId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Buscar plano semanal do atleta", description = "Busca o plano semanal atual de um atleta")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plano encontrado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlanoSemanalOutputDto.class))),
            @ApiResponse(responseCode = "404", description = "Plano não encontrado",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping("/{id}")
    public ResponseEntity<PlanoSemanalOutputDto> buscarPlanoSemanal(
            @Parameter(description = "ID do atleta") @PathVariable UUID id) {
        PlanoSemanalOutputDto planoSemanalOutputDto = planoService.buscarPlanoPorAtleta(id);

        return ResponseEntity.ok(planoSemanalOutputDto);
    }
}
