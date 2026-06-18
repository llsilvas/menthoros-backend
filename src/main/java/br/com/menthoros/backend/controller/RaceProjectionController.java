package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.RaceProjectionRequestDto;
import br.com.menthoros.backend.dto.output.AthleteProjectionViewDto;
import br.com.menthoros.backend.dto.output.RaceProjectionSnapshotOutputDto;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.RaceProjectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/atletas/{atletaId}/projecoes-prova")
@Tag(name = "race-projection", description = "Geração e consulta de projeções de tempo de prova por corrida")
@Validated
public class RaceProjectionController {

    private final RaceProjectionService raceProjectionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Gerar projeção de tempo de prova",
               description = "Executa a pipeline determinística de 3 camadas + LLM e persiste o snapshot")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Projeção gerada com sucesso",
                    content = @Content(schema = @Schema(implementation = RaceProjectionSnapshotOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Parâmetros inválidos"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas TECNICO e ADMIN")
    })
    public ResponseEntity<RaceProjectionSnapshotOutputDto> generate(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Valid @RequestBody RaceProjectionRequestDto request,
            Authentication authentication) {

        UUID tenantId = TenantContext.getRequiredTenantId();
        UUID coachId = UUID.fromString(authentication.getName());

        RaceProjectionSnapshotOutputDto output = raceProjectionService.generate(atletaId, tenantId, coachId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(output);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Histórico de projeções",
               description = "Retorna todos os snapshots de projeção de um atleta para uma prova, do mais recente ao mais antigo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico retornado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")
    })
    public ResponseEntity<List<RaceProjectionSnapshotOutputDto>> getHistory(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da prova") @RequestParam UUID provaId) {

        UUID tenantId = TenantContext.getRequiredTenantId();
        return ResponseEntity.ok(raceProjectionService.getHistory(atletaId, provaId, tenantId));
    }

    @GetMapping("/oficial")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Projeção oficial atual",
               description = "Retorna o snapshot marcado como oficial para a prova")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Projeção oficial encontrada"),
            @ApiResponse(responseCode = "404", description = "Nenhuma projeção oficial encontrada"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")
    })
    public ResponseEntity<RaceProjectionSnapshotOutputDto> getOfficial(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da prova") @RequestParam UUID provaId) {

        UUID tenantId = TenantContext.getRequiredTenantId();
        return raceProjectionService.getOfficial(atletaId, provaId, tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{snapshotId}/oficial")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Marcar projeção como oficial",
               description = "Define o snapshot como oficial para a prova, desmarcando o anterior se existir, e registra coach_reviewed_at")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Projeção marcada como oficial"),
            @ApiResponse(responseCode = "404", description = "Snapshot não encontrado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")
    })
    public ResponseEntity<RaceProjectionSnapshotOutputDto> markAsOfficial(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID do snapshot") @PathVariable UUID snapshotId,
            @Parameter(description = "ID da prova") @RequestParam UUID provaId,
            Authentication authentication) {

        UUID tenantId = TenantContext.getRequiredTenantId();
        UUID coachId = UUID.fromString(authentication.getName());

        return ResponseEntity.ok(
                raceProjectionService.markAsOfficial(snapshotId, atletaId, provaId, tenantId, coachId));
    }

    @GetMapping("/visao-atleta")
    @PreAuthorize("isAuthenticated()")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Visão simplificada para o atleta",
               description = "Retorna a projeção oficial revisada pelo coach, sem confiança numérica, gap ou coach_note. Retorna 404 se não houver projeção oficial revisada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visão do atleta retornada"),
            @ApiResponse(responseCode = "404", description = "Nenhuma projeção oficial revisada encontrada"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")
    })
    public ResponseEntity<AthleteProjectionViewDto> getAthleteView(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da prova") @RequestParam UUID provaId) {

        UUID tenantId = TenantContext.getRequiredTenantId();
        return raceProjectionService.getAthleteView(atletaId, provaId, tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
