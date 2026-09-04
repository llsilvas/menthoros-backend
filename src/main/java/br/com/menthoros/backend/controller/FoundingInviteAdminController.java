package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.FoundingInviteOutputDto;
import br.com.menthoros.backend.services.FoundingInviteService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Emissão do convite de assessoria fundadora a partir de um inscrito da waitlist.
 *
 * <p>Rota {@code /api/admin/**}: tenant-less por contrato (isenta no {@code JwtTenantFilter}) e
 * restrita a {@code ADMIN} — o role de staff da plataforma, não o dono de uma assessoria. O founder
 * chama por curl/Bruno; não há tela.</p>
 */
@RestController
@RequestMapping("/api/admin/waitlist/{id}/convite")
@RequiredArgsConstructor
@Tag(name = "founding-invite-admin", description = "Convite das assessorias fundadoras a partir da waitlist (staff)")
public class FoundingInviteAdminController {

    private final FoundingInviteService foundingInviteService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Convida um inscrito da waitlist como assessoria fundadora",
            description = "Gera token de uso único (7 dias), invalida convites anteriores do inscrito e envia o "
                    + "e-mail. Nunca devolve o token — ele existe em claro só no e-mail.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Convite emitido e e-mail enviado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FoundingInviteOutputDto.class))),
            @ApiResponse(responseCode = "401", description = "Sem autenticação"),
            @ApiResponse(responseCode = "403", description = "Acesso negado - apenas ADMIN"),
            @ApiResponse(responseCode = "404", description = "Inscrito não encontrado"),
            @ApiResponse(responseCode = "409", description = "E-mail já possui conta, ou convite já convertido"),
            @ApiResponse(responseCode = "422", description = "Inscrito não é treinador, ou e-mail maior que o cadastro aceita"),
            @ApiResponse(responseCode = "502", description = "Falha no envio do e-mail (convite fica gravado sem sentAt)")
    })
    public ResponseEntity<FoundingInviteOutputDto> convidar(
            @Parameter(description = "Id do inscrito na waitlist") @PathVariable("id") UUID waitlistId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(foundingInviteService.invite(waitlistId, jwt.getSubject()));
    }
}
