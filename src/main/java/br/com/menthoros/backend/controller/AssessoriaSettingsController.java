package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AssessoriaMeOutputDto;
import br.com.menthoros.backend.services.AssessoriaSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuração da assessoria pelo próprio coach.
 *
 * <p>Endpoints {@code /me}: o tenant vem do JWT via {@code TenantContext}, nunca do cliente —
 * por isso não usam {@code @RequireTenant}, que valida um parâmetro de recurso que aqui não existe.
 */
@RestController
@RequestMapping("/api/v1/assessorias")
@RequiredArgsConstructor
@Tag(name = "assessoria-settings",
        description = "Configuração da assessoria do usuário autenticado (identidade, plano e uso)")
public class AssessoriaSettingsController {

    private final AssessoriaSettingsService assessoriaSettingsService;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('TECNICO','PROPRIETARIO','ADMIN')")
    @Operation(summary = "Consultar a assessoria do usuário autenticado",
            description = "Devolve identidade, plano, uso do plano e a versão para concorrência otimista. "
                    + "Consultar não é privilégio de dono — qualquer técnico do tenant enxerga.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configuração da assessoria",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AssessoriaMeOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Sem tenant resolvido ou role insuficiente",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Assessoria do tenant não encontrada",
                    content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<AssessoriaMeOutputDto> buscarMinhaAssessoria() {
        return ResponseEntity.ok(assessoriaSettingsService.buscarDoTenantCorrente());
    }
}
