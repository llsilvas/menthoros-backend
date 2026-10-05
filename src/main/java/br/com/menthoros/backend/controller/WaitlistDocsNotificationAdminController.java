package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.WaitlistDocsNotificationResultDto;
import br.com.menthoros.backend.services.WaitlistDocsNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aviso em massa, por e-mail, aos treinadores da waitlist de que a central de ajuda está no ar.
 *
 * <p>Rota {@code /api/admin/**}: tenant-less por contrato (isenta no {@code JwtTenantFilter}) e
 * restrita a {@code ADMIN} — o role de staff da plataforma, não o dono de uma assessoria. O
 * founder chama por Apidog; não há tela. Sem corpo — processa todos os elegíveis de uma vez,
 * diferente do convite de fundador (que é por inscrito).</p>
 */
@RestController
@RequestMapping("/api/admin/waitlist/notificar-docs")
@RequiredArgsConstructor
@Tag(name = "waitlist-docs-notification-admin", description = "Aviso em massa aos treinadores da waitlist sobre a central de ajuda (staff)")
public class WaitlistDocsNotificationAdminController {

    private final WaitlistDocsNotificationService service;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Avisa por e-mail todo treinador da waitlist ainda não avisado sobre a central de ajuda",
            description = "Idempotente: só envia a quem não tem docsNotifiedAt. Falha de um inscrito não interrompe "
                    + "os demais; o inscrito que falhar segue elegível na próxima chamada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Processado — ver contagem no corpo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WaitlistDocsNotificationResultDto.class))),
            @ApiResponse(responseCode = "401", description = "Sem autenticação"),
            @ApiResponse(responseCode = "403", description = "Acesso negado - apenas ADMIN")
    })
    public ResponseEntity<WaitlistDocsNotificationResultDto> notificar() {
        return ResponseEntity.ok(service.notificar());
    }
}
