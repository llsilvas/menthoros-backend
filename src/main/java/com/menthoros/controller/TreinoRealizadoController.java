package com.menthoros.controller;

import com.menthoros.dto.input.TreinoRealizadoInputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.mapper.TreinoMapper;
import com.menthoros.services.TreinoService;
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

import java.util.UUID;

@Tag(name = "Treinos Realizados", description = "Operações relacionadas ao registro de treinos realizados")
@RestController
@RequestMapping("/treinos")
public class TreinoRealizadoController {

    private final TreinoService treinoService;
    private final TreinoMapper treinoMapper;

    public TreinoRealizadoController(TreinoService treinoService, TreinoMapper treinoMapper) {
        this.treinoService = treinoService;
        this.treinoMapper = treinoMapper;
    }

    @Operation(summary = "Marcar treino como realizado",
               description = "Registra a execução de um treino planejado com os dados reais da atividade")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Treino registrado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TreinoRealizadoOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Treino planejado não encontrado",
                    content = @Content(mediaType = "application/json"))
    })
    @PostMapping("{treinoPlanejadoId}/marcar-realizado")
    public ResponseEntity<TreinoRealizadoOutputDto> criarTreino(
            @Parameter(description = "ID do treino planejado que foi realizado")
            @PathVariable("treinoPlanejadoId") UUID treinoPlanejadoId,
            @Valid @RequestBody TreinoRealizadoInputDto treinoRealizadoInputDto) {
        TreinoRealizado treinoRealizado = treinoService.addTreino(treinoPlanejadoId, treinoRealizadoInputDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(treinoMapper.toOutputDto(treinoRealizado));
    }

    @Operation(summary = "Lançar treinos manualmente para o atleta",
               description = "Lança um treino manualmente para o atleta, registrando os dados da execução")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Treino lançado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TreinoRealizadoOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado",
                    content = @Content(mediaType = "application/json"))
    })
    @PostMapping("{atletaId}/lancar-treino")
    public ResponseEntity<TreinoRealizadoOutputDto> lancarTreino(
            @Parameter(description = "ID do atleta que está realizando o treino")
            @PathVariable("atletaId") UUID atletaId,
            @Valid @RequestBody TreinoRealizadoInputDto treinoRealizadoInputDto) {
        TreinoRealizadoOutputDto treinoRealizadoOutputDto = treinoService.lancarTreino(atletaId, treinoRealizadoInputDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(treinoRealizadoOutputDto);
    }
}
