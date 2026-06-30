package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.WaitlistInputDto;
import br.com.menthoros.backend.dto.output.WaitlistOutputDto;
import br.com.menthoros.backend.services.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Sem @RequireTenant: endpoint público (pré-signup), não usa TenantContext (ver StatusController).
@RestController
@RequestMapping("/api/v1/waitlist")
@RequiredArgsConstructor
@Tag(name = "waitlist", description = "Inscrição pública na lista de interessados no beta do Menthoros")
public class WaitlistController {

    private final WaitlistService waitlistService;

    @Operation(summary = "Inscreve um interessado na waitlist do Menthoros")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Inscrição criada"),
            @ApiResponse(responseCode = "200", description = "E-mail já estava na lista (idempotente)"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos (nome, e-mail ou aceite LGPD)"),
            @ApiResponse(responseCode = "429", description = "Limite de requisições por IP excedido"),
            @ApiResponse(responseCode = "500", description = "Erro interno inesperado")
    })
    @PostMapping
    public ResponseEntity<WaitlistOutputDto> inscrever(@Valid @RequestBody WaitlistInputDto dto) {
        WaitlistService.Resultado resultado = waitlistService.registrar(dto);
        return switch (resultado) {
            // IGNORADO (honeypot) responde como CRIADO — indistinguível para o bot.
            case CRIADO, IGNORADO -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(new WaitlistOutputDto("CRIADO",
                            "Você está na lista. Em breve entraremos em contato."));
            case JA_INSCRITO -> ResponseEntity.ok(
                    new WaitlistOutputDto("JA_INSCRITO",
                            "Este e-mail já está na nossa lista."));
        };
    }
}
