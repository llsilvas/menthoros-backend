package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.TrajetoShareConsentInputDto;
import br.com.menthoros.backend.dto.output.TrajetoShareConsentOutputDto;
import br.com.menthoros.backend.services.TrajetoShareConsentService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/atletas/me/consentimentos/trajeto-compartilhamento")
@Tag(name = "atleta-consentimentos", description = "Consentimentos específicos do próprio atleta")
public class AtletaConsentimentoController {

    private final TrajetoShareConsentService service;

    // Só ATLETA: é consentimento do titular do dado, nenhum outro papel decide por ele.
    // @RequireTenant não se aplica: /me/ resolve o usuário pelo token, dentro do tenant do TenantContext.

    @GetMapping
    @PreAuthorize("hasRole('ATLETA')")
    @Operation(summary = "Estado do consentimento para o trajeto GPS aparecer nos cards de compartilhamento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado atual",
                    content = @Content(schema = @Schema(implementation = TrajetoShareConsentOutputDto.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas o próprio atleta"),
            @ApiResponse(responseCode = "404", description = "Usuário do token não encontrado no tenant")
    })
    public ResponseEntity<TrajetoShareConsentOutputDto> estado() {
        return ResponseEntity.ok(service.estadoAtual());
    }

    @PostMapping
    @PreAuthorize("hasRole('ATLETA')")
    @Operation(summary = "Conceder o consentimento para a versão da Política apresentada no aviso")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Concedido (ou já estava, na versão vigente)",
                    content = @Content(schema = @Schema(implementation = TrajetoShareConsentOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Versão ausente"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas o próprio atleta"),
            @ApiResponse(responseCode = "404", description = "Usuário do token não encontrado no tenant"),
            @ApiResponse(responseCode = "409", description = "Versão apresentada não é a vigente (CONSENT_VERSION_STALE)")
    })
    public ResponseEntity<TrajetoShareConsentOutputDto> conceder(@Valid @RequestBody TrajetoShareConsentInputDto input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.conceder(input.policyVersion()));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ATLETA')")
    @Operation(summary = "Revogar o consentimento; o histórico anterior é preservado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Revogado (ou já estava)",
                    content = @Content(schema = @Schema(implementation = TrajetoShareConsentOutputDto.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — apenas o próprio atleta"),
            @ApiResponse(responseCode = "404", description = "Usuário do token não encontrado no tenant")
    })
    public ResponseEntity<TrajetoShareConsentOutputDto> revogar() {
        return ResponseEntity.ok(service.revogar());
    }
}
