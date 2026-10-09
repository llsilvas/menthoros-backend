package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.WaitlistFunnelBucketOutputDto;
import br.com.menthoros.backend.services.WaitlistFunnelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Funil de inscrições da waitlist por origem — rota {@code /api/admin/**}, isenta do
 * {@code JwtTenantFilter} (dado global, sem tenant) e restrita a {@code ADMIN} (role de staff da
 * plataforma, mesmo padrão de {@link FoundingInviteAdminController}).
 */
@RestController
@RequestMapping("/api/admin/waitlist/funnel")
@RequiredArgsConstructor
@Tag(name = "waitlist-admin", description = "Funil de inscrições da waitlist por origem (staff)")
public class WaitlistFunnelController {

    private final WaitlistFunnelService waitlistFunnelService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Funil de inscrições agrupado por UTM",
            description = "total/qualified/invited/active por (utm_source, utm_content). Filtro de período opcional.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Grupos do funil",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = WaitlistFunnelBucketOutputDto.class)))),
            @ApiResponse(responseCode = "401", description = "Sem autenticação"),
            @ApiResponse(responseCode = "403", description = "Acesso negado - apenas ADMIN")
    })
    public ResponseEntity<List<WaitlistFunnelBucketOutputDto>> funil(
            @Parameter(description = "Início do período (inclusive), ISO-8601")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @Parameter(description = "Fim do período (inclusive), ISO-8601")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate) {
        return ResponseEntity.ok(waitlistFunnelService.calcularFunil(desde, ate));
    }
}
