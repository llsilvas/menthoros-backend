package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.CheckinProntidaoInputDto;
import br.com.menthoros.backend.dto.output.CheckinProntidaoOutputDto;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.CheckinProntidaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/checkins")
@Tag(name = "checkin-prontidao", description = "Registro e consulta do checkin diário de prontidão subjetiva do atleta")
public class CheckinProntidaoController {

    private static final int MAX_DIAS_HISTORICO = 90;

    private final CheckinProntidaoService checkinProntidaoService;
    private final AtletaProgressService atletaProgressService;

    // @RequireTenant não se aplica: resolve o atletaId do JWT via resolverAtletaIdAtual(),
    // sem receber um resource-ID como parâmetro (mesmo padrão de AtletaTreinoController).
    @PostMapping
    @PreAuthorize("hasAnyRole('ATLETA','ADMIN')")
    @Operation(summary = "Registrar (ou atualizar) o checkin de prontidão do dia",
            description = "Cria o checkin do atleta autenticado para a data informada (ou hoje, se omitida). " +
                    "Idempotente: um segundo checkin na mesma data atualiza o existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Checkin registrado/atualizado com sucesso",
                    content = @Content(schema = @Schema(implementation = CheckinProntidaoOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas atletas podem usar este endpoint")
    })
    public ResponseEntity<CheckinProntidaoOutputDto> registrarCheckin(
            @Valid @RequestBody CheckinProntidaoInputDto input) {
        UUID atletaId = atletaProgressService.resolverAtletaIdAtual();
        CheckinProntidaoOutputDto output = checkinProntidaoService.registrarCheckin(atletaId, input);
        return ResponseEntity.status(HttpStatus.CREATED).body(output);
    }

    @GetMapping("/{atletaId}/atual")
    @PreAuthorize("hasAnyRole('ATLETA','ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Buscar o checkin mais recente do atleta")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Checkin mais recente encontrado",
                    content = @Content(schema = @Schema(implementation = CheckinProntidaoOutputDto.class))),
            @ApiResponse(responseCode = "204", description = "Nenhum checkin registrado para o atleta"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado")
    })
    public ResponseEntity<CheckinProntidaoOutputDto> buscarAtual(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        CheckinProntidaoOutputDto output = checkinProntidaoService.buscarAtual(atletaId);
        return output != null ? ResponseEntity.ok(output) : ResponseEntity.noContent().build();
    }

    @GetMapping("/{atletaId}")
    @PreAuthorize("hasAnyRole('ATLETA','ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Listar histórico de checkins do atleta")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico retornado com sucesso",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CheckinProntidaoOutputDto.class)))),
            @ApiResponse(responseCode = "400", description = "Parâmetro dias fora do intervalo permitido [1, 90]"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado")
    })
    public ResponseEntity<List<CheckinProntidaoOutputDto>> buscarHistorico(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "Número de dias a consultar (1–90, default 7)")
            @RequestParam(defaultValue = "7") @Min(1) @Max(MAX_DIAS_HISTORICO) Integer dias) {
        return ResponseEntity.ok(checkinProntidaoService.buscarHistorico(atletaId, dias));
    }
}
