package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.FoundingInviteLookupOutputDto;
import br.com.menthoros.backend.services.FoundingInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta pública do convite pelo token — o que a página {@code /#/cadastro?convite=} usa para
 * pré-preencher nome e e-mail.
 *
 * <p>Público ({@code /api/public/**}, já em {@code publicPaths} e isento de tenant) e sob rate
 * limit por IP no {@code PublicEndpointRateLimitFilter}. Responde 404 idêntico para inexistente,
 * expirado, invalidado e convertido: o estado do convite não é informação pública.</p>
 */
@RestController
@RequestMapping("/api/public/founding-invites")
@RequiredArgsConstructor
@Tag(name = "founding-invite", description = "Consulta pública do convite de assessoria fundadora")
public class FoundingInviteController {

    private final FoundingInviteService foundingInviteService;

    @GetMapping("/{token}")
    @Operation(summary = "Dados do inscrito para um convite ativo",
            description = "404 para qualquer token que não esteja ativo, sem distinguir o motivo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Convite ativo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FoundingInviteLookupOutputDto.class))),
            @ApiResponse(responseCode = "404", description = "Convite inexistente, expirado, invalidado ou já convertido"),
            @ApiResponse(responseCode = "429", description = "Muitas consultas do mesmo IP")
    })
    public ResponseEntity<FoundingInviteLookupOutputDto> consultar(
            @Parameter(description = "Token recebido por e-mail") @PathVariable("token") String token) {
        return ResponseEntity.ok(foundingInviteService.lookup(token));
    }
}
