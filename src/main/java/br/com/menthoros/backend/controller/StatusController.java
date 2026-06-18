package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.StatusOutputDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;

// Sem @RequireTenant: endpoint global e público, não usa TenantContext.
@RestController
@RequestMapping("/api/v1/status")
@Tag(name = "status", description = "Status público da aplicação (nome, versão e horário do servidor)")
public class StatusController {

    private final String application;
    private final String version;
    private final Clock clock;

    public StatusController(
            @Value("${spring.application.name:menthoros}") String application,
            @Value("${app.version:1.0.0}") String version,
            Clock clock) {
        this.application = application;
        this.version = version;
        this.clock = clock;
    }

    @Operation(summary = "Retorna o status público da aplicação (nome, versão e horário do servidor)")
    @ApiResponses(@ApiResponse(
            responseCode = "200",
            description = "Status retornado com sucesso",
            content = @Content(schema = @Schema(implementation = StatusOutputDto.class))))
    @GetMapping
    public ResponseEntity<StatusOutputDto> getStatus() {
        return ResponseEntity.ok(
                new StatusOutputDto(application, version, OffsetDateTime.now(clock)));
    }
}
