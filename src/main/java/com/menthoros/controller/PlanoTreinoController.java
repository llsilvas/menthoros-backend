package com.menthoros.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.entity.PlanoSemanal;
import com.menthoros.enums.ModoGeracaoPlano;
import com.menthoros.mapper.PlanoSemanalMapper;
import com.menthoros.services.impl.PlanoServiceImpl;
import com.menthoros.services.IaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Planos de Treino", description = "Operações relacionadas ao gerenciamento de planos de treino")
@RequiredArgsConstructor
@RestController
@RequestMapping("/planos")
public class PlanoTreinoController {

    private final PlanoServiceImpl planoServiceImpl;
    private final PlanoSemanalMapper planoSemanalMapper;

    @Operation(summary = "Gerar plano de treino", description = "Gera um novo plano de treino semanal para o atleta especificado usando IA")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plano gerado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlanoSemanalOutputDto.class))),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Erro ao gerar plano",
                    content = @Content(mediaType = "application/json"))
    })
    @PostMapping("/atletas/{atletaId}/gerar")
    public ResponseEntity<PlanoSemanalOutputDto> gerarPlanoTreino(
            @Parameter(description = "ID do atleta para o qual gerar o plano") @PathVariable UUID atletaId,
            @Parameter(description = "Modo de geração do plano (SEMANA_ATUAL ou PROXIMA_SEMAMA)") @RequestParam(required = false, defaultValue = "PROXIMA_SEMANA") ModoGeracaoPlano modoGeracaoPlano) {
        PlanoSemanal planoSemanal = planoServiceImpl.gerarPlanoTreino(atletaId, modoGeracaoPlano);
        PlanoSemanalOutputDto planoSemanalOutputDto = planoSemanalMapper.toOutputDto(planoSemanal);

        return ResponseEntity.ok(planoSemanalOutputDto);
    }

    @Operation(summary = "Gerar plano de treino aprimorado", description = "Gera um plano de treino semanal aprimorado usando IA avançada")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plano aprimorado gerado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlanoSemanalOutputDto.class))),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Erro ao gerar plano",
                    content = @Content(mediaType = "application/json"))
    })
    @PostMapping("/atletas/{atletaId}/gerar-enhanced")
    public ResponseEntity<PlanoSemanalOutputDto> gerarPlanoTreinoEnhanced(
            @Parameter(description = "ID do atleta para o qual gerar o plano aprimorado") @PathVariable UUID atletaId, ModoGeracaoPlano modoGeracaoPlano) {
        PlanoSemanal planoSemanal = planoServiceImpl.gerarPlanoTreino(atletaId, modoGeracaoPlano);
        PlanoSemanalOutputDto planoSemanalOutputDto = planoSemanalMapper.toOutputDto(planoSemanal);

        return ResponseEntity.ok(planoSemanalOutputDto);
    }

    @Operation(summary = "Deletar plano semanal", description = "Remove um plano semanal do sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Plano deletado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Plano não encontrado",
                    content = @Content(mediaType = "application/json"))
    })
    @DeleteMapping("/{planoSemanalId}")
    public ResponseEntity<Void> deletePlanoSemanal(
            @Parameter(description = "ID do plano semanal a ser deletado") @PathVariable UUID planoSemanalId) {
        planoServiceImpl.deletePlanoSemanal(planoSemanalId);
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
        PlanoSemanalOutputDto planoSemanalOutputDto = planoServiceImpl.buscarPlanoPorAtleta(id);

        return ResponseEntity.ok(planoSemanalOutputDto);
    }
}
