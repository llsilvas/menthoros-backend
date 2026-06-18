package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.StravaStatusGlobalDto;
import br.com.menthoros.backend.services.StravaStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/strava")
@Tag(name = "strava", description = "Status global da integração Strava")
public class StravaStatusController {

    private final StravaStatusService stravaStatusService;

    @GetMapping("/status-global")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Retorna status global da integração Strava")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status retornado com sucesso"),
        @ApiResponse(responseCode = "403", description = "Acesso negado - apenas TECNICO e ADMIN podem acessar status global")
    })
    public ResponseEntity<StravaStatusGlobalDto> getStatusGlobal() {
        StravaStatusGlobalDto status = stravaStatusService.getStatusGlobal();
        return ResponseEntity.ok(status);
    }
}
