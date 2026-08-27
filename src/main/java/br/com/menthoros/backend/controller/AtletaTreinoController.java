package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.PularTreinoInputDto;
import br.com.menthoros.backend.dto.input.TreinoManualInputDto;
import br.com.menthoros.backend.dto.output.TreinoHojeDto;
import br.com.menthoros.backend.enums.MotivoPulo;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.AtletaTreinoHojeService;
import br.com.menthoros.backend.services.TreinoService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/atletas")
@Tag(name = "atleta-treinos", description = "Registro e consulta de treinos pelo próprio atleta")
public class AtletaTreinoController {

    private final TreinoService treinoService;
    private final AtletaProgressService atletaProgressService;
    private final AtletaTreinoHojeService treinoHojeService;

    // @RequireTenant não se aplica: endpoints /me/ resolvem o atletaId do JWT via resolverAtletaIdAtual(),
    // sem receber um resource-ID como parâmetro. Isolamento garantido por TenantContext + queries tenant-scoped.

    @PostMapping("/me/treinos")
    @PreAuthorize("hasAnyRole('ATLETA','ADMIN')")
    @Operation(summary = "Registrar treino realizado manualmente")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Treino registrado com sucesso",
                    content = @Content(schema = @Schema(implementation = TreinoRealizadoOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas atletas podem usar este endpoint"),
            @ApiResponse(responseCode = "422", description = "Regra de negócio violada (ex: data anterior a 7 dias)")
    })
    public ResponseEntity<TreinoRealizadoOutputDto> registrarTreinoManual(
            @Valid @RequestBody TreinoManualInputDto input) {
        UUID atletaId = atletaProgressService.resolverAtletaIdAtual();
        TreinoRealizadoOutputDto output = treinoService.registrarTreinoManualAtleta(atletaId, input);
        return ResponseEntity.status(HttpStatus.CREATED).body(output);
    }

    @GetMapping("/me/treinos/hoje")
    @PreAuthorize("hasAnyRole('ATLETA','ADMIN')")
    @Operation(summary = "Treino planejado de hoje com alvos resolvidos (modo treino)",
            description = "'Hoje' é no fuso do atleta. Cada etapa traz o alvo efetivo (FC vence pace) "
                    + "resolvido pela mesma cadeia do push ao intervals.icu.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Treino de hoje",
                    content = @Content(schema = @Schema(implementation = TreinoHojeDto.class))),
            @ApiResponse(responseCode = "204", description = "Sem treino planejado hoje"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas atletas podem usar este endpoint"),
            @ApiResponse(responseCode = "404", description = "Atleta do usuário autenticado não encontrado")
    })
    public ResponseEntity<TreinoHojeDto> getTreinoHoje() {
        UUID atletaId = atletaProgressService.resolverAtletaIdAtual();
        return treinoHojeService.getTreinoHoje(atletaId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/me/treinos/hoje/pular")
    @PreAuthorize("hasAnyRole('ATLETA','ADMIN')")
    @Operation(summary = "Pular o treino de hoje (\"Não vou conseguir hoje\")",
            description = "Marca o planejado de hoje como PERDIDO com motivo opcional e carimbo. "
                    + "Não cria treino realizado; um registro posterior que vincule o planejado reverte o pulo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Treino de hoje marcado como pulado",
                    content = @Content(schema = @Schema(implementation = TreinoHojeDto.class))),
            @ApiResponse(responseCode = "400", description = "Motivo fora da lista"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas atletas podem usar este endpoint"),
            @ApiResponse(responseCode = "404", description = "Atleta do usuário autenticado não encontrado"),
            @ApiResponse(responseCode = "422", description = "Sem treino planejado hoje, ou já realizado")
    })
    public ResponseEntity<TreinoHojeDto> pularTreinoHoje(
            @RequestBody(required = false) PularTreinoInputDto input) {
        UUID atletaId = atletaProgressService.resolverAtletaIdAtual();
        MotivoPulo motivo = input != null ? input.motivo() : null;
        return ResponseEntity.ok(treinoHojeService.pularHoje(atletaId, motivo));
    }

    @GetMapping("/me/treinos")
    @PreAuthorize("hasAnyRole('ATLETA','ADMIN')")
    @Operation(summary = "Listar treinos recentes do atleta autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de treinos realizados no período",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreinoRealizadoOutputDto.class)))),
            @ApiResponse(responseCode = "400", description = "Parâmetro dias fora do intervalo permitido [1, 30]"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas atletas podem usar este endpoint")
    })
    public ResponseEntity<List<TreinoRealizadoOutputDto>> listarTreinosRecentes(
            @Parameter(description = "Número de dias a consultar (1–30, default 7)")
            @RequestParam(defaultValue = "7") @Min(1) @Max(TreinoService.MAX_JANELA_DIAS) Integer dias) {
        UUID atletaId = atletaProgressService.resolverAtletaIdAtual();
        return ResponseEntity.ok(treinoService.listarTreinosRecentes(atletaId, dias));
    }
}
