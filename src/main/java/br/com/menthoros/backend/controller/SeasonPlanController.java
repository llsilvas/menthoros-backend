package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.SeasonPlanApprovalInputDto;
import br.com.menthoros.backend.dto.input.SeasonPlanRecalculateInputDto;
import br.com.menthoros.backend.dto.input.SeasonWeekUpdateInputDto;
import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.SeasonPlanAdjustmentService;
import br.com.menthoros.backend.services.SeasonPlanService;
import br.com.menthoros.backend.services.SeasonPlanService.Generation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/atletas/{atletaId}/season-plans")
@Tag(name = "season-plan", description = "Plano de temporada do atleta até a prova-alvo (geração e leitura pelo treinador)")
public class SeasonPlanController {

    private final SeasonPlanService seasonPlanService;
    private final SeasonPlanAdjustmentService adjustmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Gerar a temporada da prova-alvo",
            description = "Cria a temporada PROPOSED; se já existe proposta para a mesma prova, devolve a mesma")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Temporada gerada",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "200", description = "Proposta já existente para a prova-alvo",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas TECNICO e ADMIN da assessoria"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado, ou plano de temporada desabilitado (code SEASON_PLAN_DISABLED)"),
            @ApiResponse(responseCode = "409", description = "Temporada ativa existente, ou proposta de outra prova"),
            @ApiResponse(responseCode = "422", description = "Sem prova-alvo válida, prova a menos de 2 semanas, base de partida faltando, ou atleta em calibração")
    })
    public ResponseEntity<SeasonPlanResponseDto> generate(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        Generation resultado = seasonPlanService.generate(atletaId);
        return ResponseEntity.status(resultado.created() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado.plan());
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Temporada atual do atleta",
            description = "A ACTIVE ou, se não houver, a PROPOSED; com ajuste aberto, traz o id dele em openDraftId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Temporada encontrada",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "204", description = "Atleta sem temporada"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas TECNICO e ADMIN da assessoria"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado, ou plano de temporada desabilitado (code SEASON_PLAN_DISABLED)")
    })
    public ResponseEntity<SeasonPlanResponseDto> current(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        return seasonPlanService.findCurrent(atletaId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{seasonPlanId}/approve")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Aprovar a temporada proposta",
            description = "PROPOSED vira ACTIVE com quem e quando aprovou; aprovar uma ACTIVE devolve a mesma, sem alterar. "
                    + "Um ajuste (versão 2 em diante) exige motivo e arquiva a ACTIVE anterior")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Temporada aprovada (ou já aprovada)",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas TECNICO e ADMIN da assessoria"),
            @ApiResponse(responseCode = "404", description = "Temporada não encontrada para o atleta, ou plano de temporada desabilitado (code SEASON_PLAN_DISABLED)"),
            @ApiResponse(responseCode = "409", description = "Temporada arquivada ou concluída, o atleta já tem outra ativa, ou a ativa mudou depois de o ajuste começar"),
            @ApiResponse(responseCode = "422", description = "A prova deixou de ser prova-alvo ou foi cancelada, o atleta está em calibração, ou falta o motivo do ajuste (máx. 255)")
    })
    public ResponseEntity<SeasonPlanResponseDto> approve(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da temporada") @PathVariable UUID seasonPlanId,
            @RequestBody(required = false) SeasonPlanApprovalInputDto body) {
        return ResponseEntity.ok(seasonPlanService.approve(atletaId, seasonPlanId, body == null ? null : body.reason()));
    }

    @GetMapping("/{seasonPlanId}")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Temporada por id", description = "Qualquer versão da temporada do atleta, inclusive o ajuste em aberto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Temporada encontrada",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas TECNICO e ADMIN da assessoria"),
            @ApiResponse(responseCode = "404", description = "Temporada não encontrada para o atleta, ou plano de temporada desabilitado (code SEASON_PLAN_DISABLED)")
    })
    public ResponseEntity<SeasonPlanResponseDto> findById(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da temporada") @PathVariable UUID seasonPlanId) {
        return ResponseEntity.ok(adjustmentService.findById(atletaId, seasonPlanId));
    }

    @PostMapping("/{seasonPlanId}/draft")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Ajustar a temporada ativa",
            description = "Cria o rascunho da próxima versão (PROPOSED) a partir da ACTIVE; se já há ajuste aberto dela, devolve o mesmo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ajuste criado",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "200", description = "Ajuste já aberto a partir desta temporada",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas TECNICO e ADMIN da assessoria"),
            @ApiResponse(responseCode = "404", description = "Temporada não encontrada para o atleta, ou plano de temporada desabilitado (code SEASON_PLAN_DISABLED)"),
            @ApiResponse(responseCode = "409", description = "A temporada não é a ativa, ou o atleta tem outra temporada proposta")
    })
    public ResponseEntity<SeasonPlanResponseDto> createDraft(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da temporada ACTIVE") @PathVariable UUID seasonPlanId) {
        Generation resultado = adjustmentService.createDraft(atletaId, seasonPlanId);
        return ResponseEntity.status(resultado.created() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado.plan());
    }

    @PatchMapping("/{seasonPlanId}/weeks/{weekIndex}")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Editar uma semana da temporada em ajuste",
            description = "Só temporada PROPOSED; semanas concluídas, em andamento e a da prova são somente leitura")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Semana editada; devolve a temporada inteira",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas TECNICO e ADMIN da assessoria"),
            @ApiResponse(responseCode = "404", description = "Temporada ou semana não encontrada, ou plano de temporada desabilitado (code SEASON_PLAN_DISABLED)"),
            @ApiResponse(responseCode = "409", description = "A temporada não está proposta nem em ajuste"),
            @ApiResponse(responseCode = "422", description = "Semana travada (concluída, em andamento ou da prova), fase não permitida, valor negativo ou sessões-chave acima de 500 caracteres")
    })
    // Sem @Valid de propósito (precedente: CoachAthleteZonesController): com problemdetails ligado, o
    // advice padrão do Spring responde ao MethodArgumentNotValidException antes do GlobalExceptionHandler,
    // sem 'message' para a tela. O serviço valida tudo e responde 422 com a mensagem.
    public ResponseEntity<SeasonPlanResponseDto> updateWeek(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da temporada") @PathVariable UUID seasonPlanId,
            @Parameter(description = "Posição da semana, a partir de 1") @PathVariable int weekIndex,
            @RequestBody SeasonWeekUpdateInputDto body) {
        return ResponseEntity.ok(adjustmentService.updateWeek(atletaId, seasonPlanId, weekIndex, body));
    }

    @PostMapping("/{seasonPlanId}/recalculate")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Recalcular o ajuste da temporada",
            description = "Regenera pelo motor as semanas livres do rascunho com as semanas por fase e o volume de "
                    + "partida pedidos; semanas travadas e as editadas pelo treinador ficam como estão")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rascunho recalculado; devolve a temporada inteira",
                    content = @Content(schema = @Schema(implementation = SeasonPlanResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas TECNICO e ADMIN da assessoria"),
            @ApiResponse(responseCode = "404", description = "Temporada não encontrada, ou plano de temporada desabilitado (code SEASON_PLAN_DISABLED)"),
            @ApiResponse(responseCode = "409", description = "A temporada não é um ajuste em aberto"),
            @ApiResponse(responseCode = "422", description = "Fases ou volume inválidos, soma das fases diferente do ciclo, prova remarcada, CTL de partida ausente ou fase divergente numa semana travada")
    })
    // Sem @Valid pelo mesmo motivo do updateWeek: o serviço valida e responde 422 com a mensagem.
    public ResponseEntity<SeasonPlanResponseDto> recalculate(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da temporada") @PathVariable UUID seasonPlanId,
            @RequestBody SeasonPlanRecalculateInputDto body) {
        return ResponseEntity.ok(adjustmentService.recalculate(atletaId, seasonPlanId, body));
    }

    @DeleteMapping("/{seasonPlanId}")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Descartar o ajuste da temporada",
            description = "O rascunho vira ARCHIVED (nenhuma linha é apagada); a temporada ativa continua")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ajuste descartado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas TECNICO e ADMIN da assessoria"),
            @ApiResponse(responseCode = "404", description = "Temporada não encontrada para o atleta, ou plano de temporada desabilitado (code SEASON_PLAN_DISABLED)"),
            @ApiResponse(responseCode = "409", description = "Não é um ajuste em aberto (já aprovado, descartado ou versão 1)")
    })
    public ResponseEntity<Void> discard(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da temporada em ajuste") @PathVariable UUID seasonPlanId) {
        adjustmentService.discard(atletaId, seasonPlanId);
        return ResponseEntity.noContent().build();
    }
}
